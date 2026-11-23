#!/usr/bin/env bash
# D7 probe: CBACT04C with two xref cards for one account (acct 00000000001).
#   legacy/tests/xref_dup.sh
# Result (GnuCOBOL 3.2/BDB): the harness cannot represent the case. cdfiles
# declares the XREFFILE alternate key without DUPLICATES, so LOAD of the second
# card fails with file status 22 and run.sh exits 3 before CBACT04C starts.
# So D7 is decided from the source: on z/OS a NONUNIQUEKEY AIX read returns
# status 02 for a duplicate, and CBACT04C.cbl:400 treats anything but '00' as
# an error -> ERROR READING XREF FILE, FILE STATUS IS: NNNN0002, abend.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUT=$(mktemp -d)
IN=$(mktemp -d)
trap 'rm -rf "$IN" "$OUT"' EXIT
tr -d '\r' < "$ROOT/app/data/ASCII/cardxref.txt" > "$IN/XREFFILE.txt"
[[ -n $(tail -c1 "$IN/XREFFILE.txt") ]] && echo >> "$IN/XREFFILE.txt"
# second card for the account that already owns card on line 1
acct=$(head -1 "$IN/XREFFILE.txt" | cut -c26-36)
printf '9999999999999999%9s%s%14s\n' '' "$acct" '' >> "$IN/XREFFILE.txt"
INPUT_DIR=$IN "$ROOT/legacy/harness/run.sh" CBACT04C "$OUT" >/dev/null 2>"$OUT/err.log"
echo "run.sh exit: $?"
grep -m1 "file status" "$OUT/err.log"
# pin the harness limitation: if it ever loads duplicates, D7 must be re-examined
grep -q "file status 22" "$OUT/err.log" || { echo "FAIL: harness now accepts duplicate xref cards; re-decide D7"; exit 1; }
