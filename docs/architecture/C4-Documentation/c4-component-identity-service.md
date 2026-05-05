# C4 Component Level: Identity Service

## Overview

- **Name**: Identity Service
- **Description**: Central authentication and user management component handling JWT token generation/validation, user registration and login, role-based access control (BUYER/SELLER/ADMIN), address management, token blacklisting, and session management. Uses PostgreSQL for persistence and Redis for token blacklist/session storage. Communicates with other services via Kafka for address lookups and via REST for internal user queries.
- **Type**: Service
- **Technology**: Java 25, Spring Boot 4.0.4, PostgreSQL (schema: `identity`), Redis, JWT (RS256 encryption), Flyway, Spring Security

## Purpose

The Identity Service is the single source of truth for all user identity and authentication concerns in the FlashSale marketplace platform. It provides:

- **Authentication**: User login via credential (username, email, or phone), password validation with BCrypt, and JWT access+refresh token generation. Supports domain-based role determination (seller/admin/customer subdomain routing) for multi-tenant deployments.
- **Registration**: Self-service registration for buyers (default BUYER role) and sellers (SELLER role), with validation for duplicate username, email, and phone.
- **Authorization**: Role-based access control with three roles (BUYER, SELLER, ADMIN). Roles are stored in a separate `roles` table and used by controllers via `@PreAuthorize`. The `CustomUserDetailsService` loads users and roles for Spring Security.
- **Token Management**: JWT refresh flow and logout-based token blacklisting via Redis with TTL matching the remaining token lifetime, ensuring automatic cleanup of expired entries.
- **User Profile Management**: Self-service profile updates (fullName, phone), avatar presigned URL generation (MinIO/S3), password change with current password verification, and seller role upgrade from BUYER.
- **Address Management**: Full CRUD for shipping addresses with province/district references, default address logic (auto-assign, auto-promote), and Kafka request-reply integration for order-service address lookups.
- **Admin Controls**: Account locking/unlocking by admin users with reason tracking.
- **Internal APIs**: REST endpoints for inter-service communication (`/internal/*`) providing user role lookup, user info retrieval, and user existence checks.

The service works in concert with the API Gateway, which decodes JWT tokens and sets `X-User-Id` and `X-User-Role` HTTP headers. The `JwtTokenDecoderFilter` from common-lib reads these headers and populates the Spring SecurityContext, enabling stateless session management across all backend services.

## Software Features

- **Multi-credential Authentication**: Users can log in using username, email, or phone combined with password. The AuthService resolves the credential type automatically and authenticates against the users table.
- **JWT Token Generation and Refresh**: Access tokens (short-lived) and refresh tokens (long-lived) generated with RS256 encryption. Token refresh endpoint issues new token pairs without requiring re-authentication.
- **Domain-based Role Routing**: The login endpoint extracts the domain from the HTTP request to determine the intended role (e.g., `seller.marketplace.vn` routes to SELLER role, `admin.marketplace.vn` routes to ADMIN role), supporting multi-tenant subdomain routing.
- **Token Blacklisting on Logout**: When a user logs out, the JWT's JTI (JWT ID) is stored in Redis with a TTL equal to the remaining token lifetime. A Spring Security filter checks the blacklist on every authenticated request.
- **Role Management**: User roles (BUYER, SELLER, ADMIN) are stored in a dedicated `roles` table with a unique constraint on `user_id`, ensuring one role per user. Buyers can self-upgrade to SELLER.
- **Address Book**: Users can manage multiple shipping addresses. Each address references province/district IDs. The system enforces a default address policy: at least one default always exists, and deleting the default auto-promotes another.
- **Account Locking**: Admin users can lock and unlock user accounts with reason tracking. Locked accounts receive `isAccountNonLocked() = false` in Spring Security, preventing authentication.
- **Avatar Upload**: Generates presigned URLs for direct upload to MinIO/S3, with path pattern including seller ID and user ID for organization.
- **Dev Data Seeding**: Rich development dataset with 13 users (5 sellers, 6 buyers, 1 admin, 2 extra buyers), role assignments, and addresses, seeded via Flyway-compatible `ON CONFLICT DO NOTHING` SQL.
- **Kafka Request-Reply for Addresses**: Asynchronous address lookup for order-service via `order.address.request` (consume) and `order.address.response` (produce), using correlation IDs for request matching.

## Code Elements

This component contains the following code-level elements:

- [c4-code-backend-identity-service.md](./c4-code-backend-identity-service.md) -- Full code-level documentation for the Identity Service

### Key Classes

