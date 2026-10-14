#!/usr/bin/env bash
# Runs COBOL CBACT04C (harness) and the Java rewrite on the same inputs; diffs outputs.
#   legacy/tests/cbact04c-java-equiv.sh
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT
(cd "$ROOT/modernized/cbact04c" && mvn -q package -DskipTests)
JAR=$ROOT/modernized/cbact04c/target/cbact04c.jar
export CARDDEMO_CURRENT_DATE=2022071800000000

check() { # name input_dir_or_empty
  local name=$1 in=$2 cob=$WORK/$1/cobol java=$WORK/$1/java stage=$WORK/$1/in
  mkdir -p "$stage"
  local s=$ROOT/app/data/ASCII
  cp "$s/tcatbal.txt" "$stage/TCATBALF.txt"; cp "$s/cardxref.txt" "$stage/XREFFILE.txt"
  cp "$s/acctdata.txt" "$stage/ACCTFILE.txt"; cp "$s/discgrp.txt" "$stage/DISCGRP.txt"
  if [[ -n $in ]]; then cp "$in"/*.txt "$stage/"; rm -f "$stage"/{RC,SYSOUT,TRANFILE,DALYREJS}.txt; fi
  INPUT_DIR=$stage "$ROOT/legacy/harness/run.sh" CBACT04C "$cob" 2>/dev/null
  java -jar "$JAR" 2022071800 "$stage" "$java" >/dev/null
  for f in TRANSACT ACCTFILE SYSOUT; do
    cmp "$cob/$f.txt" "$java/$f.txt" && echo "$name $f: identical"
  done
}
check stock ""
check after-CBTRN02C "$ROOT/legacy/golden/CBTRN02C"
