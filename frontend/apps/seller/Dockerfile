# Multi-stage build for Seller App (Vite)
# Stage 1: Build
FROM node:20-alpine as builder

# Set up working directory structure matching source tree:
# /build/apps/seller (app work dir)
# /build/shared (shared lib)
WORKDIR /build

# Copy shared library first
COPY shared ./shared

# Copy and install dependencies
WORKDIR /build/apps/seller
COPY apps/seller/package.json apps/seller/package-lock.json ./

# Install dependencies
RUN npm ci --prefer-offline --no-audit

# Copy source code and config files
COPY apps/seller/src ./src
COPY apps/seller/vite.config.ts ./
COPY apps/seller/tsconfig.json ./
COPY apps/seller/tsconfig.app.json ./
COPY apps/seller/tsconfig.node.json ./
COPY apps/seller/eslint.config.js ./
COPY apps/seller/tailwind.config.js ./
COPY apps/seller/postcss.config.cjs ./
COPY apps/seller/index.html ./

# Build the app
RUN npm run build

# Stage 2: Production with Nginx
FROM nginx:alpine

# Copy custom nginx config
COPY apps/seller/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built files from builder
COPY --from=builder /build/apps/seller/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/health || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
