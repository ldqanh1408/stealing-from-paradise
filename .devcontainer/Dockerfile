FROM mcr.microsoft.com/devcontainers/base:alpine

# Install Java 25 JDK
RUN apk add --no-cache \
    openjdk25 \
    openjdk25-jdk

# Install Maven
RUN apk add --no-cache maven

# Install Node.js & npm
RUN apk add --no-cache nodejs npm

# Install Docker CLI
RUN apk add --no-cache docker docker-compose

# Install git, curl, wget, build tools
RUN apk add --no-cache \
    git \
    curl \
    wget \
    build-base \
    bash

# Set Java environment
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV MAVEN_HOME=/usr/share/java/maven-3
ENV PATH=$MAVEN_HOME/bin:$PATH
ENV MAVEN_OPTS="-Xmx2048m -XX:+UseG1GC"

# Create non-root user
RUN useradd -m -s /bin/bash -G docker vscode 2>/dev/null || true

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

USER vscode
