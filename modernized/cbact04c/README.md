# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Reads/writes the harness's fixed-width text files.

```sh
mvn -q package
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 \
  --TCATBALF=tcatbal.txt --XREFFILE=cardxref.txt --ACCTFILE=acctdata.txt --DISCGRP=discgrp.txt \
  --TRANSACT=out/TRANSACT.txt --ACCTOUT=out/ACCTFILE.txt [--SYSOUT=out/SYSOUT.txt]
```

Exit 0 = ok, 16 = abend. Clock: `CARDDEMO_CURRENT_DATE` (YYYYMMDDHHMMSSss) else UTC system clock.

## Preserved legacy defects (fix by separate CR)
- Last account in TCATBAL order is never rewritten (interest lost, cycles not reset).
- Interest truncated, not rounded; fees never computed.
- Timestamps use the clock, not the PARM date.
- Silent high-order truncation on amounts, balance and 6-digit TRAN-ID suffix.
- Group id comes from a blank field in sample data, so DEFAULT rates are always used.
- DISCGRP open error says "DALY REJECTS".
