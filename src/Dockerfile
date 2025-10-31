# ---------- Build stage ----------
FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app

# 1) Copy only POMs first for better cache on deps
COPY pom.xml /app/pom.xml
COPY sdk/pom.xml /app/sdk/pom.xml
COPY bet-gateway/pom.xml /app/bet-gateway/pom.xml

# 2) Pre-fetch dependencies for the reactor (esp. protobuf/grpc/tooling)
RUN mvn -q -f /app/pom.xml -pl bet-gateway -am -DskipTests dependency:go-offline

# 3) Now copy sources
COPY sdk/src /app/sdk/src
COPY bet-gateway/src /app/bet-gateway/src

# 4) Build SDK + Gateway and produce shaded jar for gateway
RUN mvn -q -f /app/pom.xml -pl bet-gateway -am -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy shaded jar from gateway
COPY --from=builder /app/bet-gateway/target/bet-gateway-1.0.0-all.jar /app/app.jar

# gRPC port
EXPOSE 9010

# Environment (override at runtime)
ENV MBS_WS_SERVER="wss://wss.dataplane-nonprod.sportradar.dev" \
    MBS_AUTH_SERVER="https://auth.sportradar.com/oauth/token" \
    MBS_CLIENT_ID="0BUCPReZtlxw4OvdAJpUWFSyQf77QeIW" \
    MBS_CLIENT_SECRET="5TtmC7b60RbTytGzZJAzL7szTcg5R_D6_O0wuHkPtDV3N5rbQRh-iH-VFfLjo06-" \
    MBS_AUDIENCE="mbs-dp-non-prod-wss" \
    MBS_OPERATOR_ID="37228" \
    mts_bookmaker_id="37228" \
    mts_currency="USD" \
    mts_limit_id="4793" \
    GRPC_PORT="9010"\
    BETTING_SERVICE_HOST="host.docker.internal" \
    BETTING_SERVICE_GRPC_PORT="3001"

ENTRYPOINT ["java","-jar","/app/app.jar"]
