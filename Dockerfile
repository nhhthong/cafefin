# Multi-stage build (Task 0.9): produces one runtime image that serves both
# the React SPA and the REST API from the same origin (localhost:8080),
# matching CLAUDE.md's "same-origin, not SSR" architecture.

# --- Stage 1: frontend build ---------------------------------------------
# Builds the SPA in isolation. Its only output we need is dist/.
FROM node:24-alpine AS frontend
WORKDIR /frontend
# Copy package files first so `npm ci` is cached by Docker as long as
# dependencies haven't changed, even if application source has.
COPY frontend/cafefin-web/package.json frontend/cafefin-web/package-lock.json ./
RUN npm ci
COPY frontend/cafefin-web/ ./
RUN npm run build

# --- Stage 2: backend build -----------------------------------------------
# Builds the Spring Boot jar. The frontend's dist/ is copied into
# cafefin-api's static resources BEFORE `mvn package`, so it ends up
# bundled inside the jar on the classpath — Spring MVC serves anything
# under classpath:/static/** automatically, no extra code needed.
FROM maven:3.9.16-eclipse-temurin-25 AS backend
WORKDIR /build
COPY pom.xml ./
COPY cafefin-common/pom.xml cafefin-common/pom.xml
COPY cafefin-api/pom.xml cafefin-api/pom.xml
COPY cafefin-napas-mock/pom.xml cafefin-napas-mock/pom.xml
COPY cafefin-notification/pom.xml cafefin-notification/pom.xml
# Resolve dependencies as their own cached layer, before copying sources.
RUN mvn -B dependency:go-offline -q || true
COPY cafefin-common cafefin-common
COPY cafefin-api cafefin-api
COPY cafefin-napas-mock cafefin-napas-mock
COPY cafefin-notification cafefin-notification
COPY --from=frontend /frontend/dist cafefin-api/src/main/resources/static
# Tests need a running Docker daemon for Testcontainers (Task 0.6), which
# isn't available inside a Docker build layer — skip them here, the test
# suite runs separately in CI/local dev.
RUN mvn -B -pl cafefin-api -am package -DskipTests

# --- Stage 3: runtime -------------------------------------------------
# JRE only (no JDK/Maven) for the smallest image that can actually run the
# already-built jar.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=backend /build/cafefin-api/target/cafefin-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
