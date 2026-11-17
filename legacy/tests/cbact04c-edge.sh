#!/usr/bin/env bash
# Differential test: crafted inputs through COBOL (harness) and the Java jar; outputs must match.
# Run from the repo root: bash legacy/tests/cbact04c-edge.sh
set -u
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
fail=0

(cd modernized/cbact04c && mvn -q -DskipTests package) >"$T/mvn.log" 2>&1 || { cat "$T/mvn.log"; echo "FAIL: build"; exit 1; }
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar

for c in mixed missing-default missing-account missing-xref single-account empty; do
  in=$T/$c/in; cob=$T/$c/cobol; jv=$T/$c/java
  mkdir -p "$in" "$cob" "$jv"
  python3 -I legacy/tests/cbact04c-edge/gen.py "$c" "$in" || { echo "FAIL $c: gen"; fail=1; continue; }
  INPUT_DIR=$in legacy/harness/run.sh CBACT04C "$cob" >/dev/null 2>&1
  java -jar "$JAR" --parm 2022071800 --in "$in" --out "$jv" --now 2022071800000000 >"$jv/SYSOUT.txt" 2>/dev/null
  echo $? >"$jv/RC.txt"
  ok=1
  for f in SYSOUT.txt RC.txt; do cmp -s "$cob/$f" "$jv/$f" || { echo "FAIL $c: $f differs"; diff "$cob/$f" "$jv/$f" | head -5; ok=0; }; done
  if [[ $(cat "$cob/RC.txt") == 0 ]]; then
    for f in TRANSACT.txt ACCTFILE.txt; do cmp -s "$cob/$f" "$jv/$f" || { echo "FAIL $c: $f differs"; diff "$cob/$f" "$jv/$f" | head -5; ok=0; }; done
    echo "  $c: RC=0 TRANSACT=$(wc -l <"$jv/TRANSACT.txt") ACCTFILE=$(wc -l <"$jv/ACCTFILE.txt") SYSOUT=$(wc -l <"$jv/SYSOUT.txt")"
  else
    echo "  $c: RC=$(cat "$cob/RC.txt") (abend) SYSOUT=$(wc -l <"$jv/SYSOUT.txt")"
  fi
  [[ $ok == 1 ]] && echo "PASS $c" || fail=1
done
exit $fail