| Category | Classes |
|----------|---------|
| **Entry Point** | `IdentityServiceApplication` |
| **Controllers** | `AuthController`, `UserController`, `AdminController`, `InternalUserController` |
| **Services** | `AuthService`, `UserService`, `CustomUserDetailsService`, `TokenBlacklistService`, `AddressKafkaConsumer` |
| **Domain Models** | `User` (JPA entity), `Role` (JPA entity), `Address` (JPA entity) |
| **Repositories** | `UserRepository`, `RoleRepository`, `AddressRepository` |
| **Configuration** | `SecurityConfig`, `SecurityFilterConfig`, `KafkaConsumerConfig`, `IdentityDevDataLoader` |
| **DTOs (Request)** | `AddressCreateRequest`, `AddressUpdateRequest`, `ChangePasswordRequest`, `LockRequest`, `UnlockRequest`, `UserProfileUpdateRequest` |
| **DTOs (Response)** | `AddressResponse`, `AdminUserResponse`, `InternalUserInfoResponse`, `InternalUserRoleResponse`, `PresignedUrlResponse`, `UserExistsResponse`, `UserProfileResponse` |

## Interfaces

### REST API (External -- via API Gateway)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/v1/auth/login` | None | Authenticate by credential + password, returns JWT tokens |
| `POST` | `/v1/auth/register` | None | Register new BUYER user |
| `POST` | `/v1/auth/register/seller` | None | Register new SELLER user |
| `POST` | `/v1/auth/refresh` | None | Refresh access token using refresh token |
| `POST` | `/v1/auth/logout` | Authenticated | Logout (blacklist current access token) |
| `GET` | `/v1/users/me` | Authenticated | Get current user profile |
| `PUT` | `/v1/users/me` | Authenticated | Update profile (fullName, phone) |
| `GET` | `/v1/users/me/avatar/presigned-url` | Authenticated | Get presigned URL for avatar upload |
| `GET` | `/v1/users/me/addresses` | Authenticated | List user's addresses |
| `POST` | `/v1/users/me/addresses` | Authenticated | Add new address |
| `PUT` | `/v1/users/me/addresses/{addressId}` | Authenticated | Update address |
| `DELETE` | `/v1/users/me/addresses/{addressId}` | Authenticated | Delete address |
| `POST` | `/v1/users/me/change-password` | Authenticated | Change password (requires current password) |
| `POST` | `/v1/users/me/roles/seller` | Authenticated | Upgrade from BUYER to SELLER |
| `POST` | `/v1/admin/users/{userId}/lock` | ADMIN | Lock user account |
| `POST` | `/v1/admin/users/{userId}/unlock` | ADMIN | Unlock user account |

### Internal REST API (Inter-service)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/internal/users/{userId}/role` | Get user's role name and active status |
| `GET` | `/internal/users/{userId}` | Get full user info (id, username, email, phone, role, status) |
| `GET` | `/internal/users/exists?username=X` or `?email=X` or `?phone=X` | Check if a user exists by credential |

### Kafka Topics

**Consumed:**

| Topic | Purpose |
|-------|---------|
| `order.address.request` | Request-reply: receive address lookup requests from order-service |

**Produced:**

| Topic | Purpose |
|-------|---------|
| `order.address.response` | Request-reply: send address data to order-service |

## Dependencies

### Other Components

| Component | Interaction | Protocol |
|-----------|-------------|----------|
| **Order Service** | Provides address data via Kafka request-reply; provides user role/info via internal REST API | Kafka + REST |
| **Payment Service** | User existence/role lookup (indirect, via order-service) | REST (via order-service) |
| **Notification Service** | User info for notification delivery (indirect) | REST (via common-lib) |

### External Systems

| System | Purpose | Configuration |
|--------|---------|---------------|
| **PostgreSQL** (schema: `identity`) | Persistent storage for `users`, `roles`, `addresses` tables | Spring Data JPA + Flyway migrations |
| **Redis** | Token blacklist storage with TTL-based auto-expiry | `StringRedisTemplate`, key pattern: `token:blacklist:<jti>` |
| **Kafka** | Asynchronous address lookup with order-service via request-reply | Spring Kafka, idempotent producer, manual consumer commit |
| **MinIO / S3** | User avatar storage via presigned URL generation | MinIO Java SDK |
| **Eureka** | Service discovery registration | Spring Cloud Netflix Eureka Client |
| **API Gateway** | JWT decoding, `X-User-Id`/`X-User-Role` header injection | Stateless, header-based |

### Internal Libraries

| Library | Package | Usage |
|---------|---------|-------|
| `common-lib` | `com.flashsale.commonlib.dto` | `ApiResponse`, `AuthResponse`, `LoginRequest`, `RegisterRequest` |
| `common-lib` | `com.flashsale.commonlib.security` | `JwtUtils`, `UserDetailsImpl` |
| `common-lib` | `com.flashsale.commonlib.filter` | `JwtTokenDecoderFilter` |
| `common-lib` | `com.flashsale.commonlib.config` | `DevDataProperties` |
| `common-lib` | `com.flashsale.commonlib.event` | `KafkaTopics` |

## Component Diagram

