# Stage 1: Extract Spring Boot layers for optimal caching
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /build
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# Stage 2: Final runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy Spring Boot layers in dependency order (most stable first)
COPY --from=extractor /build/extracted/dependencies/ ./
COPY --from=extractor /build/extracted/spring-boot-loader/ ./
COPY --from=extractor /build/extracted/snapshot-dependencies/ ./
COPY --from=extractor /build/extracted/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
