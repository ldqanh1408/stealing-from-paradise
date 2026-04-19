# Multi-stage build for Admin App (Vite)
# Stage 1: Build
FROM node:20-alpine as builder

# Set up working directory structure matching source tree:
# /build/apps/admin (app work dir)
# /build/shared (shared lib)
WORKDIR /build

# Copy shared library first
COPY shared ./shared

# Copy and install dependencies
WORKDIR /build/apps/admin
COPY apps/admin/package.json apps/admin/package-lock.json ./

# Install dependencies
RUN npm ci --prefer-offline --no-audit

# Copy source code and config files
COPY apps/admin/src ./src
COPY apps/admin/vite.config.ts ./
COPY apps/admin/tsconfig.json ./
COPY apps/admin/tsconfig.app.json ./
COPY apps/admin/tsconfig.node.json ./
COPY apps/admin/eslint.config.js ./
COPY apps/admin/tailwind.config.js ./
COPY apps/admin/postcss.config.cjs ./
COPY apps/admin/index.html ./

# Build the app
RUN npm run build

# Stage 2: Production with Nginx
FROM nginx:alpine

# Copy custom nginx config
COPY apps/admin/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built files from builder
COPY --from=builder /build/apps/admin/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/health || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
