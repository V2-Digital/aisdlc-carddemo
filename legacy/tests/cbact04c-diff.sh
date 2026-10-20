#!/usr/bin/env bash
# Differential test: original COBOL (GnuCOBOL harness) vs Java port on edge-case fixtures.
# Normal fixtures: TRANSACT/ACCTFILE/SYSOUT/RC must be byte-identical.
# Abend fixtures: the harness exports nothing on abend, so only the exit status (16) is compared.
set -u
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root" || exit 1
fx=legacy/tests/cbact04c-diff
jar=modernized/cbact04c/target/cbact04c.jar
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
[[ -f $jar ]] || (cd modernized/cbact04c && mvn -q package -DskipTests) || { echo "FAIL: build"; exit 1; }
fail=0
for dir in "$fx"/*/; do
  name=$(basename "$dir")
  INPUT_DIR=$dir legacy/harness/run.sh CBACT04C "$tmp/$name/cobol" >/dev/null 2>"$tmp/$name.err"
  crc=$?
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar "$jar" --parm 2022071800 --in "$dir" --out "$tmp/$name/java" >/dev/null 2>&1
  jrc=$?
  if [[ $crc -ne $jrc ]]; then echo "FAIL $name: exit cobol=$crc java=$jrc"; fail=1; continue; fi
  if [[ $crc -ne 0 ]]; then echo "ok   $name (both abend, exit $crc)"; continue; fi
  ok=1
  for f in TRANSACT ACCTFILE SYSOUT RC; do
    cmp -s "$tmp/$name/cobol/$f.txt" "$tmp/$name/java/$f.txt" || { echo "FAIL $name: $f.txt differs"; ok=0; fail=1; }
  done
  [[ $ok -eq 1 ]] && echo "ok   $name ($(wc -l <"$tmp/$name/java/TRANSACT.txt") tx, $(wc -l <"$tmp/$name/java/ACCTFILE.txt") accts)"
done
exit $fail
