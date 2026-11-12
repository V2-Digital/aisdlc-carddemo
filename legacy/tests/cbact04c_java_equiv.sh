#!/usr/bin/env bash
# Cross-check: the Java rewrite (modernized/cbact04c) vs the real COBOL via the
# harness, on identical inputs. Diffs TRANSACT, ACCTFILE, SYSOUT, RC.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"
TESTS=legacy/tests
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
(cd modernized/cbact04c && mvn -q -DskipTests package) || { echo "FAIL: mvn package"; exit 1; }
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
fail=0

# name:input_dir  (an input dir holds <DD>.txt files; missing DDs use the stock sample)
mkdir -p "$W/in_after"
cp legacy/golden/CBTRN02C/ACCTFILE.txt legacy/golden/CBTRN02C/TCATBALF.txt "$W/in_after/"
mkdir -p "$W/in_stock"
SCEN=(stock:"$W/in_stock" after:"$W/in_after" edge:"$ROOT/$TESTS/cbact04c_java_equiv" missing:"$ROOT/$TESTS/cbact04c_java_equiv_missing")

for s in "${SCEN[@]}"; do
  name=${s%%:*}; in=${s#*:}
  cob=$W/$name.cob; jav=$W/$name.java; mkdir -p "$cob" "$jav"
  INPUT_DIR=$in legacy/harness/run.sh CBACT04C "$cob" >/dev/null 2>&1
  crc=$?
  # the harness does not export outputs after an abend; it still writes SYSOUT/RC
  # Java side: same inputs, sample data for DDs not in INPUT_DIR
  args=()
  for dd in TCATBALF:tcatbal XREFFILE:cardxref ACCTFILE:acctdata DISCGRP:discgrp; do
    d=${dd%%:*}; f=${dd##*:}
    src=app/data/ASCII/$f.txt; [[ -f $in/$d.txt ]] && src=$in/$d.txt
    tr -d '\r' <"$src" >"$jav/in_$d.txt"
    args+=("$d=$jav/in_$d.txt")
  done
  # ACCTFILE is rewritten in place; give Java its own output path
  cp "$jav/in_ACCTFILE.txt" "$jav/ACCTFILE.txt"
  args=("${args[@]/ACCTFILE=*/ACCTFILE=$jav/ACCTFILE.txt}")
  CARDDEMO_CURRENT_DATE=${CARDDEMO_CURRENT_DATE:-2022071800000000} java -jar "$JAR" 2022071800 --dd "${args[@]}" "TRANSACT=$jav/TRANSACT.txt" --sysout "$jav/SYSOUT.txt" 2>/dev/null
  echo $? >"$jav/RC.txt"
  files=(SYSOUT RC)
  [[ $crc -eq 0 ]] && files+=(TRANSACT ACCTFILE)
  for f in "${files[@]}"; do
    if cmp -s "$cob/$f.txt" "$jav/$f.txt"; then :; else echo "FAIL [$name] $f differs"; diff "$cob/$f.txt" "$jav/$f.txt" | head -5; fail=1; fi
  done
  echo "scenario $name: COBOL rc=$(cat "$cob/RC.txt") Java rc=$(cat "$jav/RC.txt") compared: ${files[*]}"
done
[[ $fail -eq 0 ]] && echo "PASS: Java == COBOL on all scenarios" || { echo "FAILED"; exit 1; }
