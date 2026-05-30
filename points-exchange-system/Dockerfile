FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /workspace/target/points-exchange-system-*.jar /app/app.jar
ENV JAVA_OPTS=
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
