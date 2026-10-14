#!/usr/bin/env bash
# CBACT04C abend path: ACCTFILE lacks account 00000000025 (fixture in cbact04c_abend/).
# Pins the real COBOL behaviour (RC 16, SYSOUT) and checks the Java rewrite prints the same SYSOUT.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbact04c_abend
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

# 1. COBOL (harness exports no outputs on abend, so only SYSOUT/RC are available)
INPUT_DIR=$FIX "$ROOT/legacy/harness/run.sh" CBACT04C "$W/cobol" >/dev/null 2>"$W/cobol.err"
[[ $(cat "$W/cobol/RC.txt" 2>/dev/null) == 16 ]] || { cat "$W/cobol.err" >&2; fail "COBOL RC is not 16"; }
tail -4 "$W/cobol/SYSOUT.txt" > "$W/tail.txt"
printf 'ACCOUNT NOT FOUND: 00000000025\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n' > "$W/expect.txt"
diff "$W/expect.txt" "$W/tail.txt" || fail "COBOL abend SYSOUT changed"

# 2. Java rewrite gives the same SYSOUT and RC
A=$ROOT/app/data/ASCII
mkdir -p "$W/classes" && javac --release 21 -d "$W/classes" "$ROOT"/modernized/cbact04c/src/main/java/carddemo/cbact04c/*.java || fail "javac"
cp "$FIX/ACCTFILE.txt" "$W/ACCTFILE.txt"
DD_TCATBALF=$A/tcatbal.txt DD_XREFFILE=$A/cardxref.txt DD_DISCGRP=$A/discgrp.txt DD_ACCTFILE=$W/ACCTFILE.txt \
  DD_TRANSACT=$W/TRANSACT.txt CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -cp "$W/classes" carddemo.cbact04c.Cbact04c 2022071800 > "$W/java.out" 2>/dev/null
[[ $? == 16 ]] || fail "Java RC is not 16"
diff "$W/cobol/SYSOUT.txt" "$W/java.out" || fail "Java SYSOUT differs from COBOL on abend"
echo "PASS cbact04c_abend"
