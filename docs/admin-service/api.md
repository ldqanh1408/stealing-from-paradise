# 🛡️ Admin Service API

**Service**: Admin Service  
**Port**: Cross-service (via API Gateway)  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Admin Service provides:
- Product moderation (approval/rejection)
- User management (lock/unlock, trust score)
- Appeal management
- Failed events retry
- System configuration

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `product.approved` | Search Service | Index approved product |
| `product.rejected` | Notification Service | Rejection notification |

### Consumes (Events Listened)
- None directly

## Key Endpoints

### Product Moderation
```
GET    /admin/products/pending              List pending products
POST   /admin/products/{id}/approve         Approve product
POST   /admin/products/{id}/reject          Reject product
```

### User Management
```
GET    /admin/users                         List users (filters)
POST   /admin/users/{id}/lock               Lock account
POST   /admin/users/{id}/unlock             Unlock account
POST   /admin/users/{id}/trust-score        Adjust trust score
GET    /admin/users/{id}/trust-score/logs   Trust score history
GET    /admin/users/{id}/ban-history        Ban history
POST   /admin/users/{id}/unlock-product-posting    Resume posting
```

### Trust Score Configuration
```
GET    /admin/trust-score-events-config            Get config
PUT    /admin/trust-score-events-config/{code}    Update config
```

### Appeal Management
```
GET    /admin/appeals                       List appeals
POST   /admin/appeals/{id}/resolve          Resolve appeal
```

### Failed Events Management
```
GET    /admin/failed-events                 List failed events
POST   /admin/failed-events/{id}/retry      Retry event
POST   /admin/failed-events/{id}/resolve    Mark as resolved
```

## Required Permissions

**All endpoints require**:
- JWT token with `ADMIN` role
- Additional scope checks per endpoint

## Total Endpoints: 14

## For Complete Documentation

→ See **[/docs/api/09-admin-service.md](../api/09-admin-service.md)**

Contains:
- Full moderation workflows
- User management procedures
- Appeal resolution details
- Failed events retry patterns
- Trust score configuration examples

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