```mermaid
C4Component
    title Component Diagram for Identity Service Container

    Container_Boundary(identity_service, "Identity Service") {
        Component(auth_controller, "Auth Controller", "Spring REST Controller", "Handles login, registration, token refresh, and logout. Determines role from request domain for multi-tenant routing.")
        Component(user_controller, "User Controller", "Spring REST Controller", "User profile CRUD, address management, password change, seller role upgrade, avatar presigned URLs.")
        Component(admin_controller, "Admin Controller", "Spring REST Controller", "Admin-only endpoints for account locking and unlocking with reason tracking.")
        Component(internal_controller, "Internal User Controller", "Spring REST Controller", "Inter-service endpoints for user role lookup, user info retrieval, and existence checks.")
        Component(auth_service, "Auth Service", "Spring Service", "Core authentication logic: credential lookup, password validation, JWT generation, domain-based role determination, token refresh.")
        Component(user_service, "User Service", "Spring Service", "Profile and address management: CRUD for user profile fields and addresses with default address logic.")
        Component(token_blacklist_service, "Token Blacklist Service", "Spring Service", "JWT token blacklisting via Redis with TTL matching remaining token lifetime. Supports check, add, and remove operations.")
        Component(address_kafka_consumer, "Address Kafka Consumer", "Kafka Listener", "Kafka request-reply consumer for address lookups from order-service. Replies with correlation ID matching.")
        Component(custom_user_details_service, "CustomUserDetailsService", "Spring Security Service", "Loads UserDetails by username or email, constructs UserDetailsImpl with role information for Spring Security.")
        Component(security_config, "Security Config", "Spring Security Configuration", "Stateless session management, CSRF disabled, JwtTokenDecoderFilter registration, BCrypt password encoder.")
        ComponentDb(user_repo, "User Repository", "JPA Repository", "User CRUD with findByUsername, findByEmail, findByPhone lookups.")
        ComponentDb(role_repo, "Role Repository", "JPA Repository", "Role lookups by userId.")
        ComponentDb(address_repo, "Address Repository", "JPA Repository", "Address CRUD with default management queries and bulk updates.")
        ComponentDb(user_entity, "User Entity", "JPA Entity", "users table: id, username, email, phone, password, fullName, status (ACTIVE/LOCKED), version (optimistic lock).")
        ComponentDb(role_entity, "Role Entity", "JPA Entity", "roles table: id, userId (unique), roleName (BUYER/SELLER/ADMIN).")
        ComponentDb(address_entity, "Address Entity", "JPA Entity", "addresses table: id, userId, provinceId, districtId, fullAddress, isDefault.")
    }

    Container_Ext(api_gateway, "API Gateway", "JWT decoding, header injection")
    Container_Ext(order_service, "Order Service", "Order management")
    Container_Ext(postgresql, "PostgreSQL", "identity schema")
    ContainerDb_Ext(redis, "Redis", "Token Blacklist")
    Container_Ext(kafka, "Apache Kafka", "Event Bus")
    Container_Ext(minio, "MinIO / S3", "Object Storage")
    Container_Ext(eureka, "Eureka", "Service Discovery")

    Rel(api_gateway, auth_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, user_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, admin_controller, "Routes HTTP requests", "REST")
    Rel(order_service, internal_controller, "User role/info queries", "REST /internal/*")
    Rel(auth_controller, auth_service, "Calls", "Java method")
    Rel(user_controller, user_service, "Calls", "Java method")
    Rel(auth_service, token_blacklist_service, "Delegates token blacklisting", "Java method")
    Rel(auth_service, user_repo, "Reads/writes", "JPA")
    Rel(auth_service, role_repo, "Reads/writes", "JPA")
    Rel(user_service, user_repo, "Reads/writes", "JPA")
    Rel(user_service, role_repo, "Reads/writes", "JPA")
    Rel(user_service, address_repo, "Reads/writes", "JPA")
    Rel(internal_controller, user_repo, "Reads", "JPA")
    Rel(internal_controller, role_repo, "Reads", "JPA")
    Rel(admin_controller, user_repo, "Reads/writes", "JPA")
    Rel(custom_user_details_service, user_repo, "Reads", "JPA")
    Rel(custom_user_details_service, role_repo, "Reads", "JPA")
    Rel(user_repo, user_entity, "Manages", "JPA")
    Rel(role_repo, role_entity, "Manages", "JPA")
    Rel(address_repo, address_entity, "Manages", "JPA")
    Rel(user_entity, postgresql, "Persisted to", "JDBC")
    Rel(role_entity, postgresql, "Persisted to", "JDBC")
    Rel(address_entity, postgresql, "Persisted to", "JDBC")
    Rel(token_blacklist_service, redis, "Blacklists tokens with TTL", "Redis SETEX")
    Rel(address_kafka_consumer, kafka, "Consumes order.address.request", "Kafka")
    Rel(address_kafka_consumer, kafka, "Produces order.address.response", "Kafka")
    Rel(kafka, order_service, "Delivers address responses", "Kafka")
    Rel(user_controller, minio, "Generates presigned PUT URLs for avatar uploads", "MinIO SDK")
    Rel(identity_service, eureka, "Registers with", "Eureka Client")
```
