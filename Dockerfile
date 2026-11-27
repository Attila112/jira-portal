# Jira Portal – egyetlen konténer: a Spring Boot szolgálja ki a lebuildelt React frontendet is.

# 1) Frontend build (Vite) -> backend/src/main/resources/static
FROM node:22-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) Backend build (Maven)
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package

# 3) Futtatás (csak JRE)
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 portal
COPY --from=backend /app/backend/target/jira-portal-*.jar /app/app.jar
USER portal
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
