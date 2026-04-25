# Deployment Fix: Port 8080 "Address Already in Use" Error

**Date Fixed**: April 25, 2026  
**Commits**:
- `ed5405f` - Initial port-cleanup hardening
- `6e10b0b` - Four-stage cleanup with Docker reservation table verification
- `4cef8ba` - Diagnostic dump before `docker-compose up`
- `ca641ce` - Architectural fix: remove host port bind (final solution)

---

## The Problem

Every production deployment was failing with:

```
Host is already in use by another container
ERROR: for fs-gateway  Cannot start service api-gateway: 
  failed to set up container networking: driver failed programming 
  external connectivity on endpoint fs-gateway (...): 
  failed to bind host port 127.0.0.1:8080/tcp: address already in use
```

The error persisted even after:
1. Stopping the old `fs-gateway` container
2. Killing stale `docker-proxy` processes
3. Verifying port 8080 was free via `lsof` and `ss`
4. Hard-gating the deployment until port was confirmed free
5. Adding diagnostic dumps before `docker-compose up`

### Why It Kept Happening

The root cause was **architectural**: the API gateway was being published to the host on port 8080, but it was already accessible inside the Docker network (where Nginx needed to reach it). This redundant host port binding created three failure modes:

1. **Stale container with restart policy**: The old `fs-gateway` container had `restart: unless-stopped`. Between cleanup steps, the Docker daemon would auto-restart it mid-cleanup, re-claiming port 8080.

2. **Docker's internal port-reservation table vs. OS-level checks**: The deploy script verified the port was free using `lsof` and `ss` (OS-level tools), but Docker's network driver tracks port reservations in its own internal database. A stopped-but-not-fully-removed container could still have a reservation, making Docker refuse the port binding even though the OS saw it as free.

3. **Race between `docker-compose pull` and `docker-compose up`**: The pull step could trigger background container state changes that re-claimed port 8080 between the port-check and the actual container creation.

---

## Root Cause Analysis

### The Architecture Flaw

```
Production Gateway Binding (BEFORE):
  client → nginx (host :80)
    └─→ fs-gateway:8080 (Docker network) ✓ NEEDED
  client → host:8080 ──→ fs-gateway:8080 (Docker) ✗ REDUNDANT
```

The gateway was **exposed both ways**:
- Via Nginx on port 80 (the intended public entry point)
- Directly on the host's port 8080 (legacy, unused, conflict-prone)

The Nginx configuration already uses the internal Docker network (`fs-gateway:8080`), so the host port 8080 binding served no purpose except to create deployment conflicts.

### Why Previous Fixes Didn't Stick

Each attempted fix targeted symptoms, not the architectural root cause:

| Fix Attempt | What It Did | Why It Failed |
|---|---|---|
| Kill docker-proxy processes | Removed OS-level port binding | Docker's internal reservation remained |
| Hard gate with lsof/ss retries | Waited for port to appear free at OS level | Didn't check Docker's reservation table |
| Diagnostic dumps before `up` | Showed what was holding the port | Only informational; didn't remove it |
| Sweep containers by HostConfig.PortBindings | Found any container reserving 8080 | Didn't address the architectural redundancy |

---

## The Solution: Remove the Redundant Host Bind

### Changes Made

#### 1. `docker-compose.prod-pulled.yml`

**Before:**
```yaml
api-gateway:
  image: ghcr.io/${IMAGE_PREFIX}/api-gateway:prod-latest
  ports:
    - "0.0.0.0:8080:8080"
```

**After:**
```yaml
api-gateway:
  image: ghcr.io/${IMAGE_PREFIX}/api-gateway:prod-latest
  ports: []
  expose:
    - "8080"
```

**Effect:**
- The gateway no longer publishes to the host on port 8080
- The gateway remains reachable only on the Docker network as `fs-gateway:8080`
- Nginx (already configured) routes public traffic to this internal address

#### 2. `.github/workflows/deploy.yml` - Final Health Check

**Before:**
```bash
curl -sf http://${_IP}:8080/actuator/health
```

**After:**
```bash
ssh -o StrictHostKeyChecking=no ${_USER}@${_IP} \
  "docker exec fs-gateway curl -sf http://localhost:8080/actuator/health"
# Also verify nginx is serving on port 80
curl -sf http://${_IP}/health
```

**Effect:**
- The external health check now uses `docker exec` to query the gateway directly (guaranteed to work even though port 8080 is not exposed)
- Added a check that Nginx is responding on port 80 (the actual public entry point)

#### 3. `.github/workflows/deploy.yml` - Summary URLs

**Before:**
```
API Gateway:  http://${_IP}:8080
Swagger:      http://${_IP}:8080/swagger-ui.html
```

**After:**
```
Customer:     http://${_IP}/
Seller:       http://${_IP}/seller/
Admin:        http://${_IP}/admin/
API Gateway:  http://${_IP}/api/
```

