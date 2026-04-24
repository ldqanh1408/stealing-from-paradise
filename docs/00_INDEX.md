# 📚 Documentation Index - stealing-from-paradise

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)
**Last Updated**: 2026-04-24
**Status**: Complete & Production-Ready

---

## 🎯 Quick Navigation

### For Getting Started
1. **[01_OVERVIEW.md](01_OVERVIEW.md)** - Project overview, tech stack, architecture, setup & running services
2. **[CLAUDE.md](/CLAUDE.md)** - Quick setup & build commands
3. **[09_RUNNING.md](09_RUNNING.md)** - Complete running guide, scripts, troubleshooting
4. **[10_REPOSITORY_GUIDE.md](10_REPOSITORY_GUIDE.md)** - Codebase structure, technologies, and organization map

### For Development
3. **[02_API.md](02_API.md)** - Complete API specification (v5.3 RTS) with all endpoints & Kafka topics
4. **[03_BUSINESS.md](03_BUSINESS.md)** - Business logic, workflows & 9 workflows (v5.3)
5. **[04_POLICIES.md](04_POLICIES.md)** - System policies, trust score rules, account lifecycle (v3)
6. **[05_OPERATIONS.md](05_OPERATIONS.md)** - Data retention, 23 cronjobs & maintenance (v5.0 — Distributed per Service)

### For Deep Dives
7. **[06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md)** - Stripe multi-vendor payment & Saga pattern
8. **[07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md)** - Tổng hợp luồng nghiệp vụ tổng quan
9. **[08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md)** - Integration details
10. **[09_RUNNING.md](09_RUNNING.md)** - Complete running guide, scripts, troubleshooting
11. **[10_REPOSITORY_GUIDE.md](10_REPOSITORY_GUIDE.md)** - Repository structure and code organization

### Architecture
10. **[erd.mermaid](erd.mermaid)** - Database entity-relationship diagram

---

## 📊 Documentation Overview

| Document | Version | Focus | Status |
|----------|---------|-------|--------|
| 01_OVERVIEW.md | v1 | Project architecture, setup | ✅ Complete |
| 02_API.md | v5.3 RTS | All endpoints, Kafka topics | ✅ Complete |
| 03_BUSINESS.md | v5.3 RTS | Workflows, policies, 23 cronjobs | ✅ Complete |
| 04_POLICIES.md | v3 RTS | Trust score, account lifecycle | ✅ Complete |
| 05_OPERATIONS.md | v5.0 RTS | Data retention, 23 jobs (per service) | ✅ Complete |
| 06_PAYMENT_SAGA_FLOW.md | v2 | Payment flow, Stripe, Saga | ✅ Complete |
| 07_BUSINESS_FLOWS.md | v1 | Luồng nghiệp vụ tổng hợp (Mermaid) | ✅ NEW |
| 08_PAYMENT_ORDER_INTEGRATION.md | v2 | Integration details | ✅ Complete |
| 10_REPOSITORY_GUIDE.md | v1 | Repo structure, technologies, organization | ✅ New |
| erd.mermaid | v1 | Database ERD | ✅ Complete |

**Total**: ~9,500+ lines of documentation

---

## 🚀 By Role

### New Developer
1. Read [01_OVERVIEW.md](01_OVERVIEW.md) — Full project picture
2. Read [CLAUDE.md](/CLAUDE.md) — Build & run commands
3. Use [02_API.md](02_API.md) — API integration reference

### Backend Developer
1. [01_OVERVIEW.md](01_OVERVIEW.md) - Backend architecture section
2. [02_API.md](02_API.md) - Endpoint specifications
3. [03_BUSINESS.md](03_BUSINESS.md) - Business logic & workflows
4. [04_POLICIES.md](04_POLICIES.md) - Rules & constraints
5. [05_OPERATIONS.md](05_OPERATIONS.md) - Cronjobs & data retention
6. [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) - Luồng nghiệp vụ tổng quan

### Frontend Developer
1. [01_OVERVIEW.md](01_OVERVIEW.md) - Frontend architecture section
2. [02_API.md](02_API.md) - API request/response examples
3. [CLAUDE.md](/CLAUDE.md) - Frontend build & dev commands

### DevOps / Operations
1. [CLAUDE.md](/CLAUDE.md) - Deployment & Docker commands
2. [01_OVERVIEW.md](01_OVERVIEW.md) - Tech stack & architecture
3. [05_OPERATIONS.md](05_OPERATIONS.md) - Maintenance & cronjobs

