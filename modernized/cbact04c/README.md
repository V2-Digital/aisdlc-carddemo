# cbact04c

Java 21 port of CardDemo `CBACT04C` (interest calculation). Behaviour-preserving, defects included.

```sh
mvn -q package                      # target/cbact04c.jar
java -jar target/cbact04c.jar --parm=2022071800 --in=<dir> --out=<dir> [--now=YYYYMMDDHHMMSShh]
```

- Reads `<in>/{TCATBALF,XREFFILE,DISCGRP,ACCTFILE}.txt`; writes `<out>/{TRANSACT,ACCTFILE}.txt`.
- SYSOUT goes to stdout; exit code is 0, or 16 on abend.
- Clock: `--now`, else `CARDDEMO_CURRENT_DATE`, else `COB_CURRENT_DATE`, else UTC system clock.
