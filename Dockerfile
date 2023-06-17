# Build stage
FROM maven:3.6.0-jdk-11-slim AS build
WORKDIR /home/app
COPY pom.xml .

# Arguments
ARG username
ARG token

# Print argument values
RUN echo $username
RUN echo $token

# Copy settings file to home directory
COPY settings.xml .

# Replace username & token in settings file
RUN sed -i "s/USERNAME/$username/g" settings.xml
RUN sed -i "s/TOKEN/$token/g" settings.xml
RUN cat settings.xml

# Maven package build
RUN mvn -s settings.xml dependency:go-offline

COPY src ./src
RUN mvn package -s settings.xml -DskipTests=true

# Package stage
FROM adoptopenjdk:11-jre-hotspot
WORKDIR /home/app
COPY --from=build /home/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx1024m", "-Xshareclasses", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseG1GC", "-XX:+ExplicitGCInvokesConcurrent", "-jar", "app.jar"]
