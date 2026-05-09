# ENTITY-PAYMENT-003: Seller Transfer

**Domain**: Payment Service  
**Table**: `SELLER_TRANSFERS`  
**Purpose**: Tracks per-seller payout records for each sub-order. Implements the delayed payout flow: platform holds funds until delivery confirmation + return window expiry, then transfers net amount to seller's Stripe connected account.  
**References**: [database-entities.md](../../../docs/database/database-entities.md#7-payments--transfers), [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## ERD (Entity Context)

```
ORDERS (Order Svc)        SELLER_TRANSFERS                   TRANSACTIONS
+--------------+          +--------------------------------+  +--------------+
| id  BIGSERIAL|<---------| order_id           BIGINT FK   |  | id  BIGSERIAL|
| seller_id    |          | seller_id          BIGINT FK   |->|              |
| final_amt    |          | transaction_id     BIGINT FK   |  +--------------+
| net_payout   |          | transfer_amount    DECIMAL     |
+--------------+          | refunded_amount    DECIMAL     |
                          | stripe_transfer_id VARCHAR     |
                          | delivered_at       TIMESTAMP   |
                          | net_payout_amount  DECIMAL     |
                          | payout_eligible_at TIMESTAMP   |
                          | platform_commission_amt DECIMAL|
                          | payout_at          TIMESTAMP   |
                          | payout_retry_count INTEGER     |
                          | status             VARCHAR     |
                          | created_at / updated_at        |
                          +--------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `order_id` | BIGINT | FK -> ORDERS.id, UNIQUE | Sub-order reference; one transfer per sub-order |
| 3 | `seller_id` | BIGINT | FK -> SELLERS.id | Seller receiving the transfer |
| 4 | `transaction_id` | BIGINT | FK -> TRANSACTIONS.id | Links directly to parent transaction |
| 5 | `transfer_amount` | DECIMAL | -- | Gross transfer amount before platform commission |
| 6 | `refunded_amount` | DECIMAL | -- | Amount refunded from this transfer |
| 7 | `stripe_transfer_id` | VARCHAR | -- | Stripe Transfer ID (`tr_xxx`) used for reversals |
| 8 | `delivered_at` | TIMESTAMP | -- | When order was confirmed delivered |
| 9 | `net_payout_amount` | DECIMAL | -- | transfer_amount - platform_commission_amt |
| 10 | `payout_eligible_at` | TIMESTAMP | -- | delivered_at + 7 days (return window end) |
| 11 | `platform_commission_amt` | DECIMAL | -- | Platform fee deducted (5% of transfer_amount) |
| 12 | `payout_at` | TIMESTAMP | -- | When Stripe Transfer was executed |
| 13 | `payout_retry_count` | INTEGER | DEFAULT 0 | Number of payout retry attempts |
| 14 | `status` | VARCHAR | -- | Transfer lifecycle state |
| 15 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 16 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_seller_transfers` | `id` | PRIMARY KEY | Unique row identifier |
| `uq_seller_transfers_order` | `order_id` | UNIQUE | Prevents duplicate transfers per sub-order |
| `idx_st_payout_eligible` | `status, payout_eligible_at` | BTREE | Cron job queries for ready-to-payout records |

---

## Status Flow

```
PENDING
  |  (payment.success received)
  v
AWAITING_DELIVERY
  |  (order.delivered event)
  v
RETURN_WINDOW
  |  (cron claims after 7 days: NOW() >= payout_eligible_at)
  v
READY_FOR_PAYOUT
  |  (Stripe Transfer API call)
  v
PAID_OUT
```

| Status | Description | Trigger |
|--------|-------------|---------|
| `PENDING` | Transfer record created, awaiting payment | `payment.requested` handler |
| `AWAITING_DELIVERY` | Payment succeeded, waiting for delivery | `payment_intent.succeeded` webhook |
| `RETURN_WINDOW` | Order delivered, 7-day return window active | `order.delivered` Kafka event |
| `READY_FOR_PAYOUT` | Return window expired, eligible for payout | Cron job (ShedLock) |
| `PAID_OUT` | Stripe Transfer completed to seller | Stripe `transfer.created` webhook |
| `FAILED` | Payout failed after max retries | Payout error |
| `SKIPPED` | Seller charges not enabled | `charges_enabled = false` check |
| `REFUNDED` | Refunded before payout (no reversal needed) | Refund during RETURN_WINDOW |
| `REVERSED` | Refunded after payout (Stripe reversal) | Refund after PAID_OUT |
| `PARTIALLY_REVERSED` | Partial refund after payout | Partial refund after PAID_OUT |

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-009 | `platform_commission_amt` = `transfer_amount` * 5% (PLATFORM_FEE) |
| BR-PAYMENT-010 | `net_payout_amount` = `transfer_amount` - `platform_commission_amt` |
| BR-PAYMENT-011 | `payout_eligible_at` = `delivered_at` + 7 calendar days |
| BR-PAYMENT-012 | UNIQUE(order_id) prevents double payout per sub-order |
| BR-PAYMENT-013 | Refund before PAID_OUT -> status = REFUNDED (no Stripe reversal) |
| BR-PAYMENT-014 | Refund after PAID_OUT -> status = REVERSED (Stripe Transfer reversal) |
| BR-PAYMENT-015 | If seller `charges_enabled = false`, status = SKIPPED |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| ORDERS | 1:1 | `order_id` FK |
| SELLERS | N:1 | `seller_id` FK |
| TRANSACTIONS | N:1 | `transaction_id` FK |
