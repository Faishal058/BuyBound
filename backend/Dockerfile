# Multi-stage build for BuyBound backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/buybound-0.4.0.jar app.jar
EXPOSE 8080
# Convert Render's postgresql://user:pass@host/db connection string to JDBC if provided
ENTRYPOINT ["sh", "-c", "RAW_DB=${SPRING_DATASOURCE_URL:-$DATABASE_URL}; case \"$RAW_DB\" in postgres://*|postgresql://*) stripped=${RAW_DB#*://}; creds=${stripped%%@*}; hostdb=${stripped#*@}; export SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:-${DATABASE_USERNAME:-${creds%%:*}}}; export SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-${DATABASE_PASSWORD:-${creds#*:}}}; export SPRING_DATASOURCE_URL=\"jdbc:postgresql://${hostdb}\"; export DATABASE_URL=\"$SPRING_DATASOURCE_URL\";; esac; exec java -Dserver.port=${PORT:-8080} -jar app.jar"]

