FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy pre-built JAR from host
COPY target/discovery-service-*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
