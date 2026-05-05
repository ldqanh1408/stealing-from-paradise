# Environment Variables Reference

**Version**: v5.4 | **Last Updated**: 2026-05-02

Tất cả environment variables cần thiết để chạy hệ thống. Copy từ đây để điền vào file `.env`.

---

## Quick Setup Checklist

```
□ POSTGRES credentials
□ MONGO credentials
□ REDIS password
□ MINIO credentials
□ JWT_SECRET (RS256 private key hoặc symmetric secret)
□ STRIPE_SECRET_KEY
□ STRIPE_WEBHOOK_SECRET
□ STRIPE_PUBLISHABLE_KEY (frontend)
```

---

## Infrastructure

### PostgreSQL
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `POSTGRES_USER` | `flashsale` | identity, payment, order, flashsale, worker | DB username |
| `POSTGRES_PASSWORD` | _(set securely)_ | identity, payment, order, flashsale, worker | DB password |
| `POSTGRES_DB` | `flashsale` | docker container init | Database name |

### MongoDB
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `MONGO_INITDB_ROOT_USERNAME` | `admin` | product, notification | Root user |
| `MONGO_INITDB_ROOT_PASSWORD` | _(set securely)_ | product, notification | Root password |
| `MONGO_INITDB_DATABASE` | `flashsale` | docker init | Initial database |

### Redis
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `REDIS_HOST` | `redis` | api-gateway, identity, flashsale, product, notification | Redis hostname |
| `REDIS_PASSWORD` | _(optional)_ | same | If auth enabled |
| `REDIS_PORT` | `6379` | same | Default port |

### Elasticsearch
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `ELASTIC_URI` | `http://elasticsearch:9200` | search-service | Full URI |
| `ES_JAVA_OPTS` | `-Xms512m -Xmx512m` | elasticsearch container | Heap size |

### MinIO
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `MINIO_URL` | `http://minio:9000` | product-service | S3-compatible endpoint |
| `MINIO_ACCESS_KEY` | _(set securely)_ | product-service | Access key ID |
| `MINIO_SECRET_KEY` | _(set securely)_ | product-service | Secret access key |

### Kafka
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `KAFKA_SERVER` | `kafka:9092` | all backend services | Bootstrap server |
| `KAFKA_BROKER_ID` | `1` | kafka container | Broker ID |
| `KAFKA_ZOOKEEPER_CONNECT` | `zookeeper:2181` | kafka container | ZK connection |

### Axon Server
| Variable | Default / Example | Used By | Notes |
|----------|-------------------|---------|-------|
| `AXON_SERVER` | `axonserver:8124` | payment, order, flashsale, worker | gRPC port |
| `AXONIQ_AXONSERVER_STANDALONE` | `true` | axonserver container | Standalone mode (no cluster) |
| `AXONIQ_AXONSERVER_DEVMODE_ENABLED` | `true` | axonserver container | Dev mode (reset on restart) |

---

## Backend Services

### api-gateway (:8080)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | Service registry |
| `SERVER_BIND` | `0.0.0.0` | Bind address |
| `REDIS_HOST` | `redis` | JWT blocklist cache |
| `JWT_SECRET` | _(RS256 public key or secret)_ | Token validation |
| `JWT_EXPIRATION` | `900000` | ms — 15 minutes |

### identity-service (:8081)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | PostgreSQL hostname |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | _(set securely)_ | |
| `REDIS_HOST` | `redis` | JWT blocklist + session |
| `JWT_SECRET` | _(private key for RS256 signing)_ | **Must match api-gateway** |
| `JWT_EXPIRATION` | `900000` | ms — access token |
| `REFRESH_TOKEN_EXPIRATION` | `604800000` | ms — 7 days |

