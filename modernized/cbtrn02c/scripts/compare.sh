#!/usr/bin/env bash
# Run the jar on stock data, diff with golden CBTRN02C, then feed the result to CBACT04C (AC 7).
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
JAR=$ROOT/modernized/cbtrn02c/target/cbtrn02c.jar
S=$ROOT/app/data/ASCII
OUT=${1:-$(mktemp -d)}
mkdir -p "$OUT/java" "$OUT/in"
tr -d '\r' <"$S/dailytran.txt" >"$OUT/in/DALYTRAN.txt"
tr -d '\r' <"$S/cardxref.txt" >"$OUT/in/XREFFILE.txt"
cp "$S/acctdata.txt" "$OUT/in/ACCTFILE.txt"
tr -d '\r' <"$S/tcatbal.txt" >"$OUT/in/TCATBALF.txt"
rc=0
env CARDDEMO_CURRENT_DATE=2022071800000000 \
  DD_DALYTRAN="$OUT/in/DALYTRAN.txt" DD_XREFFILE="$OUT/in/XREFFILE.txt" \
  DD_ACCTFILE="$OUT/in/ACCTFILE.txt" DD_TCATBALF="$OUT/in/TCATBALF.txt" \
  DD_TRANFILE="$OUT/java/TRANFILE.txt" DD_DALYREJS="$OUT/java/DALYREJS.txt" \
  java -jar "$JAR" >"$OUT/java/SYSOUT.txt" || rc=$?
echo $rc >"$OUT/java/RC.txt"
# ACCTFILE/TCATBALF are updated in place (I-O), so the inputs now hold the outputs
cp "$OUT/in/ACCTFILE.txt" "$OUT/java/ACCTFILE.txt"; cp "$OUT/in/TCATBALF.txt" "$OUT/java/TCATBALF.txt"
G=$ROOT/legacy/golden
for f in TRANFILE DALYREJS ACCTFILE TCATBALF SYSOUT RC; do
  cmp "$OUT/java/$f.txt" "$G/CBTRN02C/$f.txt"; echo "ok $f"
done
mkdir -p "$OUT/chain"
INPUT_DIR=$OUT/in "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT/chain"
for f in TRANSACT ACCTFILE SYSOUT RC; do
  cmp "$OUT/chain/$f.txt" "$G/CBACT04C-after-CBTRN02C/$f.txt"; echo "ok chain $f"
done
