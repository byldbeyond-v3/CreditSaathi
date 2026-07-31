# Build stage
FROM maven:3.8.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY udriBook/pom.xml ./udriBook/
COPY udriBook/src ./udriBook/src
RUN mvn clean package -pl udriBook -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /app/udriBook/target/app.jar app.jar

# Create the uploads directory
RUN mkdir -p uploads/profiles

# Expose the port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
