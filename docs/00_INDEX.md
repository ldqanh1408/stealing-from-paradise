# 📚 Project Documentation Index

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)  
**Last Updated**: 2026-04-15  
**Status**: Complete & Production-Ready  
**Latest Update**: Tracking number for refunds (v5.3) added to API & Business docs

---

## 🎯 Quick Links

| Document | Purpose | Type |
|----------|---------|------|
| [PROJECT_OVERVIEW.md](#project_overviewmd) | **START HERE** - Complete project guide | Comprehensive |
| [API_SPEC_v5_3_RTS.md](#api_spec) | Complete API specification (v5.3) | Technical |
| [API_DETAILED_JSON_v5_3_RTS.md](#api_detailed_json) | **JSON Requests/Responses & Kafka Payloads** | Technical Reference |
| [BUSINESS_DOC_v5_3_rts_unified.md](#business_doc) | Business requirements & workflows | Business |
| [SYSTEM_POLICY_v3_rts_unified.md](#system_policy) | System policies & configuration | Technical |
| [DATA_RETENTION_POLICY_v4_rts.md](#data_retention) | Data retention & cronjobs (22 jobs) | Operations |
| [DOCUMENTATION_INDEX.md](#documentation_indexmd) | This file - Documentation navigation | Reference |
| [erd.mermaid](#erd_mermaid) | Database Entity-Relationship Diagram | Architecture |

---

## 📄 Documentation Details

**Status**: ✅ **8 Core Documentation Files** (2026-04-15)
**Latest Update**: API_DETAILED_JSON_v5_3_RTS.md with full JSON examples & Kafka payloads
**Total**: 8 markdown files + diagrams (7,000+ lines)

---

### PROJECT_OVERVIEW.md

**Contents**:
- Project overview with features
- Complete tech stack
- Backend architecture (11 services)
- Axon Framework services structure
- Traditional database services structure
- Frontend architecture (3 apps)
- Project root structure
- Setup & installation guide
- Running services (3 options: Docker, Local, Production)
- Development workflow
- Database initialization
- API endpoints reference
- Monitoring & logging
- Deployment procedures
- Troubleshooting guide
- Contributing guidelines
- Support information

**Who Should Read**: Everyone (new team members, developers, DevOps, managers)

**Key Sections**:
1. Project Overview (features, roles, capabilities)
2. Tech Stack (versions, components, tools)
3. Backend Architecture (services diagram, detailed descriptions)
4. Backend Directory Structures (Axon vs Traditional comparison)
5. Frontend Architecture (3 apps, shared code, data flow)
6. Setup & Installation (prerequisites, environment)
7. Running Services (Docker, Local, Production)
8. Development Workflow (backend & frontend)
9. Database Initialization
10. API Endpoints (base URL, authentication, resources)
11. Monitoring & Logging
12. Deployment (checklist, steps, Kubernetes)
13. Troubleshooting
14. Contributing

---

### API_DETAILED_JSON_v5_3_RTS.md

**Status**: ✅ **NEW (2026-04-15)**  
**Size**: 1,200+ lines with complete examples

**Contents**:
- 🔐 Identity Service APIs (register, login, logout, trust score appeal)
- 📦 Product Service APIs (create, variants, submit, inventory)
- 🔍 Search Service APIs (products search, autocomplete)
- 🛒 Cart Service APIs (get, add items, update, delete)
- 📋 Order Service APIs (checkout multi-vendor, cancel, tracking, confirm, RTS)
- ↩️ Refund APIs (full, partial, admin approval with tracking number - NEW v5.3)
- 💳 Payment Service APIs (Stripe onboarding, payment status)
- ⭐ Loyalty Service APIs (balance, estimate, transactions)
- ⚡ Flash Sale Service APIs (sessions, items, buy)
- 🔔 Notification Service APIs (stream SSE, list, read status)
- 🛡️ Admin APIs (user management, trust score, appeals, failed events)
- 🧭 Kafka Topics & Payloads (35+ topics with full JSON)
- ❌ Error Response Formats (standard, validation, invalid state, locked)

**Key Features**:
- ✅ **Complete JSON Examples**: All request/response objects
- ✅ **Validation Rules**: Every field with type, range, constraints
- ✅ **Kafka Payloads**: All 35+ event topics with detailed fields
- ✅ **Multi-Vendor Examples**: Order checkout with 2+ sellers
- ✅ **RTS (Return To Sender)**: Complete v5.3 workflow
- ✅ **Tracking Numbers**: Admin refund approval with tracking
- ✅ **Error Scenarios**: All error response types
- ✅ **Side Effects**: Redis, Database, and Kafka impacts

**Who Should Read**: Frontend developers (API integration), Backend developers (implementation reference), QA (test data structures), API consumers

---

### BUSINESS_DOC_v5_3_rts_unified.md

**Status**: ✅ **NEW (2026-04-14)**  
**Size**: 719 lines, 25,314 bytes

**Contents**:
- Project overview & statistics (9 workflows, 6 policies, 23 cronjobs)
- Vai trò & Quyền hạn (Buyer, Seller, Admin)
- 9 Luồng nghiệp vụ chính:
  - Xác thực & Quản lý tài khoản
  - Vòng đời sản phẩm
  - Vòng đời đơn hàng (8 trạng thái)
  - Thanh toán Stripe multi-vendor
  - Hoàn tiền Buyer (Refund)
  - Return To Sender (RTS) - NEW v5.3
  - Flash Sale (chống oversell)
  - Điểm Tích Lũy (Loyalty Points)
  - Trust Score & Khiếu Nại (Appeal)
- 6 Chính sách chi tiết
- 23 Cronjobs định kỳ

**Key Features**:
- ✅ Complete workflow diagrams
- ✅ State transition models
- ✅ Stripe multi-vendor payment flow
- ✅ Return To Sender (RTS) process (NEW v5.3)
- ✅ Tracking number for refunds
- ✅ Trust Score tier mapping
- ✅ Loyalty points detailed breakdown
- ✅ Flash Sale concurrency handling

**Who Should Read**: Product managers, business analysts, team leads, technical stakeholders

---

#### SYSTEM_POLICY_v3_rts_unified.md

**Status**: ✅ **NEW (2026-04-14)**  
**Size**: 645 lines, 22,441 bytes

**Contents**:
- 1. Nguyên Tắc Chung (7 nguyên tắc)
- 2. Danh Sách Cronjob - chi tiết toàn bộ 22 jobs
- 3. Policy Tổng Hợp Theo Bảng (PostgreSQL, MongoDB, Elasticsearch)
- 4. Bảng Tóm Tắt Cronjob
- 5. External Storage & Cache Policy (Redis, MinIO)
- 6. Checklist Triển Khai

**Key Features**:
- Trust Score policy (thang điểm, sự kiện trừ/cộng, ngưỡng kích hoạt)
- Account Lifecycle (trạng thái, khóa tạm thời, JWT revocation)
- Flash Sale participation rules
- Seller policies & onboarding
- Refund policy
- Loyalty points system
- Schema bổ sung (v3 RTS)

**Who Should Read**: Backend developers, system architects, DevOps engineers

---

#### DATA_RETENTION_POLICY_v4_rts.md

**Status**: ✅ **NEW (2026-04-14)**  
**Size**: 892 lines, 36,851 bytes

**Contents**:
- 1. NGUYÊN TẮC CHUNG (7 nguyên tắc core)
- 2. DANH SÁCH CRONJOB (22 jobs chi tiết):
  - JOB-01: Flash Sale Session Lifecycle Manager
  - JOB-02: Flash Sale Reminder Dispatcher
  - JOB-03: Loyalty Points Expiry
  - JOB-04 — JOB-22: Cleanup, auto-lock, detection, reconciliation
  - [GAP-PATCH] JOB-21: Stock Reconciliation
  - [GAP-PATCH R1] JOB-22: Auto-Delivered Stale SHIPPING Orders
- 3. POLICY TỔNG HỢP THEO BẢNG (PostgreSQL, MongoDB, Elasticsearch)
- 4. BẢNG TÓM TẮT CRONJOB
- 5. EXTERNAL STORAGE & CACHE POLICY (Redis, MinIO)
- 6. CHECKLIST TRIỂN KHAI

**Key Features**:
- Complete retention policies for all tables
- Cron schedules for all 22 jobs
- SQL logic for each job
- MongoDB/JavaScript examples
- Redis key patterns
- MinIO bucket policies
- Deployment checklist

**Who Should Read**: DevOps engineers, system operators, database admins, infrastructure team

---

## 📊 Documentation Statistics

### Coverage

| Area | Status | Details |
|------|--------|---------|
| **Project Overview** | ✅ Complete | Full project description |
| **Backend Architecture** | ✅ Complete | 11 services documented |
| **Axon Services** | ✅ Complete | 4 services + structures |
| **Traditional Services** | ✅ Complete | 7 services documented |
| **Frontend Apps** | ✅ Complete | 3 apps + shared code |
| **Setup & Installation** | ✅ Complete | 3 options provided |
| **API Reference** | ✅ Complete | All endpoints listed |
| **API JSON Examples** | ✅ Complete | 60+ request/response examples (NEW) |
| **Kafka Payloads** | ✅ Complete | 35+ topics with full JSON (NEW) |
| **Deployment** | ✅ Complete | Production-ready |
| **Troubleshooting** | ✅ Complete | Common issues solved |
| **Business Requirements** | ✅ Complete | 9 workflows, 6 policies (v5.3) |
| **System Policies** | ✅ Complete | Trust Score, Flash Sale, Refund, Loyalty (v3.0) |
| **Data Retention** | ✅ Complete | 22 cronjobs, retention policies (v4.0) |

### Quantity

- **Total Documentation Files**: 8 markdown files
- **Total Lines**: 7,000+ lines
- **Code Examples**: 150+ real examples
- **Tables**: 50+ reference tables
- **SQL Queries**: 100+ database operations
- **Diagrams**: Architecture diagrams + ERD
- **Commands**: 250+ executable commands
- **Cronjobs**: 22 documented jobs
- **JSON Payloads**: 60+ request/response examples
- **Kafka Topics**: 35+ event topics documented

---

## 🎓 How to Use This Documentation

### For Different Roles

#### **New Developer**
1. Start: PROJECT_OVERVIEW.md (full picture)
2. Read: BACKEND_GUIDE.md or FRONTEND_GUIDE.md (based on role)
3. API Ref: API_DETAILED_JSON_v5_3_RTS.md (JSON examples for integration)
4. Setup: Follow setup instructions
5. Reference: AXON_EXPLANATION.md (if backend), BUILD_AND_DOCKER_GUIDE.md (for builds)

#### **Backend Developer**
1. Start: PROJECT_OVERVIEW.md (Backend Architecture section)
2. Deep dive: BACKEND_GUIDE.md
3. Event sourcing: AXON_EXPLANATION.md
4. Configuration: JAVA_SPRING_BOOT_CONFIG.md
5. Build/Deploy: BUILD_AND_DOCKER_GUIDE.md

#### **Frontend Developer**
1. Start: PROJECT_OVERVIEW.md (Frontend Architecture section)
2. Deep dive: FRONTEND_GUIDE.md
3. Build: BUILD_AND_DOCKER_GUIDE.md
4. Troubleshoot: FRONTEND_GUIDE.md + BUILD_AND_DOCKER_GUIDE.md

#### **DevOps Engineer**
1. Start: PROJECT_OVERVIEW.md (Tech Stack & Deployment)
2. Deployment: BUILD_AND_DOCKER_GUIDE.md
3. Configuration: JAVA_SPRING_BOOT_CONFIG.md
4. Reference: BACKEND_GUIDE.md (service config)

#### **Team Lead / Manager**
1. Start: PROJECT_OVERVIEW.md (Project Overview + Tech Stack)
2. Architecture: Backend Architecture section
3. Deployment: Deployment section

---

## 🔍 Finding Specific Information

### "How do I...?"

| Question | Document | Section |
|----------|----------|---------|
| Start development locally? | PROJECT_OVERVIEW.md | Running Services → Local Development |
| Deploy to production? | BUILD_AND_DOCKER_GUIDE.md | Deployment |
| Understand Axon Framework? | AXON_EXPLANATION.md | Full document |
| Set up Java 25? | JAVA_SPRING_BOOT_CONFIG.md | Prerequisites |
| Create new Axon service? | BACKEND_GUIDE.md | Event Sourcing with Axon |
| Add new frontend page? | FRONTEND_GUIDE.md | Common Tasks |
| Debug build error? | BUILD_AND_DOCKER_GUIDE.md | Troubleshooting |
| Understand project structure? | PROJECT_OVERVIEW.md | Project Structure section |
| Integrate with APIs? | API_DETAILED_JSON_v5_3_RTS.md | Identity/Product/Order/etc endpoints |
| Handle Kafka events? | API_DETAILED_JSON_v5_3_RTS.md | Kafka Topics & Payloads |
| Implement multi-vendor checkout? | API_DETAILED_JSON_v5_3_RTS.md | Order Service → POST /orders/checkout |
| Handle refund with tracking number? | API_DETAILED_JSON_v5_3_RTS.md | Refund APIs → Admin approve (v5.3) |

---

## 🚀 Quick Start Path

### Path 1: Full Setup (5 minutes)
```
1. PROJECT_OVERVIEW.md → Setup & Installation
2. docker-compose up -d
3. Open: http://localhost:3000 (customer), 3001 (seller), 3002 (admin)
```

### Path 2: Local Backend (10 minutes)
```
1. BACKEND_GUIDE.md → Setup & Build
2. JAVA_SPRING_BOOT_CONFIG.md → Prerequisites
3. cd backend; mvn clean install
4. Start services individually
```

### Path 3: Local Frontend (5 minutes)
```
1. FRONTEND_GUIDE.md → Setup & Install Dependencies
2. cd frontend/apps/customer; npm install
3. npm run dev
4. Open: http://localhost:3000
```

---

## ✨ Key Features

### Comprehensive
✅ Covers every aspect of the project  
✅ Multiple perspectives (developer, DevOps, manager)  
✅ Real-world examples and commands  

### Organized
✅ Clear table of contents  
✅ Logical flow and structure  
✅ Cross-referenced between documents  

### Practical
✅ Copy-paste commands  
✅ Step-by-step instructions  
✅ Troubleshooting guides  

### Visual
✅ Architecture diagrams  
✅ Reference tables  
✅ Code examples  

### Actionable
✅ Specific, not vague  
✅ Complete, not partial  
✅ Current, regularly updated  

---

## 📞 Support & Updates

### Version Info
- **Last Updated**: 2026-04-14
- **Java**: 25 (LTS)
- **Spring Boot**: 4.0.4 (Latest)
- **React**: 19 (Latest)
- **Axon Framework**: 4.13.0 (Latest stable)
- **Business Doc Version**: v5.3 RTS
- **System Policy Version**: v3.0 RTS Unified
- **Data Retention Version**: v4.0 RTS

### Keep Documentation Updated
- Update when tech versions change
- Add new services or features
- Fix discovered issues
- Clarify confusing sections
- Add real-world examples

---

## 🎉 Summary

This documentation set provides **complete, production-ready guidance** for the Flash Sale E-Commerce Platform:

- ✅ **Comprehensive**: Everything you need to know (8 files, 7,000+ lines)
- ✅ **Organized**: Easy to navigate and find info
- ✅ **Practical**: Real commands, SQL, examples, JSON requests/responses
- ✅ **Professional**: Production-grade quality
- ✅ **Updated**: Current as of 2026-04-15
- ✅ **Complete**: Business, technical, operational, API perspectives

**Includes**:
- Architecture & Setup (Project Overview, Backend/Frontend Guides)
- Technical Details (Java Config, Axon Framework, Build & Docker)
- Business Logic (Business Doc v5.3, System Policy v3.0)
- Operations (Data Retention v4.0 with 22 cronjobs)
- API Reference (Complete JSON with 60+ examples, Kafka payloads)

**Start with PROJECT_OVERVIEW.md for the complete picture!**
**Use API_DETAILED_JSON_v5_3_RTS.md for API integration!**

---

**Ready for production deployment! 🚀**