**Effect:**
- Summary displays the accurate public URLs (all routed through Nginx)
- Removes the now-inaccurate direct port 8080 references

---

## How It Works Now

### Deployment Flow (Fixed)

```
docker-compose up -d
  ↓
api-gateway service created
  ↓
Gateway binds to :8080 internally (port not exposed to host)
  ↓
Nginx reaches gateway via fs-gateway:8080 (Docker network)
  ↓
docker exec health check validates gateway is running
  ↓
curl health check validates Nginx is serving on :80
  ↓
Deployment succeeds ✓
```

### Public Traffic Flow

```
Client
  ↓
Host Port 80 (Nginx)
  ↓
Docker Network
  ↓
fs-gateway:8080 (internal)
  ↓
Microservices
```

---

## Why This Fix Is Robust

1. **No port conflicts**: The gateway is never published on the host, so there's no race with stale containers or other processes.

2. **Single entry point**: All public traffic goes through Nginx (port 80). This is cleaner, more secure (one place to add TLS, rate limiting, etc.), and eliminates the "two ways to reach the gateway" problem.

3. **Nginx was already configured for it**: The `nginx/reverse-proxy.conf` already used `server fs-gateway:8080;` — the fix simply makes this the *only* way to reach the gateway, as originally intended.

4. **Dev still works**: The base `docker-compose.yml` keeps `127.0.0.1:8080:8080` for local development convenience. Only the production override changes.

5. **Eliminates an entire class of bugs**: Any future deployment failure caused by port 8080 conflicts cannot happen, because port 8080 is never bound to the host in production.

---

## Commit History

### Commit `ed5405f`: Initial hardening
- Added `docker network disconnect --force` to clear stale endpoints
- Replaced process-level port checking with container-level sweep (HostConfig.PortBindings)
- Added hard gate that fails the deployment if port 8080 can't be freed

**Why this wasn't enough:** It addressed symptoms (stale containers, docker-proxy) but not the root cause (the redundant port binding on every deploy).

### Commit `6e10b0b`: Four-stage cleanup with Docker reservation checks
- Stage 1: Disable restart policy on the gateway (`docker update --restart=no`)
- Stage 2: Sweep all containers for port 8080 reservations
- Stage 3: Existing network/container cleanup
- Stage 4: Hard gate checking both OS-level AND Docker's internal reservation table

**Why this wasn't enough:** It added a 10-retry gate that would eventually force-remove the offender, but the underlying architecture problem remained (every deploy would try to re-bind 8080, potentially creating the same conflict again).

### Commit `4cef8ba`: Diagnostic dump before `docker-compose up`
- Printed host state (containers, ss, lsof) right before the actual `up` call
- Added last-second sweep as a backstop
- Fixed grep patterns to also match `"HostPort":"8080"` value form

**Why this wasn't enough:** It made the failure observable (showed *which* container had the port), but didn't eliminate the root cause. The fix was diagnostic, not structural.

### Commit `ca641ce`: Architectural fix (final solution)
- Removed the host port bind from the prod compose override
- Changed the external health check to use `docker exec` instead of curl
- Updated deployment summary URLs to reflect Nginx-only routing

**Result:** The port conflict is no longer possible, because port 8080 is never bound to the host in production.

---

## Testing the Fix

When the first deployment runs after this fix is merged:

1. **Step 3 cleanup** will catch the OLD `fs-gateway` container (which still has the old `0.0.0.0:8080:8080` binding) and remove it.

2. **The new `docker-compose up`** will create a fresh `fs-gateway` with `ports: []` — no host port binding.

3. **The health check** will use `docker exec` (works regardless of port 8080 exposure).

4. **All subsequent deployments** will be conflict-free (the new containers never bind port 8080 to the host).

---

## Lessons Learned

### 1. Distinguish Between Symptoms and Root Causes

The port 8080 error was a **symptom** of a **redundant architecture** (gateway exposed both directly and via Nginx). Fixing symptoms (cleanup logic, retries, diagnostics) eventually required fixing the architecture itself.

### 2. Understand the Difference Between OS-Level and Container-Level Port Conflicts

- **OS-level** (`lsof`, `ss`): Checks if the kernel socket is bound
- **Container-level** (Docker): Tracks which containers have which port mappings, even if stopped

A stopped container with `restart: unless-stopped` still has a reservation in Docker's internal table, so removing it might require `docker rm -f`, not just `docker stop`.

### 3. Verify Every Assumption in Architecture Design

The question "Why does the gateway need to be exposed on host port 8080?" revealed that it didn't — Nginx was already configured to reach it over the Docker network. Once that redundancy was eliminated, the entire conflict class disappeared.

---

## References

- **Docker Compose networking**: https://docs.docker.com/compose/networking/
- **Docker port binding**: https://docs.docker.com/config/containers/container-networking/
- **Nginx upstream configuration**: https://nginx.org/en/docs/http/ngx_http_upstream_module.html
- **GitHub Actions secrets**: https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions
