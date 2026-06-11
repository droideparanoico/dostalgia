# DOStalgia — Quarkus multi-stage build

# ─── 1. Build frontend ───────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci 2>/dev/null || npm install --legacy-peer-deps
COPY frontend/ ./
RUN npm run build
RUN npm test

# ─── 2. Build Quarkus app ────────────────────────
FROM maven:3-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Copy the built frontend into the source tree so maven-resources-plugin picks it up
COPY --from=frontend /build/dist ./frontend/dist/
RUN mvn package -q -DskipFrontend=true -DskipFrontendTests=true

# ─── 3. Runtime ──────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Quarkus uber-jar layout
COPY --from=build /build/target/quarkus-app/ /app/
EXPOSE 8765
VOLUME /data
ENV DOSTALGIA_DATA_DIR=/data
ENTRYPOINT ["java", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=20", "-jar", "quarkus-run.jar"]
