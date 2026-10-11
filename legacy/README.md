# legacy/ - GnuCOBOL harness for CardDemo batch

Runs the original CardDemo batch programs **CBACT04C** (interest calculation,
`app/jcl/INTCALC.jcl`) and **CBTRN02C** (transaction posting,
`app/jcl/POSTTRAN.jcl`) unmodified under GnuCOBOL, and keeps their outputs in
`golden/` as the reference for equivalence tests against rewrites.

- Image: `debian:trixie-slim` + Debian `gnucobol3` 3.2-3 (**GnuCOBOL 3.2.0**,
  indexed handler **BDB**, Berkeley DB 5.3). About 413 MB, mostly gcc.
  `openjdk-21-jdk-headless` is available from the same apt repository.
- Builds and runs on linux/arm64 and linux/amd64 with byte-identical output
  (amd64 checked under emulation with `make verify PLATFORM=linux/amd64`).
- CardDemo sources: the enclosing repo when it is the CardDemo fork
  (`../app/cbl`), else a shallow clone in `legacy/.carddemo` (gitignored,
  `make source` creates it). Override with `CARDDEMO_HOME`.

## Usage

```sh
make build                   # docker image carddemo-gnucobol:local
make run PROGRAM=CBACT04C    # -> out/CBACT04C/*.txt (gitignored)
make golden                  # regenerate golden/ (each scenario runs twice; must be identical)
make verify                  # re-run and diff against golden/ (use in CI)
```

Inside the image (or any host with GnuCOBOL 3.2 + BDB):
`harness/run.sh <PROGRAM> [OUT_DIR]`. Set `INPUT_DIR=<dir>` to replace sample
input datasets with `<dir>/<DD>.txt`, for example a previous program's outputs.

## Harness

| File | Role |
|---|---|
| `harness/run.sh` | Compiles everything into a fresh temp work dir. Loads the sample data, runs the program, then unloads every output dataset. |
| `harness/cdfiles.cbl` | `cdfiles LOAD\|UNLOAD <DD>`: text to INDEXED/SEQUENTIAL and back. Keys come from the IDCAMS definitions. XREFFILE gets its AIX (acct id, offset 25, length 11) as an alternate key, which BDB stores in `XREFFILE.1`, so the JCL's `XREFFIL1` path DD is not needed. |
| `harness/pgmdrv.cbl` | Stands in for `EXEC PGM=x,PARM='...'`: builds the halfword-length + text PARM block and dynamically CALLs `x.so` with it. The program's RETURN-CODE becomes the exit status. |
| `harness/cee3abd.cbl` | CEE3ABD stub: prints `CEE3ABD: ABEND U0999` to stderr and exits with **16**. |
| `harness/golden.sh` | Writes `golden/`, or diffs against it (`--check`). |

Files are bound through GnuCOBOL's `DD_<ddname>` environment variables, using
the JCL DD names. No CardDemo source is patched.

Compile flags: `cobc -fsign=EBCDIC -fassign-clause=ibm` (default dialect).

## Outputs (`golden/<scenario>/`)

Canonical text form: one record per line, fixed width (trailing spaces kept),
LF line endings, zoned decimals with overpunch signs. This is the same format
as `app/data/ASCII/*.txt`, so an output can be fed back in as an input.
KSDS files are unloaded in primary-key order; sequential files in write order.
`SYSOUT.txt` is the program's DISPLAY output. `RC.txt` is the exit status.

| Scenario | File | Records | What it is |
|---|---|---|---|
| `CBACT04C` (stock data, PARM `2022071800`) | `TRANSACT.txt` | 50 | new interest transactions (LRECL 350) |
| | `ACCTFILE.txt` | 50 | account master after REWRITE (LRECL 300) |
| | `SYSOUT.txt` / `RC.txt` | 152 / `0` | |
| `CBTRN02C` (stock data) | `TRANFILE.txt` | 262 | posted transactions, new KSDS (LRECL 350) |
| | `DALYREJS.txt` | 38 | rejects: tran + 80-byte trailer (LRECL 430). All 38 are `0102 OVERLIMIT TRANSACTION` |
| | `ACCTFILE.txt` | 50 | balances / cycle credit+debit updated |
| | `TCATBALF.txt` | 100 | 50 updated + 50 created category balances |
| | `SYSOUT.txt` / `RC.txt` | 54 / `4` | RC 4 = rejects present (by design) |
| `CBACT04C-after-CBTRN02C` | `TRANSACT.txt` | 50 | interest on the posted balances (non-zero amounts) |
| | `ACCTFILE.txt` | 50 | |
| | `SYSOUT.txt` / `RC.txt` | 302 / `0` | |

