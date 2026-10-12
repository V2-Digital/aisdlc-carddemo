# cbact04c — INTCALC (`CBACT04C`) in Java 21

Behaviour-preserving rewrite of `app/cbl/CBACT04C.cbl`. Reads/writes the canonical text datasets
(same as `legacy/golden`). Legacy defects are kept on purpose: D1 last account not rewritten,
D2 truncation, D3 group lookup on cols 113-122, D4 silent overflow. See `aisdlc/*/spec.md` §5.

```sh
mvn -q package
java -cp target/classes com.v2.carddemo.cbact04c.Main --parm 2022071800 \
  --tcatbalf tcatbal.txt --xreffile cardxref.txt --discgrp discgrp.txt \
  --acctfile acct.txt [--acctfile-out out.txt] --transact TRANSACT.txt \
  [--clock 2022071800000000]
```

Exit codes: 0 ok, 16 abend, 3 duplicate key on load, 2 usage. SYSOUT is stdout.
Without `--acctfile-out` the account file is rewritten in place.