### Product Manager / Team Lead
1. [01_OVERVIEW.md](01_OVERVIEW.md) - Project overview & features
2. [03_BUSINESS.md](03_BUSINESS.md) - Business workflows & policies
3. [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) - Luồng nghiệp vụ tổng quan (Mermaid)
4. [CLAUDE.md](/CLAUDE.md) - Tech stack summary

---

## ✨ Key Features Documented

### Architecture
- ✅ 11 microservices (4 Axon + 7 traditional)
- ✅ Multi-vendor payment with Stripe Connect
- ✅ Event-driven with Kafka & Axon Framework
- ✅ Reactive services (WebFlux, R2DBC)
- ✅ 3 React frontend apps (Customer 3000, Seller 3001, Admin 3002)

### Backend Services (11)
| Service | Type | Port | Database | Cronjobs |
|---------|------|------|----------|---------|
| discovery-service | Infrastructure | 8761 | — | — |
| api-gateway | Infrastructure | 8080 | — | — |
| identity-service | Traditional | 8085 | PostgreSQL | JOB-03/11/14/17/18/19/20 |
| product-service | Traditional | 8090 | MongoDB | JOB-07/10/16 |
| order-service | **Axon** | 8088 | PostgreSQL | JOB-13/22 |
| payment-service | **Axon** | 8089 | PostgreSQL | JOB-04/05/06/12/15 |
| flashsale-service | **Axon** | 8085 | PostgreSQL | JOB-01/02/08/21 |
| search-service | Traditional | 8091 | Elasticsearch | — |
| notification-service | Traditional | 8092 | MongoDB | JOB-09 (TTL Index) |
| worker-service | — | — | — | **Deprecated** (moved to respective services) |
| common-lib | Library | — | — | — |

### Business Logic
- ✅ 9 core workflows (auth, products, orders, payments, refunds, Flash Sale, loyalty, trust score, RTS)
- ✅ Order lifecycle (8 statuses): PENDING → PAID → SHIPPING → DELIVERED / RETURNED / REFUNDED / PARTIALLY_REFUNDED / CANCELLED
- ✅ Refund system with RTS (Return To Sender) - v5.3
- ✅ Trust Score with appeal system (0-100, 6 tiers)
- ✅ Loyalty Points system (EARNED → CONFIRMED → EXPIRED)
- ✅ Flash Sale anti-oversell mechanism (Redis atomic counters)

### Technical
- ✅ 40+ API endpoints documented
- ✅ 35+ Kafka topics with payloads
- ✅ 23 cronjobs with SQL logic
- ✅ Complete API JSON examples
- ✅ Error handling & validation rules
- ✅ Stripe Connect multi-vendor transfers

---

## 📖 How to Use Documentation

### Find Information Quick
| Need | Where |
|------|-------|
| How to start development? | [CLAUDE.md](/CLAUDE.md) |
| API endpoint details? | [02_API.md](02_API.md) |
| Business workflow? | [03_BUSINESS.md](03_BUSINESS.md) |
| System rules & policies? | [04_POLICIES.md](04_POLICIES.md) |
| Database schema? | [erd.mermaid](erd.mermaid) |
| Cronjob details? | [05_OPERATIONS.md](05_OPERATIONS.md) |
| How does payment work? | [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) |
| Luồng nghiệp vụ tổng quan? | [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) |
| Service integration? | [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) |

### Common Tasks
| Task | Document | Section |
|------|---------|---------|
| Set up local environment | CLAUDE.md | Build & Run Commands |
| Implement new API endpoint | 02_API.md | Any endpoint section |
| Understand order workflow | 03_BUSINESS.md | Luồng: Vòng Đời Đơn Hàng |
| Configure cronjob | 05_OPERATIONS.md | DANH SÁCH CRONJOB |
| Debug payment issue | 06_PAYMENT_SAGA_FLOW.md | Full document |
| How to run / start? | 09_RUNNING.md | Scripts, troubleshooting |
| Xem luồng nghiệp vụ | 07_BUSINESS_FLOWS.md | Mermaid diagrams |
| Order ↔ Payment integration | 08_PAYMENT_ORDER_INTEGRATION.md | Full document |

---

## 🔄 Document Relationships

