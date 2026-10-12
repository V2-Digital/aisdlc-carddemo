#!/usr/bin/env bash
# run.sh <IN_DIR> <OUT_DIR>: inputs DALYTRAN/XREFFILE/ACCTFILE/TCATBALF .txt -> outputs in OUT_DIR
set -euo pipefail
IN=${1:?in dir}; OUT=${2:?out dir}
mkdir -p "$OUT"
cp "$IN/ACCTFILE.txt" "$OUT/ACCTFILE.txt"; cp "$IN/TCATBALF.txt" "$OUT/TCATBALF.txt"
export DD_DALYTRAN=$IN/DALYTRAN.txt DD_XREFFILE=$IN/XREFFILE.txt
export DD_ACCTFILE=$OUT/ACCTFILE.txt DD_TCATBALF=$OUT/TCATBALF.txt
export DD_TRANFILE=$OUT/TRANFILE.txt DD_DALYREJS=$OUT/DALYREJS.txt
exec java -jar "$(dirname "$0")/target/cbtrn02c.jar"
