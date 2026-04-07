FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests -pl lifesync-app -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/lifesync-app/target/lifesync-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
