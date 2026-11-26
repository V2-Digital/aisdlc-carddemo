#!/usr/bin/env bash
# COBOL (GnuCOBOL harness) vs Java rewrite on crafted inputs: negative balances,
# real disclosure group, multiple categories, overflow, and an abend (partial files).
# Run from the repo root. Exits non-zero on any difference.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-diff
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

if [[ ! -f $JAR ]]; then
  (cd "$ROOT/modernized/cbact04c" && mvn -q -DskipTests package) || { echo "FAIL: build"; exit 1; }
fi

fail=0
for c in caseA caseB; do
  cob=$TMP/$c/cobol; jav=$TMP/$c/java; mkdir -p "$cob" "$jav"
  INPUT_DIR=$FIX/$c "$ROOT/legacy/harness/run.sh" CBACT04C "$cob" >"$TMP/$c/run.log" 2>&1
  crc=$?
  files="TRANSACT ACCTFILE SYSOUT RC"
  if [[ $crc -eq 16 ]]; then
    # abend: the harness exports only SYSOUT (no partial datasets), so compare SYSOUT + RC
    echo 16 >"$cob/RC.txt"; files="SYSOUT RC"
  elif [[ $crc -ne 0 ]]; then
    echo "FAIL $c: harness run failed (rc=$crc)"; cat "$TMP/$c/run.log"; fail=1; continue
  fi
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar "$JAR" 2022071800 "$FIX/$c" "$jav" >"$jav/SYSOUT.txt" 2>/dev/null
  echo $? >"$jav/RC.txt"
  for f in $files; do
    if cmp -s "$cob/$f.txt" "$jav/$f.txt"; then
      echo "ok   $c $f"
    else
      echo "FAIL $c $f differs"; diff "$cob/$f.txt" "$jav/$f.txt" | head -10; fail=1
    fi
  done
done
# the cases must exercise what they claim
grep -q '^16$' "$TMP/caseB/cobol/RC.txt" || { echo "FAIL caseB did not abend in COBOL"; fail=1; }
grep -q '^0$' "$TMP/caseA/cobol/RC.txt" || { echo "FAIL caseA did not finish RC 0 in COBOL"; fail=1; }
exit $fail
