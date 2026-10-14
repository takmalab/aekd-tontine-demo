# AEKD-Tontine : un seul conteneur, le backend Spring Boot sert aussi le frontend
# Angular compilé (même adresse pour l'interface et /api, pas de CORS).
# Déploiement : Railway (voir docs/deploiement.md).

# ---- 1. Frontend Angular -------------------------------------------------------
FROM node:24-bookworm-slim AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# ---- 2. Backend Spring Boot ----------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# Les tests demandent PostgreSQL : ils sont lancés en développement, pas ici.
RUN mvn -B -q package -DskipTests && cp target/tontine-backend-*.jar /build/app.jar

# ---- 3. Image d'exécution ------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Les volumes Railway (justificatifs, /app/storage) appartiennent à root : l'application
# tourne donc en root dans le conteneur, isolée par la plateforme.
RUN mkdir -p /app/storage
COPY --from=backend /build/app.jar /app/app.jar
COPY --from=frontend /build/dist/tontine-frontend/browser /app/frontend

ENV APP_FRONTEND_DIR=/app/frontend \
    APP_STORAGE_ROOT=/app/storage \
    SERVER_FORWARD_HEADERS_STRATEGY=framework \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
