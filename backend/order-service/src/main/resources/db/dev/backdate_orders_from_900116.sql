-- DEV ONLY: backdate completed runtime orders from id 900116 onward by 10 days.
--
-- Why it is scoped this way:
-- - Checkout discovers the newly-created parentOrderId from the most recent order list.
-- - Backdating PENDING orders makes new runtime orders look older than seeded/cancelled orders,
--   so the frontend can keep using a stale parentOrderId such as 900115.
-- - The order lifecycle scheduler also auto-cancels old PENDING orders.
--
-- Run manually against flashsale_platform, for example:
-- docker exec -i fs-postgres psql -U postgres -d flashsale_platform < backend/order-service/src/main/resources/db/dev/backdate_orders_from_900116.sql

DROP TRIGGER IF EXISTS trg_dev_backdate_parent_orders ON orders.parent_orders;
DROP TRIGGER IF EXISTS trg_dev_backdate_orders ON orders.orders;
DROP TRIGGER IF EXISTS trg_dev_backdate_completed_orders ON orders.orders;

DROP FUNCTION IF EXISTS orders.dev_backdate_parent_order_created_at();
DROP FUNCTION IF EXISTS orders.dev_backdate_order_created_at_and_code();
DROP FUNCTION IF EXISTS orders.dev_backdate_completed_order_created_at_and_code();

CREATE OR REPLACE FUNCTION orders.dev_backdate_completed_order_created_at_and_code()
RETURNS trigger AS $$
DECLARE
  target_created_at timestamp := now() - interval '10 days';
  target_order_code text := 'OR-' || to_char(now() - interval '10 days', 'YYYYMMDD') || '-' || NEW.id;
BEGIN
  IF NEW.id >= 900116 AND NEW.status IN ('PAID', 'SHIPPING', 'DELIVERED') THEN
    UPDATE orders.parent_orders
    SET created_at = target_created_at
    WHERE id = NEW.parent_order_id;

    UPDATE orders.orders
    SET
      created_at = target_created_at,
      order_code = target_order_code
    WHERE id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dev_backdate_completed_orders
AFTER INSERT OR UPDATE OF status
ON orders.orders
FOR EACH ROW
EXECUTE FUNCTION orders.dev_backdate_completed_order_created_at_and_code();

-- Repair runtime PENDING orders that may already have been backdated by the unsafe trigger.
-- They must stay "today" while payment is still pending so checkout can find them and
-- JOB-13 does not auto-cancel them as stale.
WITH pending_runtime_orders AS (
  SELECT id, parent_order_id, now() AS repaired_created_at
  FROM orders.orders
  WHERE id >= 900116
    AND status = 'PENDING'
    AND created_at < now() - interval '30 minutes'
)
UPDATE orders.parent_orders po
SET
  created_at = pro.repaired_created_at,
  updated_at = now()
FROM pending_runtime_orders pro
WHERE po.id = pro.parent_order_id;

WITH pending_runtime_orders AS (
  SELECT id, now() AS repaired_created_at
  FROM orders.orders
  WHERE id >= 900116
    AND status = 'PENDING'
    AND created_at < now() - interval '30 minutes'
)
UPDATE orders.orders o
SET
  created_at = pro.repaired_created_at,
  updated_at = now(),
  order_code = 'OR-' || to_char(pro.repaired_created_at, 'YYYYMMDD') || '-' || o.id
FROM pending_runtime_orders pro
WHERE o.id = pro.id;

-- Keep already-completed runtime orders in the target "10 days ago" state for testing.
WITH completed_runtime_orders AS (
  SELECT id, parent_order_id, now() - interval '10 days' AS target_created_at
  FROM orders.orders
  WHERE id >= 900116
    AND status IN ('PAID', 'SHIPPING', 'DELIVERED')
)
UPDATE orders.parent_orders po
SET created_at = cro.target_created_at
FROM completed_runtime_orders cro
WHERE po.id = cro.parent_order_id;

WITH completed_runtime_orders AS (
  SELECT id, now() - interval '10 days' AS target_created_at
  FROM orders.orders
  WHERE id >= 900116
    AND status IN ('PAID', 'SHIPPING', 'DELIVERED')
)
UPDATE orders.orders o
SET
  created_at = cro.target_created_at,
  order_code = 'OR-' || to_char(cro.target_created_at, 'YYYYMMDD') || '-' || o.id
FROM completed_runtime_orders cro
WHERE o.id = cro.id;

-- Cleanup when done testing:
-- DROP TRIGGER IF EXISTS trg_dev_backdate_completed_orders ON orders.orders;
-- DROP FUNCTION IF EXISTS orders.dev_backdate_completed_order_created_at_and_code();
