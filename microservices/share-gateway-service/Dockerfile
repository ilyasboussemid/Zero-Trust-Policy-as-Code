FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY share-gateway-service/target/share-gateway-service-1.0.0.jar /app/app.jar
RUN chown -R appuser:appgroup /app

USER appuser
EXPOSE 8441

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
