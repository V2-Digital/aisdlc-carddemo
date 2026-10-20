#!/usr/bin/env bash
# CR-3: COBOL CBTRN02C reference for paths the golden scenario misses
# (reasons 100/101/102/103, 103-over-102, no rejects, duplicate TRAN-ID abend, PIC overflow).
# Runs the unmodified COBOL on fixtures and checks it against the references the Java tests use.
# If modernized/cbtrn02c/target/cbtrn02c.jar exists, the Java program is also compared.
set -uo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
FIX=$ROOT/legacy/tests/cbtrn02c_reasons
REF=$ROOT/modernized/cbtrn02c/src/test/resources/reasons
JAR=$ROOT/modernized/cbtrn02c/target/cbtrn02c.jar
OUT=${1:-$(mktemp -d)}
fail=0
bad() { echo "FAIL: $*"; fail=1; }

for c in reasons norejects overflow overflow_acct dupid; do
  d=$OUT/cobol/$c; mkdir -p "$d"
  INPUT_DIR=$FIX/$c "$ROOT/legacy/harness/run.sh" CBTRN02C "$d" >/dev/null 2>&1
  rc=$?
  if [[ $c == dupid ]]; then
    [[ $rc == 16 ]] && echo "ok $c: abend RC 16" || bad "$c: expected RC 16, got $rc"
  else
    for f in TRANFILE DALYREJS ACCTFILE TCATBALF SYSOUT RC; do
      cmp -s "$d/$f.txt" "$REF/$c/$f.txt" && echo "ok $c/$f" || bad "$c/$f differs from reference"
    done
  fi
done

# expected reject reasons, in order, for the reasons case
got=$(cut -c351-354 "$OUT/cobol/reasons/DALYREJS.txt" | tr '\n' ' ')
[[ $got == "0100 0101 0102 0103 0103 0103 " ]] && echo "ok reasons: $got" || bad "reject reasons: $got"

if [[ -f $JAR ]]; then
  for c in reasons norejects overflow overflow_acct dupid; do
    d=$OUT/java/$c; mkdir -p "$d"
    for f in DALYTRAN XREFFILE ACCTFILE TCATBALF; do cp "$FIX/$c/$f.txt" "$d/in_$f.txt"; done
    rc=0
    env CARDDEMO_CURRENT_DATE=2022071800000000 \
      DD_DALYTRAN=$d/in_DALYTRAN.txt DD_XREFFILE=$d/in_XREFFILE.txt \
      DD_ACCTFILE=$d/in_ACCTFILE.txt DD_TCATBALF=$d/in_TCATBALF.txt \
      DD_TRANFILE=$d/TRANFILE.txt DD_DALYREJS=$d/DALYREJS.txt \
      java -jar "$JAR" >"$d/SYSOUT.txt" 2>/dev/null || rc=$?
    exp=$(cat "$d/../../cobol/$c/RC.txt" 2>/dev/null || echo 16)
    [[ $rc == "$exp" ]] || bad "java $c: RC $rc, COBOL $exp"
    [[ $c == dupid ]] && continue
    cmp -s "$d/SYSOUT.txt" "$OUT/cobol/$c/SYSOUT.txt" || bad "java $c SYSOUT"
    cmp -s "$d/TRANFILE.txt" "$OUT/cobol/$c/TRANFILE.txt" || bad "java $c TRANFILE"
    cmp -s "$d/DALYREJS.txt" "$OUT/cobol/$c/DALYREJS.txt" || bad "java $c DALYREJS"
    cmp -s "$d/in_ACCTFILE.txt" "$OUT/cobol/$c/ACCTFILE.txt" || bad "java $c ACCTFILE"
    cmp -s "$d/in_TCATBALF.txt" "$OUT/cobol/$c/TCATBALF.txt" || bad "java $c TCATBALF"
  done
fi
exit $fail
