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
ENTRYPOINT ["sh", "-c", "case \"$DATABASE_URL\" in postgres://*|postgresql://*) stripped=${DATABASE_URL#*://}; creds=${stripped%%@*}; hostdb=${stripped#*@}; export DATABASE_USERNAME=${DATABASE_USERNAME:-${creds%%:*}}; export DATABASE_PASSWORD=${DATABASE_PASSWORD:-${creds#*:}}; export DATABASE_URL=\"jdbc:postgresql://${hostdb}\";; esac; exec java -Dserver.port=${PORT:-8080} -jar app.jar"]

