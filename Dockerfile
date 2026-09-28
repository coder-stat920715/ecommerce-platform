# Generic image for any Spring Boot module:  docker build --build-arg MODULE=order-service -t shop/order-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN mvn -q -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULE
RUN useradd --system --uid 1001 app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
