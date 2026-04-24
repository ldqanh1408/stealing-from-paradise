#!/bin/sh
# Entrypoint for nginx reverse-proxy — no envsubst, pure shell substitution
set -e

: ${NGINX_PORT:=80}
: ${GATEWAY_HOST:=fs-gateway}
: ${GATEWAY_PORT:=8080}
: ${CUSTOMER_HOST:=fs-customer-fe}
: ${CUSTOMER_PORT:=3000}

cat > /etc/nginx/conf.d/default.conf <<EOF
upstream gateway {
    server ${GATEWAY_HOST}:${GATEWAY_PORT};
}

upstream customer-app {
    server ${CUSTOMER_HOST}:${CUSTOMER_PORT};
}

server {
    listen ${NGINX_PORT};
    server_name _;

    real_ip_header X-Real-IP;
    real_ip_recursive on;

    location /api/ {
        proxy_pass http://gateway/api/v1/;
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

    location / {
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
