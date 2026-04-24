# 🧭 Repository Guide - stealing-from-paradise

This document explains how this repository is structured, what technologies it uses, and how the code is organized.

---

## 1) What this repository is

`stealing-from-paradise` is a full-stack flash-sale e-commerce platform built with:

- **Backend microservices** (Java + Spring Boot)
- **Frontend web apps** (React + Vite + TypeScript)
- **Shared infrastructure** (PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch, Axon Server, MinIO)
- **Docker-based orchestration** for local/dev/prod-style environments

---

## 2) Top-level structure

At the root:

- `backend/` — Java microservices and shared backend library
- `frontend/` — React apps (`customer`, `seller`, `admin`)
- `docs/` — system documentation
- `docker-compose.yml` and related compose files — stack orchestration
- `flashsale-build.ps1` — unified run/build helper script
- `README.md`, `RUNNING.md`, `INDEX.md` — onboarding and navigation

---

## 3) Backend organization

The backend is a **Maven multi-module project** (`backend/pom.xml`), with modules:

- `common-lib` — shared DTOs/utilities/config used by services
- `discovery-service` — Eureka service discovery
- `api-gateway` — entry point and routing (Spring Cloud Gateway, WebFlux)
- `identity-service` — auth/user/identity domain
- `product-service` — product domain
- `order-service` — order domain (Axon/CQRS/Saga)
- `payment-service` — payment domain (Axon/CQRS/Saga, Stripe-oriented flows)
- `flashsale-service` — flash sale domain (Axon + Redis + event-driven flow)
- `search-service` — search domain (Elasticsearch)
- `notification-service` — notification domain (reactive + Kafka + MongoDB)
- `worker-service` — legacy/background module (partially deprecated by current docs)

Typical service layout:

- `src/main/java/...` — controllers, services, domain models, configs, messaging/axon logic
- `src/main/resources/` — application config and migration/resources
- `src/test/java/...` — tests
- `Dockerfile.dev` / `Dockerfile.prod` — container build targets

---

## 4) Frontend organization

The frontend contains three separate React applications under `frontend/apps/`:

- `customer/` — buyer-facing storefront and checkout flows
- `seller/` — seller dashboard and operations
- `admin/` — admin moderation and management

Each app follows a standard Vite + React + TS structure:

- `src/pages` — route-level pages
- `src/components` — reusable UI components
- `src/lib` (or similar helpers) — API clients/store/utilities
- `vite.config.ts`, `tsconfig*.json`, `tailwind.config.js` — build/tooling configs

---

## 5) Key technologies used

### Backend

- **Java 25**, **Spring Boot 4**, **Spring Cloud**
- **Axon Framework** (CQRS/Event Sourcing/Saga in key services)
- **Kafka** for async events and inter-service messaging
- **JPA + PostgreSQL**, **MongoDB**, **Redis**, **Elasticsearch**
- **Flyway** for relational DB migrations
- **Eureka** for service discovery

### Frontend

- **React 19**, **Vite 6**, **TypeScript**
- **React Router**
- **TanStack Query** (server-state fetching/caching)
- **Zustand** (client state)
- **Tailwind CSS**
- **Stripe JS** (customer payment integration)

### DevOps / Runtime

- **Docker / Docker Compose**
- **Nginx** (frontend/runtime proxying in deployment paths)

---

## 6) How code is organized conceptually

The system combines two backend styles:

1. **Event-driven Axon services** for order/payment/flash-sale workflows
2. **Traditional CRUD-oriented services** for identity, product, search, and notifications

Main request path:

1. Frontend app calls **API Gateway**
2. Gateway routes to domain services (resolved via **Eureka**)
3. Services persist to domain-specific storage
4. Cross-service communication uses **Kafka events** and Axon messaging patterns where applicable

This keeps high-throughput workflows (flash sale, order-payment orchestration) scalable while retaining straightforward CRUD service boundaries for supporting domains.

---

## 7) Documentation map (where to read next)

- `docs/01_OVERVIEW.md` — architecture + setup overview
- `docs/02_API.md` — API contracts/endpoints
- `docs/03_BUSINESS.md` — business workflows
- `docs/05_OPERATIONS.md` — jobs/operations/retention
- `docs/09_RUNNING.md` — detailed run commands and troubleshooting

---

**Last Updated**: 2026-04-24
