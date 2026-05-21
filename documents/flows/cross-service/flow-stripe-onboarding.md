# Business Flow: Stripe Connect Onboarding
Scope: Cross-Service (payment-service · identity-service)

### Description
Documents the end-to-end integration flow of a Seller onboarding onto Stripe Express Connect, completing KYC verification, and the asynchronous webhook lifecycle that activates the seller account.

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Seller as Seller (Browser)
    participant GW as API Gateway
    participant PayS as Payment Service
    participant Stripe as Stripe API
    participant Kafka as Kafka Broker
    participant IdentS as Identity Service

    Seller->>GW: POST /api/v1/stripe/onboarding/start (payout_currency)
    GW->>PayS: Route request
    PayS->>PayS: Check existing SELLER_STRIPE_ACCOUNTS
    alt No account exists
        PayS->>Stripe: Create Custom/Express Account (Stripe.Account.create)
        Stripe-->>PayS: Account Object (acct_xxxx)
        PayS->>PayS: Persist acct_xxxx (charges_enabled=false, status=PENDING)
    end
    PayS->>Stripe: Generate onboarding link (Stripe.AccountLink.create)
    Stripe-->>PayS: URL (https://connect.stripe.com/setup/s/xxx)
    PayS-->>Seller: 200 OK (redirect_url)
    
    Seller->>Stripe: Redirect & Complete KYC Verification
    
    Note over Stripe, PayS: Asynchronous Webhook Callback
    
    Stripe->>GW: POST /stripe/webhooks (event: account.updated)
    GW->>PayS: Route raw webhook payload
    PayS->>PayS: Verify Stripe Webhook Signature
    PayS->>PayS: Parse charges_enabled, payouts_enabled, details_submitted
    alt KYC Completed successfully
        PayS->>PayS: UPDATE SELLER_STRIPE_ACCOUNTS (status=ACTIVE, charges_enabled=true)
        PayS->>Kafka: Publish event: seller.stripe_active
        Kafka->>IdentS: Consume event
        IdentS->>IdentS: UPDATE SELLERS.payout_status = ACTIVE
    else KYC Rejected / Pending
        PayS->>PayS: UPDATE SELLER_STRIPE_ACCOUNTS (status=SUSPENDED/PENDING)
    end
    PayS-->>Stripe: 200 OK Response
```

### Participant Directory

| Participant | Service Name | Role & Responsibility |
|-------------|--------------|-----------------------|
| **Seller** | Frontend client | Initiates request, inputs KYC details on Stripe's hosted Express form. |
| **API Gateway** | `api-gateway` | Handles routing, rate limiting, and forwards Stripe webhooks to Payment Service. |
| **Payment Service** | `payment-service` | Generates Stripe link, maps Stripe Account records, listens to webhooks, publishes Kafka triggers. |
| **Stripe API** | External Service | Hosts KYC screens, manages merchant onboarding, fires callbacks. |
| **Identity Service** | `identity-service` | Updates internal seller registration status and permissions upon successful Stripe link. |

### Message & Event Catalog

| Step | Source | Target | Trigger/Payload | Channel | Reference |
|------|--------|--------|-----------------|---------|-----------|
| 1    | Seller | `payment-service` | `POST /stripe/onboarding/start` | HTTP | API-POST-/stripe/onboarding/start |
| 2    | `payment-service` | Stripe | Create Account & Link | HTTPS API | External Stripe API |
| 3    | Stripe | `payment-service` | Webhook: `account.updated` | HTTP Webhook | Webhook Endpoint |
| 4    | `payment-service` | Kafka | Event: `seller.stripe_active` | Kafka (topic: seller.stripe) | EV-seller.stripe_active |
| 5    | Kafka | `identity-service` | Event: `seller.stripe_active` | Kafka (topic: seller.stripe) | EV-seller.stripe_active |
