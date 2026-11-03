#!/usr/bin/env bash
# Cross-check: COBOL CBACT04C (harness) vs Java port on synthetic edge inputs.
# Covers negative balances, -0.01 -> zero sign, group-specific rate, DEFAULT fallback,
# zero rate, last-account defect, and abends (xref missing, DEFAULT missing).
set -u
cd "$(dirname "$0")/../.."
FIX=legacy/tests/cbact04c_edge
JAR=$(ls modernized/cbact04c/target/cbact04c*.jar 2>/dev/null | grep -v original | head -1)
if [[ -z $JAR ]]; then (cd modernized/cbact04c && mvn -q -DskipTests package) || exit 1
  JAR=$(ls modernized/cbact04c/target/cbact04c*.jar | head -1); fi
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
fail=0
for c in ok abend_xref abend_default; do
  mkdir -p "$T/$c/cobol" "$T/$c/java"
  INPUT_DIR=$FIX/$c legacy/harness/run.sh CBACT04C "$T/$c/cobol" >/dev/null 2>"$T/$c/cobol.err"
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar "$JAR" "$FIX/$c" "$T/$c/java" 2022071800 \
    >"$T/$c/java/SYSOUT.txt" 2>"$T/$c/java.err"; echo $? >"$T/$c/java/RC.txt"
  files="SYSOUT.txt RC.txt"; [[ $c == ok ]] && files="$files TRANSACT.txt ACCTFILE.txt"
  for f in $files; do
    if cmp -s "$T/$c/cobol/$f" "$T/$c/java/$f"; then echo "PASS $c/$f"
    else echo "FAIL $c/$f"; diff "$T/$c/cobol/$f" "$T/$c/java/$f" | head -10; fail=1; fi
  done
done
[[ $(cat "$T/ok/java/RC.txt") == 0 && $(cat "$T/abend_xref/java/RC.txt") == 16 \
  && $(cat "$T/abend_default/java/RC.txt") == 16 ]] || { echo "FAIL unexpected RC"; fail=1; }
exit $fail