```
01_OVERVIEW.md
  ↓ (project structure)
  ├→ CLAUDE.md (quick start)
  ├→ 02_API.md (technical details)
  └→ 03_BUSINESS.md (business logic)
     ├→ 04_POLICIES.md (system rules)
     ├→ 05_OPERATIONS.md (cronjobs)
     ├→ 07_BUSINESS_FLOWS.md (Mermaid flow diagrams) ← NEW
     └→ 06_PAYMENT_SAGA_FLOW.md (payment detail)
        └→ 08_PAYMENT_ORDER_INTEGRATION.md

erd.mermaid (database schema)
  └→ Used by all technical docs
```

---

## 📝 Version Information

### Current Versions
- **API**: v5.3 RTS (includes tracking number for refunds)
- **Business**: v5.3 RTS (includes Return To Sender)
- **Policies**: v3 RTS (with trust score & account lifecycle)
- **Operations**: v5.0 RTS (23 cronjobs — distributed per service)
- **Flows**: v1 (Mermaid diagrams)

### Tech Stack
| Component | Version |
|-----------|---------|
| Java | 25 (LTS) |
| Spring Boot | 4.0.4 |
| React | 19 |
| Axon Framework | 4.13.0 |
| Kafka | 7.4.0 |
| PostgreSQL | 15.4 |
| MongoDB | 6.0 |
| Redis | 7.0 |
| Elasticsearch | 8.10 |

---

## ✅ Documentation Checklist

| Item | Status | Details |
|------|--------|---------|
| **Architecture** | ✅ Complete | 11 services, 3 apps documented |
| **Running Guide** | ✅ NEW | Complete setup, compose file map, troubleshooting |
| **API Endpoints** | ✅ Complete | 40+ endpoints with examples |
| **Business Workflows** | ✅ Complete | 9 workflows, all detailed |
| **System Policies** | ✅ Complete | Trust score, refund, loyalty |
| **Cronjobs** | ✅ Complete | 23 jobs with SQL logic |
| **Kafka Topics** | ✅ Complete | 35+ topics documented |
| **Error Handling** | ✅ Complete | All error codes defined |
| **Database Schema** | ✅ Complete | ERD provided |
| **Setup Guide** | ✅ Complete | Docker, Local, Prod options |
| **Business Flow Diagrams** | ✅ Complete | Mermaid diagrams in 07_BUSINESS_FLOWS.md |
| **Payment Integration** | ✅ Complete | Saga pattern, Stripe, RTS |
| **Running Guide** | ✅ NEW v1 | Setup, compose map, scripts, troubleshooting |

---

## 🎓 Learning Path

### Beginner (0-2 hours)
1. Read [01_OVERVIEW.md](01_OVERVIEW.md) — Project overview
2. Skim [CLAUDE.md](/CLAUDE.md) — Build commands
3. Start local dev environment

### Intermediate (2-8 hours)
1. [02_API.md](02_API.md) — Learn endpoints
2. [03_BUSINESS.md](03_BUSINESS.md) — Understand workflows
3. [04_POLICIES.md](04_POLICIES.md) — Rules & constraints
4. [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) — Xem luồng nghiệp vụ bằng Mermaid
5. Try API in Postman/Insomnia

### Advanced (8+ hours)
1. [05_OPERATIONS.md](05_OPERATIONS.md) — Cronjobs & data flow
2. [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) — Saga pattern
3. [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) — Deep dive

---

## 📞 Updates & Maintenance

### Last Updated
- **2026-04-23** — Tạo docs/09_RUNNING.md, hoàn thiện flashsale-build.ps1, cập nhật INDEX
- **2026-04-20** — Documentation reorganized & consolidated
- **2026-04-18** — Payment Saga Flow v2
- **2026-04-15** — API v5.3 with tracking number for refunds
- **2026-04-14** — Business docs & system policies

### How to Update
When making changes:
1. Update relevant doc file
2. Update this INDEX with new version
3. Update version numbers in affected docs
4. Commit with message: `docs: update [doc-name] for [feature]`

---

## 🚀 Summary

This documentation provides **complete, production-ready guidance** for the Flash Sale E-Commerce Platform:

- ✅ **Comprehensive**: 9,500+ lines covering all aspects
- ✅ **Organized**: Clear navigation by role & task
- ✅ **Practical**: Real commands, examples, SQL queries, Mermaid diagrams
- ✅ **Current**: Latest versions (v5.3 API, v3 Policies, v5.0 Operations, v1 Flows)
- ✅ **Complete**: Business, technical, operational, testing

**Start with 01_OVERVIEW.md for the full picture!**
**Use CLAUDE.md for quick setup!**
**Reference 02_API.md for endpoint details!**
**Use 07_BUSINESS_FLOWS.md for visual flow diagrams!**

---

**Ready for production! 🚀**
