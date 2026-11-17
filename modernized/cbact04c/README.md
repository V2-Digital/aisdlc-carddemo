# cbact04c

Java 21 rewrite of the INTCALC batch (`app/cbl/CBACT04C.cbl`). Like-for-like: legacy defects (D1-D7 in CR-1) are preserved.

```sh
mvn -f modernized/cbact04c package -DskipTests
DD_TCATBALF=in/TCATBALF.txt DD_XREFFILE=in/XREFFILE.txt DD_DISCGRP=in/DISCGRP.txt \
DD_ACCTFILE=in/ACCTFILE.txt DD_TRANSACT=out/TRANSACT.txt \
CARDDEMO_CURRENT_DATE=2022071800000000 \
java -jar modernized/cbact04c/target/cbact04c.jar 2022071800 > SYSOUT.txt
```

- Datasets are the harness's fixed-width text form. `DD_ACCTFILE` is rewritten in place.
- `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, UTC) freezes the timestamps; otherwise the system clock is used.
- SYSOUT goes to stdout. Exit 0 on success, 16 after an abend (`CEE3ABD: ABEND U0999` on stderr).
