# Start with Eclipse Temurin Java 25 (has Java 25 built-in)
FROM eclipse-temurin:25-jdk-jammy

# Install Node.js & npm
RUN apt-get update && apt-get install -y --no-install-recommends \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*

# Install Maven (latest)
RUN apt-get update && apt-get install -y --no-install-recommends \
    maven \
    && rm -rf /var/lib/apt/lists/*

# Install Docker CLI
RUN apt-get update && apt-get install -y --no-install-recommends \
    docker.io \
    docker-compose-v2 \
    && rm -rf /var/lib/apt/lists/*

# Install git, curl, wget, build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    wget \
    build-essential \
    bash \
    && rm -rf /var/lib/apt/lists/*

# Set Java environment (Eclipse Temurin uses /opt/java/openjdk)
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV MAVEN_OPTS="-Xmx2048m -XX:+UseG1GC"

# Verify installations
RUN java -version && \
    javac -version && \
    mvn --version && \
    node --version && \
    npm --version && \
    docker --version

WORKDIR /workspace

# Install global npm packages
RUN npm install -g \
    typescript \
    @types/node \
    @types/react \
    @types/react-dom \
    pnpm

# Create vscode user for Codespaces
RUN useradd -m -s /bin/bash -G docker vscode || true

USER vscode
