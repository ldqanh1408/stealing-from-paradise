# State Diagram: User Account Lifecycle
Service: identity-service
Entity: ENTITY-IDENTITY-001 (User)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : POST /auth/register\nUC-IDENTITY-001\nBR-IDENTITY-001, BR-IDENTITY-002

    ACTIVE --> LOCKED : POST /admin/users/{id}/lock\nUC-IDENTITY-005\nBR-IDENTITY-008, BR-IDENTITY-009

    LOCKED --> ACTIVE : POST /admin/users/{id}/unlock\nUC-IDENTITY-005\nBR-IDENTITY-008

    note right of ACTIVE
        All operations permitted:
        - Login (UC-IDENTITY-002)
        - Profile management (UC-IDENTITY-003)
        - Address CRUD (UC-IDENTITY-004)
        - Seller registration (UC-IDENTITY-006)
    end note

    note right of LOCKED
        All operations denied:
        - Login rejected (BR-IDENTITY-003)
        - All active tokens revoked (BR-IDENTITY-009)
        - Products hidden from search
        - Posting suspended (if SELLER)
    end note
```

## State Transition Table

| From | To | Trigger | Actor | BR | UC |
|------|----|---------|-------|-----|-----|
| [*] | ACTIVE | POST /auth/register | Guest | BR-IDENTITY-001, BR-IDENTITY-002 | UC-IDENTITY-001 |
| [*] | ACTIVE | POST /auth/register/seller | Guest | BR-IDENTITY-001, BR-IDENTITY-002 | UC-IDENTITY-006 |
| ACTIVE | LOCKED | POST /admin/users/{id}/lock | Admin | BR-IDENTITY-008, BR-IDENTITY-009 | UC-IDENTITY-005 |
| LOCKED | ACTIVE | POST /admin/users/{id}/unlock | Admin | BR-IDENTITY-008 | UC-IDENTITY-005 |

## Lock Side Effects
| Effect | Mechanism |
|--------|-----------|
| Login blocked | BR-IDENTITY-003 returns HTTP 403 |
| Tokens revoked | Redis blocklist -- all JWTs invalidated (BR-IDENTITY-009) |
| Seller products hidden | Search Service consumes account.locked event |
| Notification sent | account.locked -> Notification Service |

## Idempotency
| Operation | Current State | Result |
|-----------|--------------|--------|
| Lock | Already LOCKED | 200 (no-op) |
| Unlock | Already ACTIVE | 200 (no-op) |
