FROM eclipse-temurin:21-jre
COPY target/cbtrn02c.jar /app/cbtrn02c.jar
# DD_DALYTRAN, DD_XREFFILE, DD_ACCTFILE, DD_TCATBALF, DD_TRANFILE, DD_DALYREJS point at the text datasets.
# Optional: CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss
ENTRYPOINT ["java", "-jar", "/app/cbtrn02c.jar"]
