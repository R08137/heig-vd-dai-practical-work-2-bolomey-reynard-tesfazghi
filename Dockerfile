# Base image maven
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory
WORKDIR /app


COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -q -DskipTests package

# Base image java
FROM eclipse-temurin:21-jre

# Set the working directory
WORKDIR /app

# Copy the jar file
COPY target/practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar /app/practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar

# Default entrypoint: run the picocli app
ENTRYPOINT ["java", "-jar", "practical-work-2-bolomey-reynard-tesfazghi-1.0-SNAPSHOT.jar"]

# Set the default command
CMD ["--help"]