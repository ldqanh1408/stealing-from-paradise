FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy pre-built JAR from host
COPY target/search-service-*.jar app.jar

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]

