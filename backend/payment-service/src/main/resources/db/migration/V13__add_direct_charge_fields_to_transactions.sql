ALTER TABLE payment.transactions
    ADD COLUMN order_id BIGINT,
    ADD COLUMN seller_id BIGINT,
    ADD COLUMN stripe_account_id VARCHAR(100),
    ADD COLUMN stripe_payment_intent_id VARCHAR(100),
    ADD COLUMN stripe_charge_id VARCHAR(100),
    ADD COLUMN gross_amount DECIMAL(15,2),
    ADD COLUMN seller_net_amount DECIMAL(15,2),
    ADD COLUMN currency VARCHAR(10) DEFAULT 'vnd';

ALTER TABLE payment.seller_transfers
    ADD COLUMN stripe_payout_id VARCHAR(100),
    ADD COLUMN payout_status VARCHAR(50);
