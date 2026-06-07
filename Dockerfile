FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace
ARG BUILD_VERSION=20260607-fix9
RUN echo "=== Building version: ${BUILD_VERSION} ==="
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -DskipTests -B package && echo "=== Build SUCCESS version ${BUILD_VERSION} ==="

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /workspace/target/points-exchange-system-*.jar /app/app.jar
ENV JAVA_OPTS=
RUN echo "20260607-fix9" > /app/build-version.txt
CMD ["sh", "-c", "echo \"=== Starting app build version: $(cat /app/build-version.txt) ===\" && java $JAVA_OPTS -jar /app/app.jar"]
