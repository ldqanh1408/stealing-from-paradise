#!/bin/sh
# Entrypoint for nginx reverse-proxy — no envsubst, pure shell substitution
set -e

: ${NGINX_PORT:=80}
: ${GATEWAY_HOST:=fs-gateway}
: ${GATEWAY_PORT:=8080}
: ${CUSTOMER_HOST:=fs-customer-fe}
: ${CUSTOMER_PORT:=3000}
: ${SELLER_HOST:=fs-seller-fe}
: ${SELLER_PORT:=3001}
: ${ADMIN_HOST:=fs-admin-fe}
: ${ADMIN_PORT:=3002}

cat > /etc/nginx/conf.d/default.conf <<EOF
# Rate limiting zones — \$binary_remote_addr is a literal nginx variable
# (plain heredoc + \$ means the shell outputs a single $ character).
limit_req_zone \$binary_remote_addr zone=api_limit:10m rate=10r/s;
limit_req_zone \$binary_remote_addr zone=frontend_limit:10m rate=20r/s;

upstream gateway {
    server ${GATEWAY_HOST}:${GATEWAY_PORT};
}

upstream customer-app {
    server ${CUSTOMER_HOST}:${CUSTOMER_PORT};
}

upstream seller-app {
    server ${SELLER_HOST}:${SELLER_PORT};
}

upstream admin-app {
    server ${ADMIN_HOST}:${ADMIN_PORT};
}

server {
    listen ${NGINX_PORT};
    server_name _;

    real_ip_header X-Real-IP;
    real_ip_recursive on;

    location /api/ {
        # 10 req/s per IP, allow short bursts up to 20
        limit_req zone=api_limit burst=20 nodelay;

        # Forward request URI as-is (no path rewriting). Frontend already
        # sends /api/v1/... and gateway routes match /api/v1/**, so the
        # path must reach gateway unchanged. Adding a trailing path here
        # like 'http://gateway/api/v1/' would cause nginx to REPLACE the
        # matched location prefix '/api/' with '/api/v1/', producing
        # '/api/v1/v1/...' on the wire — which doesn't match any gateway
        # route and returns 404.
        proxy_pass http://gateway;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_pass_request_body on;
        proxy_redirect off;
        proxy_buffering off;
        proxy_connect_timeout 10s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    location /seller/ {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://seller-app/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_redirect off;
    }

    location /admin/ {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://admin-app/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_redirect off;
    }

    location / {
        limit_req zone=frontend_limit burst=40 nodelay;

        proxy_pass http://customer-app/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_redirect off;
    }

    location /health {
        access_log off;
        default_type text/plain;
        return 200 "healthy\n";
    }

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;

    error_page 500 502 503 504 @error;
    location @error {
        default_type text/plain;
        return 503 "Service temporarily unavailable";
    }
}
EOF

exec nginx -g 'daemon off;'
