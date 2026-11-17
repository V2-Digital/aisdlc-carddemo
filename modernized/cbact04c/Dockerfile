# Run note only; not built in CR-1.
#   mvn -f modernized/cbact04c package -DskipTests
#   docker build -t cbact04c modernized/cbact04c
FROM eclipse-temurin:21-jre
COPY target/cbact04c.jar /app/cbact04c.jar
ENV DD_TCATBALF=/data/TCATBALF.txt DD_XREFFILE=/data/XREFFILE.txt \
    DD_DISCGRP=/data/DISCGRP.txt DD_ACCTFILE=/data/ACCTFILE.txt DD_TRANSACT=/data/TRANSACT.txt
ENTRYPOINT ["java", "-jar", "/app/cbact04c.jar"]
CMD ["2022071800"]
