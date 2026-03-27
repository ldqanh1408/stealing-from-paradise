#!/bin/sh
set -e

# Create databases for each service if they do not already exist.
# Uses the POSTGRES_USER configured for the container as owner.
create_db() {
  db_name="$1"
  echo "Ensuring database '$db_name' exists"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'create database ' || quote_ident('$db_name')
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db_name')\gexec
    ALTER DATABASE "$db_name" OWNER TO "$POSTGRES_USER";
EOSQL
}

create_db "${DB_NAME_IDENTITY:-fs_identity_prod}"
create_db "${DB_NAME_ORDER:-fs_order_prod}"
create_db "${DB_NAME_PAYMENT:-fs_payment_prod}"
create_db "${DB_NAME_PROMOTION:-fs_promo_prod}"
create_db "${DB_NAME_WORKER:-fs_worker_prod}"
create_db "${DB_NAME_PRODUCT:-fs_product_prod}"
create_db "${DB_NAME_NOTIFICATION:-fs_noti_prod}"


echo "Database initialization completed successfully"

