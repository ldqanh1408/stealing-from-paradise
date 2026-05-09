# Stealing-from-Paradise — Micro-Documentation
**Source of Truth:** database-entities.md (unchanged)
**Generated:** 2026-05-09
**Format:** Micro-documentation — one file per concept

## Directory Map

```
documents/
├── README.md                          ← You are here
├── overview/
│   └── ARCHITECTURE.md                System architecture, services, tech stack
├── messaging/
│   └── KAFKA_CATALOG.md               47 Kafka topics, event flows, request-reply
├── operations/
│   └── CRONJOBS.md                    17 cronjobs, retention policies
├── data-models/
│   ├── identity-service/              entity-user, entity-role, entity-customer, entity-seller, entity-admin, entity-address
│   ├── product-service/               entity-category, entity-product, entity-product-variant, entity-product-image, entity-stock-reservation, entity-cart, entity-cart-item
│   ├── flashsale-service/             entity-fs-session, entity-fs-item, entity-fs-reminder
│   ├── order-service/                 entity-parent-order, entity-order, entity-order-item
│   ├── payment-service/               entity-seller-stripe-account, entity-transaction, entity-seller-transfer, entity-refund, entity-refund-item
│   ├── notification-service/          entity-notification
│   ├── search-service/                entity-search-document
│   └── ai-chat-service/               entity-chat-session, entity-chat-message
├── business-rules/
│   ├── identity-service/              br-auth.md
│   ├── product-service/               br-catalog.md, br-cart.md
│   ├── flashsale-service/             br-flash-sale.md
│   ├── order-service/                 br-checkout.md, br-order-lifecycle.md
│   ├── payment-service/               br-stripe-onboarding.md, br-payment.md, br-refund.md
│   ├── notification-service/          br-notification.md
│   ├── search-service/                br-search.md
│   └── ai-chat-service/               br-ai-chat.md
├── srs/fr/
│   ├── identity-service/              fr-auth.md
│   ├── product-service/               fr-catalog.md, fr-cart.md
│   ├── flashsale-service/             fr-flash-sale.md
│   ├── order-service/                 fr-order.md
│   ├── payment-service/               fr-payment.md
│   ├── notification-service/          fr-notification.md
│   ├── search-service/                fr-search.md
│   └── ai-chat-service/               fr-ai-chat.md
├── use-cases/
│   ├── identity-service/              uc-001 through uc-006
│   ├── product-service/               uc-001 through uc-011
│   ├── flashsale-service/             uc-001 through uc-006
│   ├── order-service/                 uc-001 through uc-007
│   ├── payment-service/               uc-001 through uc-008
│   ├── notification-service/          uc-001 through uc-003
│   ├── search-service/                uc-001 through uc-003
│   └── ai-chat-service/               uc-001 through uc-003
├── api-contracts/
│   ├── identity-service/              api-post-auth-register.yaml, api-post-auth-login.yaml, api-get-addresses.yaml
│   ├── product-service/               api-get-products.yaml, api-post-products.yaml, api-get-cart.yaml, api-post-cart-items.yaml, api-post-variants.yaml
│   ├── flashsale-service/             api-post-flash-sales.yaml, api-get-flash-sales.yaml, api-post-flash-sales-buy.yaml
│   ├── order-service/                 api-post-orders-checkout.yaml, api-get-orders.yaml, api-put-orders-ship.yaml, api-post-orders-return.yaml
│   ├── payment-service/               api-post-stripe-onboarding-start.yaml, api-post-stripe-webhook.yaml, api-post-refunds.yaml, api-put-refunds-approve.yaml
│   ├── notification-service/          api-get-notifications.yaml, api-put-notifications-read.yaml
│   ├── search-service/                api-get-search.yaml
│   └── ai-chat-service/               api-post-chat-messages.yaml
├── state-diagrams/
│   ├── identity-service/              state-user.md
│   ├── product-service/               state-product.md, state-stock-reservation.md, state-cart.md
│   ├── flashsale-service/             state-fs-session.md
│   ├── order-service/                 state-order.md
│   ├── payment-service/               state-transaction.md, state-refund.md, state-stripe-account.md
│   ├── notification-service/          state-notification.md
│   ├── search-service/                state-search-index.md
│   └── ai-chat-service/               state-chat-session.md
└── traceability/
    ├── identity-service/              traceability-matrix.md
    ├── product-service/               traceability-matrix.md
    ├── flashsale-service/             traceability-matrix.md
    ├── order-service/                 traceability-matrix.md
    ├── payment-service/               traceability-matrix.md
    ├── notification-service/          traceability-matrix.md
    ├── search-service/                traceability-matrix.md
    └── ai-chat-service/               traceability-matrix.md
```

## ID Conventions

| Prefix | Scope | Example |
|--------|-------|---------|
| SVC- | Service | SVC-003 identity-service |
| ENTITY- | Data entity | ENTITY-IDENTITY-001 user |
| BR- | Business rule | BR-IDENTITY-001 unique username |
| FR- | Functional requirement | FR-IDENTITY-001 register account |
| UC- | Use case | UC-IDENTITY-001 register |
| API- | API endpoint | API-POST-/auth/register |

## Table Groups → Service Mapping (from database-entities.md)

| Table Group | Service | Tables |
|-------------|---------|--------|
| identity | identity-service | users, roles, customers, sellers, admins, addresses |
| catalog | product-service | category, product, product_variant, product_image, stock_reservation |
| cart | product-service | cart, cart_item |
| flash_sale | flashsale-service | fs_sessions, fs_items, fs_reminders |
| orders | order-service | parent_orders, orders, order_items |
| payments | payment-service | seller_stripe_accounts, transactions, seller_transfers, refunds, refund_items |
| notifications | notification-service | mg_notifications (MongoDB) |
| search | search-service | Elasticsearch index: skus |
| ai_chat | ai-chat-service | chat_sessions, chat_messages, pending_confirmations, tool_call_logs |

## Quick Path by Role

| Role | Start With | Then Read |
|------|-----------|-----------|
| New Developer | overview/ARCHITECTURE.md | data-models/{service}/ |
| Backend Developer | overview/ARCHITECTURE.md | api-contracts/{service}/ |
| QA/Tester | use-cases/{service}/ | business-rules/{service}/ |
| Architect | overview/ARCHITECTURE.md | traceability/{service}/ |
| DevOps | operations/CRONJOBS.md | messaging/KAFKA_CATALOG.md |
