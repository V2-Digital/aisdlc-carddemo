FROM eclipse-temurin:21-jre
COPY target/cbact04c.jar /app/cbact04c.jar
ENV INPUT_DIR=/data/in OUTPUT_DIR=/data/out
ENTRYPOINT ["java", "-jar", "/app/cbact04c.jar"]
