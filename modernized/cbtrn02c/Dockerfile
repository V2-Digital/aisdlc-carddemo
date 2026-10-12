FROM eclipse-temurin:21-jre
COPY target/cbtrn02c.jar /app/cbtrn02c.jar
WORKDIR /data
# DD_<NAME> env vars select dataset paths; default is <NAME> in /data
ENTRYPOINT ["java", "-jar", "/app/cbtrn02c.jar"]
