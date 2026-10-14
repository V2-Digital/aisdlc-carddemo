# cbact04c — INTCALC / CBACT04C in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`.

    mvn package            # builds target/cbact04c.jar, runs tests
    java -jar target/cbact04c.jar --parm 2022071800 \
      --TCATBALF f --XREFFILE f --DISCGRP f --ACCTFILE f --TRANSACT f [--clock YYYYMMDDHHMMSSss]

Files are the harness's canonical text format. ACCTFILE is read and rewritten in place.
Clock: `--clock` > env `CARDDEMO_CURRENT_DATE` > system UTC. Exit 0, or 16 on abend.

Legacy defects kept on purpose: D1 last account never rewritten, D2 no fees,
D3 interest truncated, D4 DISCGRP open error text, F2 silent overflow/wrap.
