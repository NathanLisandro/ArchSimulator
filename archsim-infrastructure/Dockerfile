FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY archsim-domain/pom.xml archsim-domain/
COPY archsim-application/pom.xml archsim-application/
COPY archsim-infrastructure/pom.xml archsim-infrastructure/
RUN mvn -q -B dependency:go-offline
COPY . .
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/archsim-infrastructure/target/archsim-infrastructure-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
