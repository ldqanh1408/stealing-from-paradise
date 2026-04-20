#!/bin/bash
set -e

echo "🔧 Running on-create setup..."

# Verify installations
echo "✅ Java version:"
java -version 2>&1 | head -n 1

echo "✅ Maven version:"
mvn --version 2>&1 | head -n 1

echo "✅ Node version:"
node --version

echo "✅ npm version:"
npm --version

echo "✅ Docker version:"
docker --version

echo "✅ Docker Compose version:"
docker-compose version 2>&1 | head -n 1

echo ""
echo "✅ On-create setup complete!"
