# UC-IDENTITY-005: Admin Lock/Unlock User
Service: identity-service

| Property | Value |
|----------|-------|
| Use Case ID | UC-IDENTITY-005 |
| Title | Admin Locks or Unlocks User Account |
| Actor | Admin (role=ADMIN) |
| Precondition | Admin has valid JWT with ADMIN role; target user exists |
| Postcondition | User.status changed to LOCKED or ACTIVE; all active tokens revoked (if locking) |
| Trigger | Admin selects a user and clicks Lock or Unlock |
| Business Rules | BR-IDENTITY-003, BR-IDENTITY-008, BR-IDENTITY-009 |
| Entities | ENTITY-IDENTITY-001 (User), ENTITY-IDENTITY-005 (Admin) |
| APIs | POST /admin/users/{userId}/lock, POST /admin/users/{userId}/unlock |
| Kafka Events | account.locked (post-MVP), account.unlocked (post-MVP) |

### Main Flow: Lock User
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Admin | Submits userId and reason | BR-IDENTITY-008: admin role checked |
| 2 | System | Validates target user exists | -- |
| 3 | System | Sets User.status = LOCKED | ENTITY-IDENTITY-001 |
| 4 | System | Revokes all active JWT tokens for user | BR-IDENTITY-009 (Redis blocklist) |
| 5 | System | Publishes account.locked (post-MVP) to Kafka | FR-IDENTITY-014 |
| 6 | System | Returns 200 with confirmation | FR-IDENTITY-012 |

### Main Flow: Unlock User
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Admin | Submits userId | BR-IDENTITY-008: admin role checked |
| 2 | System | Validates target user exists | -- |
| 3 | System | Sets User.status = ACTIVE | ENTITY-IDENTITY-001 |
| 4 | System | Publishes account.unlocked (post-MVP) to Kafka | FR-IDENTITY-014 |
| 5 | System | Returns 200 with confirmation | FR-IDENTITY-013 |

### Main Flow: List Users (Admin)
| Step | Actor | Action | Validations & Rules |
|------|-------|--------|---------------------|
| 1 | Admin | Requests user list with optional filters | Query: status, role, q, page, size |
| 2 | System | Queries USERS with filters | ENTITY-IDENTITY-001 |
| 3 | System | Returns paginated user list | FR-IDENTITY-011 |

### Postcondition: Lock Side Effects
| Effect | Mechanism | Impact |
|--------|-----------|--------|
| Login rejected | BR-IDENTITY-003 | Any login attempt returns 403 |
| Active tokens revoked | Redis blocklist | All existing JWTs invalidated |
| Kafka event emitted | account.locked (post-MVP) | Notification Service notifies user; Search Service hides products |

### Alternate Flows
| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User not found | Return 404 |
| A2 | Requester not ADMIN | Return 403 (BR-IDENTITY-008) |
| A3 | User already LOCKED on lock | Idempotent -- return 200 |
| A4 | User already ACTIVE on unlock | Idempotent -- return 200 |

### Downstream Consumers
| Consumer | Event | Action |
|----------|-------|--------|
| Notification Service | account.locked (post-MVP) | Notify user of account lock |
| Notification Service | account.unlocked (post-MVP) | Notify user of account unlock |
| Search Service | account.locked (post-MVP) | Hide seller's products from search |
