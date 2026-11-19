FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S parking && adduser -S parking -G parking
COPY target/parking-backend-1.0.0.jar app.jar
USER parking
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=60s --retries=30 CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
