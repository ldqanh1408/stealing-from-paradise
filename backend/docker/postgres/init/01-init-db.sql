-- Tự động chạy bởi docker-entrypoint-initdb.d khi data dir trống
-- KHÔNG chạy lại nếu data dir đã có data

\c postgres;

CREATE DATABASE fs_identity_prod;
CREATE DATABASE fs_order_prod;
CREATE DATABASE fs_payment_prod;
CREATE DATABASE fs_promo_prod;
CREATE DATABASE fs_worker_prod;
CREATE DATABASE fs_flashsale_prod;

-- Grant quyền cho user
GRANT ALL PRIVILEGES ON DATABASE fs_identity_prod  TO postgres;
GRANT ALL PRIVILEGES ON DATABASE fs_order_prod     TO postgres;
GRANT ALL PRIVILEGES ON DATABASE fs_payment_prod   TO postgres;
GRANT ALL PRIVILEGES ON DATABASE fs_promo_prod     TO postgres;
GRANT ALL PRIVILEGES ON DATABASE fs_worker_prod    TO postgres;
GRANT ALL PRIVILEGES ON DATABASE fs_flashsale_prod TO postgres;
