# ============================================================
# PostgresPulse - imagen de produccion (Fase 7)
# Build: docker build -t postgrespulse:1.0 .
# Uso normal: via el servicio "app" de docker-compose.yml
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pulse && adduser -S pulse -G pulse
WORKDIR /app

COPY --from=build /app/target/postgrespulse-*.jar app.jar
RUN chown pulse:pulse app.jar
USER pulse

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
