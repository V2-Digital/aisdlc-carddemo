# cbact04c: INTCALC in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`.

```sh
mvn -B package
java -jar target/cbact04c.jar --PARM=2022071800 \
  --TCATBALF=in/TCATBALF.txt --XREFFILE=in/XREFFILE.txt --DISCGRP=in/DISCGRP.txt \
  --ACCTFILE=in/ACCTFILE.txt [--ACCTFILE-OUT=out/ACCTFILE.txt] \
  --TRANSACT=out/TRANSACT.txt [--CURRENT-DATE=YYYYMMDDHHMMSSss]
```

SYSOUT goes to stdout. Exit 0 on success, 16 on abend (no output files written).
Files are fixed-width text, LF, overpunch signs, as in `app/data/ASCII/`.

Legacy defects are preserved on purpose: last account never rewritten, truncated
interest, empty fees, silent overflow, no TCATBAL reset, `DALY REJECTS` message.
