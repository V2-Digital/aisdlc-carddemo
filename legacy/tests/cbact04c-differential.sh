#!/usr/bin/env bash
# Differential test: COBOL CBACT04C (GnuCOBOL harness) vs the Java rewrite on crafted inputs.
# Run from the repo root. Exits non-zero on any difference.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c-differential
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
(cd "$ROOT/modernized/cbact04c" && mvn -q -DskipTests package) || { echo "build failed"; exit 2; }
export CARDDEMO_CURRENT_DATE=2022071800000000
fail=0
for dir in "$FIX"/*/; do
  c=$(basename "$dir")
  cob=$WORK/$c/cobol; jv=$WORK/$c/java
  mkdir -p "$cob" "$jv"
  INPUT_DIR=$dir "$ROOT/legacy/harness/run.sh" CBACT04C "$cob" >/dev/null 2>&1
  for dd in TCATBALF XREFFILE DISCGRP ACCTFILE; do cp "$dir/$dd.txt" "$jv/$dd.txt"; done
  env DD_TCATBALF="$jv/TCATBALF.txt" DD_XREFFILE="$jv/XREFFILE.txt" DD_DISCGRP="$jv/DISCGRP.txt" \
      DD_ACCTFILE="$jv/ACCTFILE.txt" DD_TRANSACT="$jv/TRANSACT.txt" \
      java -cp "$ROOT/modernized/cbact04c/target/classes" carddemo.cbact04c.Cbact04c 2022071800 \
      >"$jv/SYSOUT.txt" 2>/dev/null
  echo $? >"$jv/RC.txt"
  # on abend the harness does not export datasets, so only SYSOUT and RC are comparable
  files="SYSOUT.txt RC.txt"
  [[ $(cat "$cob/RC.txt") == 0 ]] && files="$files TRANSACT.txt ACCTFILE.txt"
  ok=1
  for f in $files; do
    cmp -s "$cob/$f" "$jv/$f" || { ok=0; echo "DIFF $c/$f"; diff "$cob/$f" "$jv/$f" | head -5; }
  done
  if (( ok )); then echo "PASS $c (rc=$(cat "$cob/RC.txt"); $files)"; else fail=1; fi
done
exit $fail
