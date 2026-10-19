#!/usr/bin/env bash
# Edge cases for the Java rewrite of CBACT04C: the same inputs go through the real COBOL (harness/run.sh) and
# through modernized/cbact04c; SYSOUT and RC must match for every case, TRANSACT/ACCTFILE for the non-abend ones.
# (run.sh exports no datasets when the program abends, so abend cases compare SYSOUT + RC only.)
# Run from the repo root.
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c_edge
SAMPLE=$ROOT/app/data/ASCII
WORK=$(mktemp -d "${TMPDIR:-/tmp}/cbact04c-edge.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
fail=0

mvn -q -f "$ROOT/modernized/cbact04c/pom.xml" package -DskipTests || { echo "FAIL: mvn package"; exit 1; }
JAR=$(ls "$ROOT"/modernized/cbact04c/target/*.jar | grep -v original | head -1)
[[ -f $JAR ]] || { echo "FAIL: jar not built"; exit 1; }

# run_case <name> : inputs in $WORK/<name>/in/<DD>.txt (only the DDs that differ from the sample data)
run_case() {
  local name=$1 d=$WORK/$1 dd src
  mkdir -p "$d/cobol" "$d/java"
  INPUT_DIR=$d/in "$ROOT/legacy/harness/run.sh" CBACT04C "$d/cobol" >/dev/null 2>"$d/cobol.err"
  local crc=$?
  [[ -f $d/cobol/RC.txt ]] || echo "$crc" >"$d/cobol/RC.txt"
  local args=()
  for pair in tcatbalf:TCATBALF xreffile:XREFFILE discgrp:DISCGRP; do
    dd=${pair#*:}; src=$d/in/$dd.txt
    if [[ ! -f $src ]]; then
      case $dd in TCATBALF) src=$SAMPLE/tcatbal.txt ;; XREFFILE) src=$SAMPLE/cardxref.txt ;; DISCGRP) src=$SAMPLE/discgrp.txt ;; esac
    fi
    args+=(--"${pair%%:*}" "$src")
  done
  local acct=$d/in/ACCTFILE.txt; [[ -f $acct ]] || acct=$SAMPLE/acctdata.txt
  COB_CURRENT_DATE=2022071800000000 java -jar "$JAR" --parm 2022071800 "${args[@]}" --acctfile-in "$acct" \
    --acctfile-out "$d/java/ACCTFILE.txt" --transact "$d/java/TRANSACT.txt" --sysout "$d/java/SYSOUT.txt" \
    >/dev/null 2>"$d/java.err"
  echo $? >"$d/java/RC.txt"
  local files=(SYSOUT.txt RC.txt)
  [[ $(cat "$d/cobol/RC.txt") == 0 ]] && files+=(TRANSACT.txt ACCTFILE.txt)
  for f in "${files[@]}"; do
    if cmp -s "$d/cobol/$f" "$d/java/$f"; then
      echo "ok   $name/$f"
    else
      echo "FAIL $name/$f differs (cobol vs java)"; diff "$d/cobol/$f" "$d/java/$f" | head -5; fail=1
    fi
  done
  # sanity: the case really exercised what it claims
  if [[ $2 == abend && $(cat "$d/cobol/RC.txt") != 16 ]]; then echo "FAIL $name: COBOL RC not 16"; fail=1; fi
  if [[ $2 == ok && $(cat "$d/cobol/RC.txt") != 0 ]]; then echo "FAIL $name: COBOL RC not 0"; fail=1; fi
}

new_case() { mkdir -p "$WORK/$1/in"; }

# 1. negative balance (truncation toward zero), rate-0 category (type 03), 99.99 truncation, last account (D1)
#    (the fixture has negative balances: -1.00 and -0.00; positive 99.99; account 6 is last, D1)
new_case negative; cp "$FIX/TCATBALF_mixed.txt" "$WORK/negative/in/TCATBALF.txt"
run_case negative ok

# 2. account whose only category has rate 0: no txn, no counter increment, account still updated
new_case rate0only; cp "$FIX/TCATBALF_rate0.txt" "$WORK/rate0only/in/TCATBALF.txt"
run_case rate0only ok

# 3. empty TCATBALF
new_case empty; : >"$WORK/empty/in/TCATBALF.txt"
run_case empty ok

# 4. DEFAULT disclosure group missing -> abend
new_case nodefault; grep -v '^DEFAULT' "$SAMPLE/discgrp.txt" >"$WORK/nodefault/in/DISCGRP.txt"
run_case nodefault abend

# 5. xref missing for account 2 -> abend
new_case noxref; grep -v '^.\{25\}00000000002' "$SAMPLE/cardxref.txt" >"$WORK/noxref/in/XREFFILE.txt"
run_case noxref abend

# 6. account 2 missing from ACCTFILE -> abend
new_case noacct; grep -v '^00000000002' "$SAMPLE/acctdata.txt" >"$WORK/noacct/in/ACCTFILE.txt"
run_case noacct abend

(( fail )) && { echo "cbact04c_edge: FAILED"; exit 1; }
echo "cbact04c_edge: all cases identical"
