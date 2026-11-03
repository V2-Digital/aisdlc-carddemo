# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Plain Java, no frameworks, JUnit 5 for tests.

```
mvn -q package
java -jar target/cbact04c.jar --data ../../app/data/ASCII [--input-dir <dir>] \
     --out <dir> --parm 2022071800 [--clock 2022071800000000]
```

- Reads `TCATBALF/XREFFILE/ACCTFILE/DISCGRP` as fixed-width text (`--input-dir/<DD>.txt` wins over the sample in `--data`).
- Writes `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, `RC.txt` to `--out`. Exit 0 ok, 16 abend (only SYSOUT and RC written).
- `--clock` is `YYYYMMDDHHMMSSss`; default is the system UTC clock.

Verified byte-identical to `legacy/golden/CBACT04C` and `CBACT04C-after-CBTRN02C`.

## Preserved legacy defects (not fixed here)
D1 last account never rewritten · D2 interest truncated, not rounded · D3 blank `ACCT-GROUP-ID` so all rates come from `DEFAULT` ·
D4 fees are a no-op · D5 timestamps from the clock · D6 zero-amount transactions · D7 no negative-balance guard ·
D8 first card per account · D9 DISCGRP open error says "DALY REJECTS FILE". See `aisdlc/*/spec.md`.
