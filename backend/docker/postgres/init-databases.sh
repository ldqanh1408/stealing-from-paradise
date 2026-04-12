#!/bin/bash

# PostgreSQL init script — chạy trong Docker postgres container

echo "Initializing PostgreSQL databases..."

# Identity Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'flashsale_identity'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE flashsale_identity OWNER postgres"

# Order Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'flashsale_order'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE flashsale_order OWNER postgres"

# Payment Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'flashsale_payment'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE flashsale_payment OWNER postgres"

# Flash Sale Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'flashsale_flashsale'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE flashsale_flashsale OWNER postgres"

# Worker Service Database
psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'flashsale_worker'" | grep -q 1 || psql -U postgres -c "CREATE DATABASE flashsale_worker OWNER postgres"

echo "PostgreSQL initialization completed!"

