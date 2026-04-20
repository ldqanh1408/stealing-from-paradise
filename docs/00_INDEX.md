# 📚 Documentation Index - stealing-from-paradise

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)  
**Last Updated**: 2026-04-20  
**Status**: Complete & Production-Ready  
**Latest Update**: Documentation consolidated & updated

---

## 🎯 Quick Navigation

### For Getting Started
1. **[01_OVERVIEW.md](01_OVERVIEW.md)** - Project overview, tech stack, architecture, setup & running services
2. **[CLAUDE.md](/CLAUDE.md)** - Quick setup & build commands

### For Development
3. **[02_API.md](02_API.md)** - Complete API specification (v5.3 RTS) with all endpoints & Kafka topics
4. **[03_BUSINESS.md](03_BUSINESS.md)** - Business logic, workflows & 9 workflows (v5.3)
5. **[04_POLICIES.md](04_POLICIES.md)** - System policies, trust score rules, account lifecycle (v3)
6. **[05_OPERATIONS.md](05_OPERATIONS.md)** - Data retention, 23 cronjobs & maintenance (v4)

### For Deep Dives
7. **[06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md)** - Stripe multi-vendor payment & Saga pattern
8. **[07_TESTING_GUIDE.md](07_TESTING_GUIDE.md)** - Testing guide & QA procedures
9. **[08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md)** - Integration details

### Architecture
10. **[erd.mermaid](erd.mermaid)** - Database entity-relationship diagram

---

## 📊 Documentation Overview

| Document | Version | Focus | Size |
|----------|---------|-------|------|
| 01_OVERVIEW.md | v1 | Project architecture, setup | 1,200+ lines |
| 02_API.md | v5.3 RTS | All endpoints, Kafka topics | 5,200+ lines |
| 03_BUSINESS.md | v5.3 RTS | Workflows, policies, 23 cronjobs | 719 lines |
| 04_POLICIES.md | v3 RTS | Trust score, account lifecycle | 514 lines |
| 05_OPERATIONS.md | v4.0 RTS | Data retention, cleanup | 713 lines |
| 06_PAYMENT_SAGA_FLOW.md | v2 | Payment flow, Stripe, Saga | TBD |
| 07_TESTING_GUIDE.md | v1 | Test scenarios | TBD |
| 08_PAYMENT_ORDER_INTEGRATION.md | v2 | Integration details | TBD |

**Total**: ~9,000+ lines of documentation covering all aspects

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
3. [CLAUDE.md](/CLAUDE.md) - Tech stack summary

---

## ✨ Key Features Documented

### Architecture
- ✅ 11 microservices (4 Axon + 7 traditional)
- ✅ Multi-vendor payment with Stripe Connect
- ✅ Event-driven with Kafka & Axon Framework
- ✅ Reactive services (WebFlux, R2DBC)
- ✅ 3 React frontend apps

### Business Logic
- ✅ 9 core workflows (auth, products, orders, payments, refunds, Flash Sale, loyalty, trust score, RTS)
- ✅ Order lifecycle (8 statuses)
- ✅ Refund system with RTS (Return To Sender) - v5.3
- ✅ Trust Score with appeal system
- ✅ Loyalty Points system
- ✅ Flash Sale anti-oversell mechanism

### Technical
- ✅ 40+ API endpoints documented
- ✅ 35+ Kafka topics with payloads
- ✅ 23 cronjobs with SQL logic
- ✅ Complete API JSON examples
- ✅ Error handling & validation rules

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

### Common Tasks
| Task | Document | Section |
|------|----------|---------|
| Set up local environment | CLAUDE.md | Build & Run Commands |
| Implement new API endpoint | 02_API.md | Any endpoint section |
| Understand order workflow | 03_BUSINESS.md | Luồng: Vòng Đời Đơn Hàng |
| Configure cronjob | 05_OPERATIONS.md | DANH SÁCH CRONJOB |
| Debug payment issue | 06_PAYMENT_SAGA_FLOW.md | Full document |
| Write test cases | 07_TESTING_GUIDE.md | Test scenarios |

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
     └→ 06_PAYMENT_SAGA_FLOW.md (payment detail)
        ├→ 08_PAYMENT_ORDER_INTEGRATION.md
        └→ 07_TESTING_GUIDE.md (testing)

erd.mermaid (database schema)
  └→ Used by all technical docs
```

---

## 📝 Version Information

### Current Versions
- **API**: v5.3 RTS (includes tracking number for refunds)
- **Business**: v5.3 RTS (includes Return To Sender)
- **Policies**: v3 RTS (with trust score & account lifecycle)
- **Operations**: v4.0 RTS (23 cronjobs)

### Tech Stack
- **Java**: 25 (LTS)
- **Spring Boot**: 4.0.4 (Latest)
- **React**: 19 (Latest)
- **Axon Framework**: 4.13.0
- **Kafka**: 7.4.0
- **PostgreSQL**: 15.4
- **MongoDB**: 6.0
- **Redis**: 7.0
- **Elasticsearch**: 8.10

---

## ✅ Documentation Checklist

| Item | Status | Details |
|------|--------|---------|
| **Architecture** | ✅ Complete | 11 services, 3 apps documented |
| **API Endpoints** | ✅ Complete | 40+ endpoints with examples |
| **Business Workflows** | ✅ Complete | 9 workflows, all detailed |
| **System Policies** | ✅ Complete | Trust score, refund, loyalty |
| **Cronjobs** | ✅ Complete | 23 jobs with SQL logic |
| **Kafka Topics** | ✅ Complete | 35+ topics documented |
| **Error Handling** | ✅ Complete | All error codes defined |
| **Database Schema** | ✅ Complete | ERD provided |
| **Setup Guide** | ✅ Complete | 3 options (Docker, Local, Prod) |
| **Testing Guide** | ✅ Complete | Test scenarios defined |

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
4. Try API in Postman/Insomnia

### Advanced (8+ hours)
1. [05_OPERATIONS.md](05_OPERATIONS.md) — Cronjobs & data flow
2. [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) — Saga pattern
3. [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) — Deep dive
4. [07_TESTING_GUIDE.md](07_TESTING_GUIDE.md) — Testing strategies

---

## 📞 Updates & Maintenance

### Last Updated
- **2026-04-20** — Documentation reorganized & consolidated
- **2026-04-15** — API v5.3 with tracking number for refunds
- **2026-04-14** — Business docs & system policies

### How to Update
When making changes:
1. Update relevant doc file
2. Update this INDEX with new version
3. Update version numbers in affected docs
4. Commit with message: "docs: update [doc-name] for [feature]"

---

## 🚀 Summary

This documentation provides **complete, production-ready guidance** for the Flash Sale E-Commerce Platform:

- ✅ **Comprehensive**: 9,000+ lines covering all aspects
- ✅ **Organized**: Clear navigation by role & task
- ✅ **Practical**: Real commands, examples, SQL queries
- ✅ **Current**: Latest versions (v5.3 API, v3 Policies, v4 Operations)
- ✅ **Complete**: Business, technical, operational, testing

**Start with 01_OVERVIEW.md for the full picture!**  
**Use CLAUDE.md for quick setup!**  
**Reference 02_API.md for endpoint details!**

---

**Ready for production! 🚀**