### payment-service (:8082)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | _(set securely)_ | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |
| `STRIPE_SECRET_KEY` | `sk_live_...` | **Sensitive** — Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` | **Sensitive** — Webhook signature |
| `STRIPE_PLATFORM_FEE_PERCENTAGE` | `0.05` | Platform fee rate (5%) |

### order-service (:8083)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | _(set securely)_ | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |
| `JWT_SECRET` | _(same as identity-service)_ | For internal JWT decode |

### flashsale-service (:8085)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | _(set securely)_ | |
| `REDIS_HOST` | `redis` | **Critical** — Lua atomic buy |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |

### worker-service (:8086)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `DB_HOST` | `postgres` | Outbox + DLQ tables |
| `POSTGRES_USER` | `flashsale` | |
| `POSTGRES_PASSWORD` | _(set securely)_ | |
| `KAFKA_SERVER` | `kafka:9092` | |
| `AXON_SERVER` | `axonserver:8124` | |

### product-service (:8090)
| Variable | Example | Notes |
|----------|---------|-------|
| `EUREKA_URI` | `http://discovery-service:8761/eureka` | |
| `MONGO_HOST` | `mongo` | MongoDB hostname |
| `MONGO_INITDB_ROOT_USERNAME` | `admin` | |
| `MONGO_INITDB_ROOT_PASSWORD` | _(set securely)_ | |
| `REDIS_HOST` | `redis` | Cart cache |
| `KAFKA_SERVER` | `kafka:9092` | |
| `MINIO_URL` | `http://minio:9000` | Image storage |
| `MINIO_ACCESS_KEY` | _(set securely)_ | |
| `MINIO_SECRET_KEY` | _(set securely)_ | |

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
| `MONGO_INITDB_ROOT_PASSWORD` | _(set securely)_ | |
| `REDIS_HOST` | `redis` | SSE connection tracking |
| `KAFKA_SERVER` | `kafka:9092` | |

---

## Frontend Apps

Tất cả 3 app dùng chung cùng set biến nhưng inject lúc build (Vite).

| Variable | Example | Notes |
|----------|---------|-------|
| `VITE_BACKEND_MODE` | `proxy` / `direct` | `proxy` = qua nginx, `direct` = gọi thẳng |
| `VITE_PROXY_TARGET` | `http://api-gateway:8080` | Target khi dùng Vite proxy |
| `VITE_STRIPE_PUBLISHABLE_KEY` | `pk_live_...` | **Sensitive** — Stripe publishable key (exposed to browser) |

> `VITE_STRIPE_PUBLISHABLE_KEY` là public key — an toàn để expose trong browser. **Không bao giờ** để `STRIPE_SECRET_KEY` vào frontend.

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
| `JWT_SECRET` | JWT forgery → auth bypass |
| `STRIPE_SECRET_KEY` | Financial fraud |
| `STRIPE_WEBHOOK_SECRET` | Fake webhook injection |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | File storage breach |
| `REDIS_PASSWORD` | Cache poisoning |

### JWT_SECRET sharing
`JWT_SECRET` phải **giống nhau** giữa:
- `identity-service` (ký token)
- `api-gateway` (xác thực token)
- `order-service` (decode user id từ internal requests)

Nếu dùng RS256: identity-service giữ **private key**, api-gateway và order-service dùng **public key**.

### Stripe Platform Fee
`STRIPE_PLATFORM_FEE_PERCENTAGE=0.05` = platform lấy 5% mỗi giao dịch. Thay đổi ảnh hưởng ngay đến các giao dịch mới (không ảnh hưởng giao dịch cũ).

---

## `.env` Template

```env
# ─── Infrastructure ───────────────────────────────────
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

# ─── JWT ──────────────────────────────────────────────
JWT_SECRET=CHANGE_ME_VERY_LONG_SECRET
JWT_EXPIRATION=900000
REFRESH_TOKEN_EXPIRATION=604800000

# ─── Stripe ───────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_CHANGE_ME
STRIPE_WEBHOOK_SECRET=whsec_CHANGE_ME
STRIPE_PLATFORM_FEE_PERCENTAGE=0.05
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_CHANGE_ME

# ─── Frontend ─────────────────────────────────────────
VITE_BACKEND_MODE=proxy
VITE_PROXY_TARGET=http://api-gateway:8080

# ─── Nginx ────────────────────────────────────────────
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

---

*Last Updated: 2026-05-02 · v5.4*
