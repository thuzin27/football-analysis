# ── Stage 1: Build ────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-jammy AS builder

RUN apt-get update \
 && apt-get install -y --no-install-recommends maven \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copia pom.xml primeiro para cachear o download de dependências
# entre builds quando só o código-fonte muda
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

COPY --from=builder /build/target/football-analysis-1.0.0.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar", "--web"]
