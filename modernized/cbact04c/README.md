# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Legacy defects are kept on purpose.

```
mvn -f modernized/cbact04c verify
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar --parm 2022071800 \
  --tcatbalf TCATBALF.txt --xreffile XREFFILE.txt --acctfile ACCTFILE.txt --discgrp DISCGRP.txt \
  --transact out/TRANSACT.txt --acctfile-out out/ACCTFILE.txt > SYSOUT.txt
```
Files are the harness text format (fixed width, LF, overpunch). Exit 0 = ok, 16 = abend (discard outputs).
Clock: `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else system UTC.

Preserved defects: D1 last account never rewritten · D2 interest truncated · D3 blank `ACCT-GROUP-ID`
→ always `DEFAULT` · D4 no fees · D5 DISCGRP open error says "DALY REJECTS FILE" (unreachable here:
no file-open failures are modelled) · D6 silent high-order truncation · D7 duplicate xref card → abend
(`legacy/tests/xref_dup.sh`: harness cannot load duplicates, so decided from source).
