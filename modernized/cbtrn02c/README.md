# CBTRN02C (POSTTRAN) in Java 21

Like-for-like rewrite of `app/cbl/CBTRN02C.cbl`. Reads and writes the harness's canonical text files.

```
mvn -q package
DALYTRAN=… XREFFILE=… ACCTFILE=… TCATBALF=… TRANFILE=out/TRANFILE.txt DALYREJS=out/DALYREJS.txt \
  ACCTFILE_OUT=out/ACCTFILE.txt TCATBALF_OUT=out/TCATBALF.txt \
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbtrn02c.jar
```
Exit code: 0, 4 (rejects), 16 (abend). SYSOUT goes to stdout. `<DD>_OUT` defaults to in-place.

## Preserved legacy defects (not fixed)
D1 overlimit uses credit − debit + amt (debit is negative); D2 103 overwrites 102; D3 reason 109 unreachable;
D4 DALYREJS close shows XREF status; D5 no SIZE ERROR truncation; D6 partial posting on duplicate TRAN-ID;
D7 text compare on expiry; D8 TRANFILE replaced every run; D9 WS-TEMP-BAL is S9(9)V99.
