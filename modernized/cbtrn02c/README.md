# cbtrn02c: POSTTRAN in Java 21

Like-for-like rewrite of `app/cbl/CBTRN02C.cbl`. Build: `mvn -q package`. Run: `java -jar target/cbtrn02c.jar`.

- Files: env `DD_<DALYTRAN|XREFFILE|ACCTFILE|TCATBALF|TRANFILE|DALYREJS>` = path (default `<NAME>` in cwd). ACCTFILE and TCATBALF are updated in place.
- Clock: `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` freezes it, else system clock.
- Exit: 0 no rejects, 4 rejects, 16 abend.
- Verified on stock data: TRANFILE, DALYREJS, ACCTFILE, TCATBALF and stdout byte-identical to `legacy/golden/CBTRN02C`, RC 4.

## Preserved legacy defects (do not fix; see `Cbtrn02c.java`)
D1 overlimit = credit - debit + amt (payments count as spend, balance ignored) · D2 103 overwrites 102 ·
D3 account REWRITE failure ignored · D4 no atomicity on TRANFILE write abend · D5 TRANFILE opened OUTPUT (replaced each run) ·
D6 DALYREJS close error shows XREFFILE status (cannot occur in Java) · D7 silent truncation to S9(9)V99 / S9(10)V99 ·
D8 ACTIVE-STATUS unchecked, text date compare, input PROC-TS dropped · D9 created TCATBAL record inherits FILLER of the last record read.
