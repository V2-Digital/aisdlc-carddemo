# CR-1 Plan: `CBACT04C` → `modernized/cbact04c/`

Behaviour: [spec.md](spec.md). Legacy facts: [intent.md](intent.md).

## Files, in order

1. `legacy/tests/cbact04c-oracle.sh`: for each case in `legacy/tests/cbact04c/<case>/input/*.txt`, runs
   `INPUT_DIR=<case>/input legacy/harness/run.sh CBACT04C <case>/expected`. Cases: `negative`, `multicard`,
   `group-found`, `overflow`, `abend-acct`, `abend-xref`, `abend-default`, `empty`. Expected outputs are committed.
2. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter, Surefire, jar with `Main-Class`. Nothing else.
3. `src/main/java/carddemo/cbact04c/`
   - `Zoned.java`: overpunch decode/encode, `BigDecimal` with fixed scale, low-N-digit truncation.
   - `Records.java`: fixed-width line read (CR strip, pad, length check) and the field offsets from spec.
   - `InterestCalc.java`: rules 1-7 over in-memory maps; `Sysout` sink, `Clock`, `PARM` injected.
   - `Abend.java` (exception carrying RC 16); `Main.java`: args/env (`DD_*`, `CARDDEMO_CURRENT_DATE`), exit code.
4. `src/test/java/...`: `ZonedTest`, `InterestTest`, `CharacterizationTest`, `GoldenEquivalenceTest`,
   `OracleCasesTest`. Tests read `legacy/golden/` and `legacy/tests/cbact04c/` via a relative repo path.
5. `modernized/cbact04c/README.md`: how to run, preserved defects F1-F6 list.

## Test strategy

- **Equivalence:** golden `CBACT04C` (stock `app/data/ASCII`) and `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF
  from `legacy/golden/CBTRN02C`). Compare TRANSACT, ACCTFILE, SYSOUT bytes and RC.
- **Oracle cases** (COBOL-generated, step 1): negatives/`-0.00` sign, several cards per account, found non-DEFAULT
  group, overflow, the three abends with partial outputs, empty input.
- **Characterization unit tests:** F1 last account untouched, truncation vs rounding, rate 0, suffix wrap, F5 text.
- Commands:
  ```sh
  legacy/tests/cbact04c-oracle.sh                       # (re)generate oracle expected outputs
  (cd modernized/cbact04c && mvn -q test)               # all Java tests
  java -jar modernized/cbact04c/target/cbact04c.jar 2022071800 ...   # manual diff vs golden
  legacy/harness/golden.sh --check                      # harness still green
  git diff --exit-code baseline -- legacy/golden legacy/harness
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Sign of `-0.00` / negative truncation guessed wrong (R1) | `negative` oracle case decides; no guessing |
| AIX card choice with duplicates (R2) | `multicard` oracle case |
| Overflow semantics differ from `BigDecimal` | `overflow` oracle; explicit low-digit truncation helper |
| Abend partial output (what the harness unloads after U999) | `abend-*` oracles define it; match their files |
| Trailing spaces / LF / CRLF inputs | byte compare in every test; CRLF `tcatbal.txt` in stock scenario |
| Repo-relative paths in tests break in CI | resolve repo root from `pom` basedir; fail loudly if missing |

## Effort (agent)

Oracle script + cases 10 min · Java code 15 min · tests 15 min · fix-ups and verification 10 min → **~50 min**.
