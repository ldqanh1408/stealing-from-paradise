# 🔐 Identity Service API

**Service**: Identity Service (+ Loyalty v5.3)  
**Port**: 8081  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Identity Service handles:
- User registration & login (JWT RS256)
- User profiles & addresses  
- Trust Score management & appeals
- Seller registration
- **Loyalty points management** (consolidated v5.3)

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `account.locked` | Notification, Search | Account locked notification |
| `account.auto_locked` | Notification | Auto-locked (low trust score) |
| `account.unlocked` | Notification | Account unlocked |
| `appeal.resolved` | Notification | Appeal decision |
| `loyalty.points_earned` | Notification | Points credited |

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `order.delivered` | Order Service | Credit loyalty points |
| `order.cancelled` | Order Service | Refund loyalty points |

## Key Endpoints

### Authentication
```
POST   /auth/register           Register new account
POST   /auth/login              Login (get JWT)
POST   /auth/refresh            Refresh access token
POST   /auth/logout             Logout (revoke token)
```

### User Profile
```
GET    /users/me                Get user profile
PUT    /users/me                Update profile
GET    /users/me/avatar/presigned-url    Upload avatar URL
```

### Address Management
```
GET    /users/me/addresses              List addresses
POST   /users/me/addresses              Add address
PUT    /users/me/addresses/{id}         Update address
DELETE /users/me/addresses/{id}         Delete address
```

### Trust Score & Appeals
```
GET    /users/me/trust-score/logs              Trust score history
POST   /support/trust-score-appeal             Submit appeal
GET    /support/trust-score-appeal/presigned-url   Appeal evidence upload
```

### Loyalty Service (NEW v5.3)
```
GET    /loyalty/balance         Get loyalty points balance
GET    /loyalty/transactions    Loyalty transaction history
GET    /loyalty/estimate        Estimate points for order
```

### Seller
```
POST   /users/me/roles/seller   Register as seller
```

## Total Endpoints: 21

## For Complete Documentation

→ See **[/docs/api/01-identity-service.md](../api/01-identity-service.md)**

Contains:
- Full request/response examples
- Error responses & status codes
- Query parameters documentation
- Integration points with other services

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

