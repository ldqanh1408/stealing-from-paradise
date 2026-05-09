# Environment Variables Reference

> **Source**: docs/operations/10_ENVIRONMENT_VARIABLES.md (v5.4)
> **Generated**: 2026-05-10
> **Service**: platform

All environment variables needed to run the system. Fill these into your `.env` file.

---

## Quick Setup Checklist

| # | Item | Notes |
|---|------|-------|
| 1 | POSTGRES credentials | DB user, password, database name |
| 2 | MONGO credentials | Root username, password, init database |
| 3 | REDIS password | Optional, for auth-enabled Redis |
| 4 | MINIO credentials | Access key + secret key |
| 5 | JWT_SECRET | RS256 private key or symmetric secret |
| 6 | STRIPE_SECRET_KEY | Stripe secret key |
| 7 | STRIPE_WEBHOOK_SECRET | Webhook signing secret |
| 8 | STRIPE_PUBLISHABLE_KEY | Frontend-only |

---

## Infrastructure

### PostgreSQL

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `POSTGRES_USER` | `flashsale` | identity, payment, order, flashsale, product, ai-chat | DB username |
| `POSTGRES_PASSWORD` | (set securely) | same services | DB password |
| `POSTGRES_DB` | `flashsale` | docker container init | Database name |

### MongoDB

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `MONGO_INITDB_ROOT_USERNAME` | `admin` | notification | Root user |
| `MONGO_INITDB_ROOT_PASSWORD` | (set securely) | notification | Root password |
| `MONGO_INITDB_DATABASE` | `flashsale` | docker init | Initial database |

### Redis

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `REDIS_HOST` | `redis` | api-gateway, identity, flashsale | Redis hostname |
| `REDIS_PASSWORD` | (optional) | same services | If auth enabled |
| `REDIS_PORT` | `6379` | same services | Default port |

### Elasticsearch

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `ELASTIC_URI` | `http://elasticsearch:9200` | search-service | Full URI |
| `ES_JAVA_OPTS` | `-Xms512m -Xmx512m` | elasticsearch container | Heap size |

### MinIO

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `MINIO_URL` | `http://minio:9000` | product-service | S3-compatible endpoint |
| `MINIO_ACCESS_KEY` | (set securely) | product-service | Access key ID |
| `MINIO_SECRET_KEY` | (set securely) | product-service | Secret access key |

### Kafka

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `KAFKA_SERVER` | `kafka:9092` | all backend services | Bootstrap server |
| `KAFKA_BROKER_ID` | `1` | kafka container | Broker ID |
| `KAFKA_ZOOKEEPER_CONNECT` | `zookeeper:2181` | kafka container | ZK connection |

### Axon Server

| Variable | Default | Used By | Notes |
|----------|---------|---------|-------|
| `AXON_SERVER` | `axonserver:8124` | payment, order, flashsale | gRPC port |
| `AXONIQ_AXONSERVER_STANDALONE` | `true` | axonserver container | Standalone mode |
| `AXONIQ_AXONSERVER_DEVMODE_ENABLED` | `true` | axonserver container | Dev mode |

---

## Backend Services

### api-gateway (:8080)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | Service registry |
| `SERVER_BIND` | `0.0.0.0` | Bind address |
| `REDIS_HOST` | `redis` | JWT blocklist cache |
| `JWT_SECRET` | (RS256 public key or secret) | Token validation |
| `JWT_EXPIRATION` | `900000` | ms -- 15 minutes |

### identity-service (:8081)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | PostgreSQL hostname |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `REDIS_HOST` | `redis` | JWT blocklist + session |
| `JWT_SECRET` | (private key for RS256 signing) | Must match api-gateway |
| `JWT_EXPIRATION` | `900000` | ms -- access token |
| `REFRESH_TOKEN_EXPIRATION` | `604800000` | ms -- 7 days |

### payment-service (:8082)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |
| `STRIPE_SECRET_KEY` | `sk_live_...` | Sensitive |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | Sensitive |
| `STRIPE_PLATFORM_FEE_PERCENTAGE` | `0.05` | Platform fee rate (5%) |

### order-service (:8083)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |
| `JWT_SECRET` | (same as identity-service) | For internal JWT decode |

### flashsale-service (:8085)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `REDIS_HOST` | `redis` | Critical -- Lua atomic buy |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |

### product-service (:8090)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | PostgreSQL hostname (catalog + cart) |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `MINIO_URL` | `http://minio:9000` | Image storage |
| `MINIO_ACCESS_KEY` | (set securely) | |
| `MINIO_SECRET_KEY` | (set securely) | |

