#!/bin/bash
set -e

echo "🔄 Starting post-start routine..."

# Check .env exists
if [ ! -f ".env" ]; then
    echo "⚠️  Creating .env from example..."
    if [ -f ".env.example" ]; then
        cp .env.example .env
    fi
fi

# Check Docker daemon
if command -v docker &> /dev/null; then
    echo "📦 Checking Docker availability..."
    if docker info >/dev/null 2>&1; then
        echo "✓ Docker is running"

        # Start containers if not running
        CONTAINERS=$(docker ps -q 2>/dev/null | wc -l)
        if [ "$CONTAINERS" -eq 0 ]; then
            echo "📦 Starting infrastructure (docker-compose up -d)..."
            docker-compose up -d 2>&1 | tail -n 3 || echo "✓ Docker containers starting..."

            echo "⏳ Waiting for services..."
            sleep 5
            echo "✓ Services should be ready"
        else
            echo "✓ Docker containers already running"
        fi
    else
        echo "⚠️  Docker daemon not accessible"
    fi
fi

# Warm up Maven cache
if [ -f "backend/pom.xml" ]; then
    echo "♻️  Preparing Maven cache..."
    cd backend
    mvn dependency:resolve -q -T 1C 2>/dev/null || true
    cd ..
fi

# Check frontend modules
for app in customer seller admin; do
    if [ -d "frontend/apps/$app" ]; then
        if [ ! -d "frontend/apps/$app/node_modules" ]; then
            echo "📦 Installing $app dependencies..."
            cd frontend/apps/$app
            npm install --silent 2>&1 | tail -n 1 || true
            cd ../../..
        fi
    fi
done

echo ""
echo "✅ Post-start complete!"
echo ""
echo "🎯 Commands:"
echo "   Backend:  cd backend/<service> && mvn spring-boot:run"
echo "   Frontend: cd frontend/apps/<app> && npm run dev"
echo "   Logs:     docker-compose logs -f"
