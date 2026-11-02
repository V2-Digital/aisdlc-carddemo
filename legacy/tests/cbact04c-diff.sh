#!/usr/bin/env bash
# Runs COBOL CBACT04C (harness) and the Java rewrite on crafted edge inputs and diffs all outputs.
# Fixtures: legacy/tests/cbact04c-diff/<case>/{TCATBALF,XREFFILE,DISCGRP,ACCTFILE}.txt
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
ROOT=$PWD
FIX=$ROOT/legacy/tests/cbact04c-diff
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

(cd modernized/cbact04c && mvn -q -DskipTests package) || { echo "FAIL: java build"; exit 1; }
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar

fail=0
for dir in "$FIX"/*/; do
  c=$(basename "$dir")
  INPUT_DIR=$dir "$ROOT/legacy/harness/run.sh" CBACT04C "$WORK/$c/cobol" >"$WORK/$c.cobol.log" 2>&1 \
    || [[ -f $WORK/$c/cobol/RC.txt ]] || { echo "FAIL $c: harness did not run"; cat "$WORK/$c.cobol.log"; fail=1; continue; }
  mkdir -p "$WORK/$c/java"
  java -jar "$JAR" --parm 2022071800 --in "$dir" --out "$WORK/$c/java" --current-date 2022071800000000 \
    >"$WORK/$c/java/SYSOUT.txt" 2>/dev/null
  echo $? >"$WORK/$c/java/RC.txt"
  # the harness unloads datasets only after a clean run, so abends compare SYSOUT and RC only
  files="SYSOUT RC"
  [[ $(cat "$WORK/$c/cobol/RC.txt") == 0 ]] && files="TRANSACT ACCTFILE SYSOUT RC"
  for f in $files; do
    if ! cmp -s "$WORK/$c/cobol/$f.txt" "$WORK/$c/java/$f.txt"; then
      echo "FAIL $c: $f differs"; diff "$WORK/$c/cobol/$f.txt" "$WORK/$c/java/$f.txt" | head -10; fail=1
    fi
  done
  echo "ok   $c (RC $(cat "$WORK/$c/cobol/RC.txt"), $(wc -l <"$WORK/$c/java/SYSOUT.txt") sysout lines)"
done
exit $fail