### search-service (:8091)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `ELASTIC_URI` | `http://elasticsearch:9200` | |
| `KAFKA_SERVER` | `kafka:9092` | |

### notification-service (:8092)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `MONGO_HOST` | `mongo` | |
| `MONGO_INITDB_ROOT_USERNAME` | `admin` | |
| `MONGO_INITDB_ROOT_PASSWORD` | (set securely) | |
| `REDIS_HOST` | `redis` | SSE connection tracking |
| `KAFKA_SERVER` | `kafka:9092` | |

### ai-chat-service (:8093)

| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | (set securely) | |
| `KAFKA_SERVER` | `kafka:9092` | |

---

## Frontend Apps

All 3 apps share the same variable set, injected at build time (Vite).

| Variable | Example | Notes |
|----------|---------|-------|
| `VITE_BACKEND_MODE` | `proxy` / `direct` | `proxy` = via nginx, `direct` = call directly |
| `VITE_PROXY_TARGET` | `http://api-gateway:8080` | Target when using Vite proxy |
| `VITE_STRIPE_PUBLISHABLE_KEY` | `pk_live_...` | Public key -- safe to expose in browser |

> `VITE_STRIPE_PUBLISHABLE_KEY` is a public key. Never put `STRIPE_SECRET_KEY` in frontend code.

---

## Nginx Reverse Proxy

| Variable | Example | Notes |
|----------|---------|-------|
| `NGINX_PORT` | `80` | Listen port |
| `GATEWAY_HOST` | `api-gateway` | API Gateway hostname |
| `GATEWAY_PORT` | `8080` | API Gateway port |
| `CUSTOMER_HOST` | `customer-app` | Customer SPA container |
| `CUSTOMER_PORT` | `3000` | |
| `SELLER_HOST` | `seller-app` | Seller SPA container |
| `SELLER_PORT` | `3001` | |
| `ADMIN_HOST` | `admin-app` | Admin SPA container |
| `ADMIN_PORT` | `3002` | |

---

## Security Notes

### Secrets that MUST be changed before production

| Variable | Risk if unchanged |
|----------|-------------------|
| `POSTGRES_PASSWORD` | Database breach |
| `MONGO_INITDB_ROOT_PASSWORD` | Database breach |
| `JWT_SECRET` | JWT forgery, auth bypass |
| `STRIPE_SECRET_KEY` | Financial fraud |
| `STRIPE_WEBHOOK_SECRET` | Fake webhook injection |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | File storage breach |
| `REDIS_PASSWORD` | Cache poisoning |

### JWT_SECRET sharing

`JWT_SECRET` must be identical across:
- `identity-service` (signs tokens)
- `api-gateway` (validates tokens)
- `order-service` (decodes user id from internal requests)

If using RS256: identity-service holds the **private key**, api-gateway and order-service use the **public key**.

### Stripe Platform Fee

`STRIPE_PLATFORM_FEE_PERCENTAGE=0.05` = platform takes 5% per transaction. Changing this affects new transactions immediately (not retroactive).

---

## .env Template

```env
# --- Infrastructure ---
POSTGRES_USER=flashsale
POSTGRES_PASSWORD=CHANGE_ME
POSTGRES_DB=flashsale

MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=CHANGE_ME
MONGO_INITDB_DATABASE=flashsale

REDIS_HOST=redis
REDIS_PASSWORD=

ELASTIC_URI=http://elasticsearch:9200
ES_JAVA_OPTS=-Xms512m -Xmx512m

MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=CHANGE_ME
MINIO_SECRET_KEY=CHANGE_ME

KAFKA_SERVER=kafka:9092
AXON_SERVER=axonserver:8124
EUREKA_URI=http://discovery-service:8761/eureka

# --- JWT ---
JWT_SECRET=CHANGE_ME_VERY_LONG_SECRET
JWT_EXPIRATION=900000
REFRESH_TOKEN_EXPIRATION=604800000

# --- Stripe ---
STRIPE_SECRET_KEY=sk_test_CHANGE_ME
STRIPE_WEBHOOK_SECRET=whsec_CHANGE_ME
STRIPE_PLATFORM_FEE_PERCENTAGE=0.05
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_CHANGE_ME

# --- Frontend ---
VITE_BACKEND_MODE=proxy
VITE_PROXY_TARGET=http://api-gateway:8080

# --- Nginx ---
NGINX_PORT=80
GATEWAY_HOST=api-gateway
GATEWAY_PORT=8080
CUSTOMER_HOST=customer-app
CUSTOMER_PORT=3000
SELLER_HOST=seller-app
SELLER_PORT=3001
ADMIN_HOST=admin-app
ADMIN_PORT=3002
```
