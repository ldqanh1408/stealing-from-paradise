# Business Flow: Identity Access, Profile, and Address
Scope: `identity-service`

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-IDENTITY-001: Customer Registration | Implemented | `AuthController.register` line 67, `AuthService.registerUser` line 42 | Creates a BUYER user and returns auth tokens. |
| UC-IDENTITY-002: User Login | Implemented | `AuthController.login` line 45, `AuthService.authenticateUser` line 114 | Supports credential login, refresh, and logout token blacklist. |
| UC-IDENTITY-003: Manage Profile | Implemented | `UserController.getCurrentUser` line 29, `UserController.updateCurrentUser` line 35, `UserController.changePassword` line 92 | Avatar presigned URL is also implemented at line 43. |
| UC-IDENTITY-004: Manage Addresses | Implemented | `UserController.getAddresses` line 60, `addAddress` line 66, `updateAddress` line 74, `deleteAddress` line 83 | `AddressKafkaConsumer` line 34 also serves cross-service address snapshots. |
| UC-IDENTITY-006: Seller Registration | Partial | `AuthController.registerSeller` line 135, `UserController.registerAsSeller` line 100, `UserService.registerAsSeller` line 154 | REST role assignment is implemented; no seller-registration Kafka event was found. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant GW as API Gateway
    participant Auth as AuthController
    participant Users as UserController
    participant Svc as Identity Services
    participant DB as PostgreSQL
    participant Kafka as Kafka
    participant Order as Order or Flashsale Service

    alt Register or login
        User->>GW: POST /api/v1/auth/register or /login
        GW->>Auth: Route auth request
        Auth->>Svc: registerUser or authenticateUser
        Svc->>DB: Read/write USERS and ROLES
        Svc-->>User: AuthResponse with access and refresh tokens
    else Profile and password
        User->>GW: GET/PUT /api/v1/users/me
        GW->>Users: Route profile request
        Users->>Svc: getUserProfile or updateUserProfile
        Svc->>DB: Read/write USERS
        Users-->>User: UserProfileResponse
    else Address CRUD
        User->>GW: /api/v1/users/me/addresses
        GW->>Users: Route address request
        Users->>Svc: Address CRUD methods
        Svc->>DB: Read/write ADDRESSES
        Users-->>User: AddressResponse
    else Cross-service address snapshot
        Order->>Kafka: order.address_request
        Kafka->>Svc: AddressKafkaConsumer.onAddressRequest
        Svc->>DB: Load default or requested address
        Svc->>Kafka: order.address_response
    end
```

### Implementation Gaps

| Gap | Impact |
|-----|--------|
| Seller role assignment does not publish a seller-registration event. | Downstream services cannot react asynchronously to seller onboarding from identity-service alone. |
| Admin lock/unlock endpoints exist in code but are not part of the current active identity use cases. | They are excluded from this business flow until the use case set reintroduces them. |
