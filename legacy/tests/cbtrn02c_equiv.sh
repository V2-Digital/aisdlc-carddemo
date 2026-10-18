#!/usr/bin/env bash
# Differential test: runs the original COBOL (via the harness) and the Java rewrite
# on edge-case inputs in legacy/tests/cbtrn02c_equiv/<case>/ and diffs every output + RC.
# Abend cases (COBOL RC 16): run.sh exports no outputs, so only the RC is compared.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
(cd modernized/cbtrn02c && mvn -q package -DskipTests) || { echo "FAIL: java build"; exit 1; }
JAR=$ROOT/modernized/cbtrn02c/target/cbtrn02c.jar
CLOCK=2022071800000000
fail=0; n=0
for dir in legacy/tests/cbtrn02c_equiv/*/; do
  c=$(basename "$dir"); n=$((n+1))
  cob=$TMP/$c/cobol; jv=$TMP/$c/java; mkdir -p "$cob" "$jv"
  INPUT_DIR=$ROOT/$dir CARDDEMO_CURRENT_DATE=$CLOCK legacy/harness/run.sh CBTRN02C "$cob" >/dev/null 2>&1
  crc=$(cat "$cob/RC.txt")
  for f in DALYTRAN XREFFILE ACCTFILE TCATBALF; do cp "$dir/$f.txt" "$jv/$f.txt"; done
  jrc=0
  env DALYTRAN=$jv/DALYTRAN.txt XREFFILE=$jv/XREFFILE.txt ACCTFILE=$jv/ACCTFILE.txt TCATBALF=$jv/TCATBALF.txt \
      TRANFILE=$jv/TRANFILE.txt DALYREJS=$jv/DALYREJS.txt CARDDEMO_CURRENT_DATE=$CLOCK \
      java -jar "$JAR" >"$jv/SYSOUT.txt" 2>&1 || jrc=$?
  if [[ $crc != "$jrc" ]]; then echo "FAIL $c: RC cobol=$crc java=$jrc"; fail=1; continue; fi
  if [[ $crc == 16 ]]; then echo "ok   $c (RC 16 both; outputs not compared)"; continue; fi
  bad=""
  for f in TRANFILE DALYREJS ACCTFILE TCATBALF SYSOUT; do
    cmp -s "$cob/$f.txt" "$jv/$f.txt" || bad="$bad $f"
  done
  if [[ -n $bad ]]; then echo "FAIL $c: differs:$bad"; fail=1; else echo "ok   $c (RC $crc)"; fi
done
echo "$n cases, fail=$fail"
exit $fail
