# Use official Java 17 image (small and efficient)
FROM eclipse-temurin:17-jdk-alpine

# Set environment variable
ENV JAVA_OPTS=""

# Add a volume pointing to /tmp
VOLUME /tmp

# ARG to allow flexible JAR file name
ARG JAR_FILE=target/*.jar

# Copy JAR file to the container
COPY ${JAR_FILE} app.jar

# Run the JAR file
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
