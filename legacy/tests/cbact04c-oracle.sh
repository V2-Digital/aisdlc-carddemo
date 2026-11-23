#!/usr/bin/env bash
# Regenerates the CBACT04C oracle cases: inputs are built into
# legacy/tests/cbact04c/<case>/input, then the original COBOL is run on them
# (legacy/harness/run.sh + INPUT_DIR) and its outputs land in <case>/expected.
# Abend cases (RC 16): run.sh exports no datasets, so only SYSOUT and RC are kept.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
A=$ROOT/app/data/ASCII
C=$ROOT/legacy/tests/cbact04c
rm -rf "$C"

acct() { sed -n "${1}p" "$A/acctdata.txt"; }                 # stock account line N
tcat() { printf '%-11s%-2s%-4s%-11s%-22s\n' "$1" "$2" "$3" "$4" ""; }  # TCATBALF record
xref() { printf '%-16s%-9s%-11s%-14s\n' "$1" "000000001" "$2" ""; }     # XREFFILE record
disc() { printf '%-10s%-2s%-4s%-6s%-28s\n' "$1" "$2" "$3" "$4" ""; }    # DISCGRP record
setgroup() { sed "s/^\(.\{112\}\)          /\1$1/"; }                  # ACCT-GROUP-ID, cols 113-122
mk() { mkdir -p "$C/$1/input"; echo "$C/$1/input"; }

d=$(mk negative)      # -0.00 and negative truncation, plain positive; acct 4 is last (not rewritten)
for n in 1 2 3 4; do acct $n; done >"$d/ACCTFILE.txt"
{ tcat 00000000001 01 0001 0000000000J     # -0.01  -> -0.00
  tcat 00000000002 01 0001 0000012345K     # -1234.52
  tcat 00000000003 01 0001 0000000010A     # 1.01
  tcat 00000000004 01 0001 0000000100{; } >"$d/TCATBALF.txt"

# multicard (several cards per account) is not representable: the AIX has no WITH DUPLICATES,
# so the harness load fails with file status 22 (R2). Java takes the first card by key.

d=$(mk group-found)   # acct 1 carries group GRP1 (rate 18.00); acct 2 falls back to DEFAULT
{ acct 1 | setgroup GRP1; acct 2; } >"$d/ACCTFILE.txt"
{ disc GRP1 01 0001 00180{; disc DEFAULT 01 0001 00150{; } >"$d/DISCGRP.txt"
{ tcat 00000000001 01 0001 0000100000{; tcat 00000000002 01 0001 0000100000{; } >"$d/TCATBALF.txt"

d=$(mk overflow)      # 999999999.99 * 9999.99 / 1200 does not fit S9(9)V99
for n in 1 2; do acct $n; done >"$d/ACCTFILE.txt"
{ disc DEFAULT 01 0001 99999I; } >"$d/DISCGRP.txt"
{ tcat 00000000001 01 0001 9999999999I; tcat 00000000002 01 0001 0000000100{; } >"$d/TCATBALF.txt"

d=$(mk abend-acct)    # account 99999999999 is not in ACCTFILE
{ tcat 00000000001 01 0001 0000010000{; tcat 99999999999 01 0001 0000010000{; } >"$d/TCATBALF.txt"

d=$(mk abend-xref)    # acct 3 has no card
{ xref 0500024453765740 00000000001; } >"$d/XREFFILE.txt"
for n in 1 2 3; do acct $n; done >"$d/ACCTFILE.txt"
{ tcat 00000000001 01 0001 0000010000{; tcat 00000000003 01 0001 0000010000{; } >"$d/TCATBALF.txt"

d=$(mk abend-default) # no DEFAULT row for type/cat 05/0009
{ tcat 00000000001 05 0009 0000010000{; } >"$d/TCATBALF.txt"

d=$(mk empty)
: >"$d/TCATBALF.txt"

for d in "$C"/*/; do
  c=$(basename "$d")
  INPUT_DIR=$d/input "$ROOT/legacy/harness/run.sh" CBACT04C "$d/expected" >/dev/null 2>&1
  echo "$c: RC=$(cat "$d/expected/RC.txt")"
done
