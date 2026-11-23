# CR-1 Build

Java 21 port in `modernized/cbact04c/` (`ZonedDecimal`, `FixedWidthFiles`, `InterestCalc`, `Main`, `AbendException`).

## Evidence
- `mvn -f modernized/cbact04c verify`: green. JUnit 5 only.
- Both golden scenarios: `TRANSACT`, `ACCTFILE` and stdout (`SYSOUT`) are byte-identical, exit 0.
  Checked by the CLI and by `GoldenTest`.
- `legacy/harness/golden.sh --check`: OK (legacy untouched).

## Deviations / decisions for a human
- **D7 settled from source, not by running it.** The harness XREF alt key has no DUPLICATES, so loading two cards for
  one account fails (status 22) before CBACT04C runs (`legacy/tests/xref_dup.sh`). On z/OS a duplicate alt-key read
  returns status 02, and `CBACT04C.cbl:400` treats that as an error. Java therefore abends (exit 16) on duplicate
  cards. This contradicts the intent D7 "first card" default. Please confirm.
- Test stage still owes: `InterestTest`, `LastAccountDefectTest`, `ZeroRateZeroBalTest`, `AbendTest`, and a
  harness run with a negative balance (truncation sign). Build only has golden and zoned-decimal tests.
- D5 (DISCGRP open-error text) is not modelled. Java does not emulate file-open failures.
