#!/bin/bash
set -e

echo "📦 Starting post-create setup..."

# Create .env if not exists
if [ ! -f ".env" ]; then
    echo "📝 Creating .env file..."
    if [ -f ".env.example" ]; then
        cp .env.example .env
    else
        cat > .env << 'EOF'
# Database
POSTGRES_VER=15.4-alpine
POSTGRES_USER=flashsale
POSTGRES_PASSWORD=devpassword123
POSTGRES_DB=flashsale
POSTGRES_PORT=5432

MONGO_VER=6.0.8
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=devpassword123
MONGO_PORT=27017

REDIS_VER=7.2.1-alpine
REDIS_PASSWORD=devpassword123
REDIS_PORT=6379

ELASTIC_VER=8.10.2
ELASTIC_PORT=9200

# Services
EUREKA_URI=http://localhost:8761/eureka/
KAFKA_SERVER=localhost:9092
AXON_SERVER=localhost:8124

# Frontend
VITE_API_URL=http://localhost:8080

# Stripe
STRIPE_PUBLIC_KEY=
STRIPE_SECRET_KEY=

# JWT
JWT_SECRET=your-secret-key-change-this-in-production
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
EOF
    fi
    echo "⚠️  Please update .env with your settings"
fi

# Build backend - MUST build common-lib FIRST
echo "📦 Building backend (this may take 3-5 minutes)..."
cd backend

# Step 1: Build common-lib FIRST (shared library)
echo "  ▶ Building common-lib (shared library)..."
cd common-lib
mvn clean install -DskipTests -q 2>&1 | grep -E "BUILD|ERROR" || echo "  ✓ common-lib installed"
cd ..

# Step 2: Build all other services
echo "  ▶ Building all services..."
mvn clean install -DskipTests -q 2>&1 | tail -n 1 || echo "  ✓ Services built successfully"

cd ..

# Install frontend
echo "📦 Installing frontend apps..."
for app in customer seller admin; do
    if [ -d "frontend/apps/$app" ]; then
        echo "  ▶ Installing $app..."
        cd frontend/apps/$app
        npm install --silent 2>&1 | tail -n 1 || true
        cd ../../..
        echo "  ✓ $app ready"
    fi
done

echo ""
echo "✅ Post-create setup complete!"
echo ""
echo "📋 Available services:"
echo "   Backend:  discovery-service (8761), api-gateway (8080), etc."
echo "   Frontend: customer (3000), seller (3001), admin (3002)"
echo ""
echo "🚀 Next steps:"
echo "   1. Update .env file if needed"
echo "   2. Run: docker-compose up -d          (start infrastructure)"
echo "   3. Run: cd backend/discovery-service && mvn spring-boot:run  # Terminal 1"
echo "   4. Run: cd backend/api-gateway && mvn spring-boot:run        # Terminal 2"
echo "   5. Run: cd frontend/apps/customer && npm run dev             # Terminal 3"
