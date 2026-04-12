# 📚 Project Documentation Index

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)  
**Last Updated**: 2026-04-12  
**Status**: Complete & Production-Ready

---

## 🎯 Quick Links

| Document | Purpose | Lines | Type |
|----------|---------|-------|------|
| [PROJECT_OVERVIEW.md](#project_overviewmd) | **START HERE** - Complete project guide | 1,117 | Comprehensive |
| [README.md](#readmemd) | Quick start and overview | 372 | Quick Start |
| [BACKEND_GUIDE.md](#backend_guidemd) | Backend architecture & services | 507 | Technical |
| [FRONTEND_GUIDE.md](#frontend_guidemd) | Frontend structure & development | 338 | Technical |
| [BUILD_AND_DOCKER_GUIDE.md](#build_and_docker_guidemd) | Build & deployment commands | 542 | Operations |
| [JAVA_SPRING_BOOT_CONFIG.md](#java_spring_boot_configmd) | Java 25 & Spring Boot 4.0.4 config | 335 | Technical |
| [AXON_EXPLANATION.md](#axon_explanationmd) | Axon Framework detailed guide | 410 | Technical |

---

## 📄 Documentation Details

### PROJECT_OVERVIEW.md

**Status**: ✅ **COMPLETE** (2026-04-12)  
**Size**: 1,117 lines, 28,881 bytes

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

### README.md

**Status**: ✅ Updated  
**Size**: 372 lines

**Contents**:
- Project overview
- Tech stack highlights
- Quick start guide
- Project structure diagram
- Documentation links
- API documentation
- Troubleshooting quick guide
- Development workflow
- Deployment steps

**Who Should Read**: Quick starters, project overview seekers

---

### BACKEND_GUIDE.md

**Status**: ✅ Enhanced with Axon Services Documentation  
**Size**: 507 lines

**Contents**:
- Backend architecture overview
- Project structure for services
- Setup & build instructions (Java 25)
- Database setup (PostgreSQL, MongoDB, Redis)
- Service configuration
- API Gateway configuration
- Authentication flow
- Inter-service communication (HTTP, Kafka, gRPC)
- Event Sourcing with Axon Framework (NEW!)
  - Directory structure for Axon services
  - Key components explained
  - Event flow diagrams
  - Code examples
  - Configuration examples
  - Comparison with traditional services

**Who Should Read**: Backend developers, DevOps engineers

---

### FRONTEND_GUIDE.md

**Status**: ✅ Updated  
**Size**: 338 lines

**Contents**:
- Frontend overview (3 apps)
- Frontend structure
- Setup instructions
- Development mode
- Production build
- Configuration (Vite, environment)
- Common tasks
- Testing
- Docker build
- Best practices
- Troubleshooting
- Related documentation links

**Who Should Read**: Frontend developers, UI/UX developers

---

### BUILD_AND_DOCKER_GUIDE.md

**Status**: ✅ Updated  
**Size**: 542 lines

**Contents**:
- Building backend locally
- Building frontend locally
- Docker build commands
- Docker run commands
- Docker management
- Logs and cleanup
- Production deployment
- Full deployment workflow (5 steps)
- Health checks
- Functionality verification
- Update strategies (rolling, blue-green, canary)
- Scaling (horizontal scaling, load balancing)
- Troubleshooting (service startup, ports, memory, networking)
- Monitoring (resource usage, metrics)
- Pre-production checklist

**Who Should Read**: DevOps engineers, deployment specialists

---

### JAVA_SPRING_BOOT_CONFIG.md

**Status**: ✅ Complete  
**Size**: 335 lines

**Contents**:
- Java 25 configuration
- Spring Boot 4.0.4 configuration
- Spring Cloud 2025.1.1 configuration
- Axon Framework 4.13.0 configuration
- Docker configuration (Eclipse Temurin JRE 25)
- pom.xml properties
- New features (Virtual Threads, Pattern Matching, Records, Text Blocks)
- Performance improvements
- Production JVM flags
- Container limits
- Deployment steps
- Troubleshooting (version mismatch, build failures, Docker issues)
- Compatibility matrix

**Who Should Read**: Backend developers, DevOps engineers, system architects

---

### AXON_EXPLANATION.md

**Status**: ✅ Complete  
**Size**: 410 lines (Vietnamese + English)

**Contents**:
- Quick summary (Axon Framework vs Axon Server)
- Axon Framework definition
- Axon Server definition
- Detailed comparison
- Event flow diagrams
- Connection setup
- Services using Axon
- Services not using Axon
- Architecture diagrams
- Code structure examples
- Differences and use cases
- Complete tóm tắt (summary in Vietnamese)

**Who Should Read**: Developers using event sourcing, system architects, new team members

---

### Supporting Documentation

#### FRONTEND_CLEANUP_COMPLETE.md
- Frontend refactoring summary
- Duplicate file removal
- Shared code consolidation
- File statistics

#### AXON_SERVICES_DOCUMENTATION_COMPLETE.md
- Axon services detailed documentation
- Directory structure enhancement
- Component explanations
- Code examples

#### DOCUMENTATION_SUMMARY.md
- Documentation integration summary
- Cross-reference map
- Integration completeness

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
| **Deployment** | ✅ Complete | Production-ready |
| **Troubleshooting** | ✅ Complete | Common issues solved |

### Quantity

- **Total Documentation Files**: 8 comprehensive files
- **Total Lines**: 4,500+ lines
- **Code Examples**: 100+ real examples
- **Tables**: 30+ reference tables
- **Diagrams**: 10+ architecture diagrams
- **Commands**: 200+ executable commands

---

## 🎓 How to Use This Documentation

### For Different Roles

#### **New Developer**
1. Start: PROJECT_OVERVIEW.md (full picture)
2. Read: BACKEND_GUIDE.md or FRONTEND_GUIDE.md (based on role)
3. Setup: Follow setup instructions
4. Reference: AXON_EXPLANATION.md (if backend), BUILD_AND_DOCKER_GUIDE.md (for builds)

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
- **Last Updated**: 2026-04-12
- **Java**: 25 (LTS)
- **Spring Boot**: 4.0.4 (Latest)
- **React**: 19 (Latest)
- **Axon Framework**: 4.13.0 (Latest stable)

### Keep Documentation Updated
- Update when tech versions change
- Add new services or features
- Fix discovered issues
- Clarify confusing sections
- Add real-world examples

---

## 🎉 Summary

This documentation set provides **complete, production-ready guidance** for the Flash Sale E-Commerce Platform:

- ✅ **Comprehensive**: Everything you need to know
- ✅ **Organized**: Easy to navigate and find info
- ✅ **Practical**: Real commands and examples
- ✅ **Professional**: Production-grade quality
- ✅ **Updated**: Current as of 2026-04-12

**Start with PROJECT_OVERVIEW.md for the complete picture!**

---

**Ready for production deployment! 🚀**

