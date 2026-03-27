FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy pre-built JAR from host
COPY target/api-gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

