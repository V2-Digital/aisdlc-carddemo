# cbtrn02c — POSTTRAN / CBTRN02C in Java 21

Like-for-like rewrite of `app/cbl/CBTRN02C.cbl`. Plain Java 21, JUnit 5 only.

```sh
mvn -q verify
CARDDEMO_CURRENT_DATE=2022071800000000 java -cp target/classes carddemo.cbtrn02c.Main <IN_DIR> <OUT_DIR>
# or with the stock sample files (dailytran/cardxref/acctdata/tcatbal.txt):
... carddemo.cbtrn02c.Main --stock ../../app/data/ASCII <OUT_DIR>
```

Inputs `DALYTRAN|XREFFILE|ACCTFILE|TCATBALF.txt`; outputs `TRANFILE|DALYREJS|ACCTFILE|TCATBALF|SYSOUT.txt`.
Exit code 0 / 4 (rejects) / 16 (abend or load error). Formats as in `legacy/README.md`.

## Preserved legacy defects (do not fix here)
F1 created TCATBAL record inherits FILLER of last record read · F2 overlimit formula adds debits as headroom ·
F3 reason 103 overwrites 102 · F4 reason 109 on account rewrite failure is ignored · F5 TRANFILE opened OUTPUT
(replaced each run) · F6 DALYREJS close error shows XREFFILE status · F7 silent truncation (`S9(9)V99`) ·
F8 no check of account status, type/category, input PROC-TS.
