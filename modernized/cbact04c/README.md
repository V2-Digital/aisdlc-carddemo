# cbact04c — INTCALC (CBACT04C) in Java 21

Behaviour-preserving port; see `aisdlc/<run>/spec.md`. Legacy defects are preserved on purpose.

```sh
mvn -q package
java -jar target/cbact04c.jar --in <dir> --out <dir> [--parm 2022071800] [--clock 2022071800000000]
# container
docker run --rm -v "$PWD":/w -w /w eclipse-temurin:21-jre java -jar target/cbact04c.jar --in /w/in --out /w/out
```

`--in` needs `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt`; `--out` gets `TRANSACT.txt` and `ACCTFILE.txt`.
SYSOUT goes to stdout; exit code 0, or 16 on abend.
