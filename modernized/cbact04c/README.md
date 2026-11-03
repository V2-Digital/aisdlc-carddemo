# cbact04c

Java 21 rewrite of CardDemo batch `CBACT04C` (interest calculation). Output is byte-identical to the COBOL, defects included.

```
mvn -q package
java -jar target/cbact04c-1.0.0.jar --parm 2022071800 [--timestamp 2022-07-18-00.00.00.000000] \
  --TCATBALF <f> --XREFFILE <f> --DISCGRP <f> --ACCTFILE <f> --out <dir> > <dir>/SYSOUT.txt
```

Writes `<dir>/TRANSACT.txt` and `<dir>/ACCTFILE.txt`. DISPLAY lines go to stdout. Exit 0, or 16 on abend.