The third scenario runs CBACT04C on the ACCTFILE and TCATBALF that CBTRN02C
leaves behind (`INPUT_DIR=golden/CBTRN02C`), which is the nightly order. It
exists because every stock TCATBAL balance is zero: on stock data CBACT04C
writes 50 zero-amount transactions, and ACCTFILE comes out identical to
`acctdata.txt`.

## Determinism

- `COB_CURRENT_DATE=2022071800000000` (`YYYYMMDDHHMMSSss`) and `TZ=UTC`
  freeze `FUNCTION CURRENT-DATE` (override with `CARDDEMO_CURRENT_DATE`).
  The 16-digit form is required. The documented `"YYYY/MM/DD HH:MM:SS"` and
  14-digit forms still take the **hundredths from the real clock**, so
  timestamps would differ between runs.
- With the frozen clock, no masking is needed; `make golden` proves two runs
  are byte-identical. If you compare against an unfrozen run, mask these
  timestamps (1-based byte columns of the 350-byte tran record):
  `TRAN-ORIG-TS` 279-304 (CBACT04C `TRANSACT` only) and `TRAN-PROC-TS` 305-330
  (`TRANSACT` and CBTRN02C `TRANFILE`). They read `2022-07-18-00.00.00.000000`.

## Known quirks (keep these in mind when reading or rewriting)

- **Signs:** the sample data uses EBCDIC-style overpunch (`{`=+0, `A`-`I`=+1..9,
  `}`=-0, `J`-`R`=-1..9). Without `-fsign=EBCDIC`, GnuCOBOL misreads `{` and
  writes `p`-`y` signs: `000012{` + 1 gives `000003q` instead of `000022{`.
- **Uninitialised WORKING-STORAGE = spaces.** `TRAN-RECORD` is never
  initialised. In CBACT04C, the tail of `TRAN-DESC` after the STRING, and the
  20-byte FILLER (cols 331-350), keep their initial value. That value is
  spaces under the default dialect. With `-std=ibm` it would be `x'00'`.
  Those are the only differences between the two dialects for these runs.
- **Sample data:** the `A000000000` group code sits in `ACCT-ADDR-ZIP`, and
  `ACCT-GROUP-ID` is blank. Every disclosure-group lookup therefore misses
  and falls back to `DEFAULT`; SYSOUT prints `DISCLOSURE GROUP RECORD
  MISSING` for each one. `cardxref.txt` lines are 36 bytes (they are padded
  to 50). `tcatbal.txt`, `trancatg.txt` and `trantype.txt` have CRLF line
  endings (stripped on load).
- **CBACT04C last-account write-back bug: confirmed.** The main loop's
  `ELSE PERFORM 1050-UPDATE-ACCOUNT` can never run, because the loop
  condition is tested before the body after EOF. As a result, the last
  account's accumulated interest is never rewritten. Evidence is in
  `golden/CBACT04C-after-CBTRN02C`:
  - `TRANSACT.txt` record `2022071800000050` charges account `00000000050`
    interest of 18.77 (`0000000187G`).
  - In `ACCTFILE.txt`, account `00000000050` is byte-identical to
    `golden/CBTRN02C/ACCTFILE.txt`. Its balance is still 1945.87
    (`00000019458G`), and cycle credit/debit are still 1501.75 / -47.88
    instead of zero.
  - Accounts 1-49 do get the interest added and their cycle fields zeroed
    (49 of 50 records change).
  - On stock data the bug is invisible, because all the interest is zero.
- **CBACT04C `1400-COMPUTE-FEES`** is an empty paragraph (`EXIT.`). It is
  performed after each non-zero-rate interest calculation and does nothing
  (CR-2 target).
- CBTRN02C opens `TRANFILE` `OUTPUT`, so the harness starts it empty instead
  of preloading the transaction master.
- Debian's libcob prints `Warning: program compiled against libxml 212 using
  older 209` to stderr. It is harmless and is filtered out of the run.sh
  output.
