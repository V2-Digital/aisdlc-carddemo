#!/usr/bin/env bash
# Run one CardDemo batch program under GnuCOBOL against fresh data.
#
#   harness/run.sh <PROGRAM> [OUT_DIR]
#
# 1. compiles the program (+ harness modules) into a fresh work dir
# 2. loads the ASCII sample data into the INDEXED/SEQUENTIAL files the
#    program expects (DD names as in app/jcl), CRLF normalised
# 3. runs it via pgmdrv with the JCL PARM and a frozen clock
# 4. unloads every output dataset to OUT_DIR/<DD>.txt (fixed-width lines),
#    plus SYSOUT.txt (DISPLAY output) and RC.txt (exit status)
#
# Environment:
#   CARDDEMO_HOME  CardDemo checkout (default: legacy/.. if it has app/cbl,
#                  else legacy/.carddemo)
#   INPUT_DIR      optional dir of <DD>.txt files that replace the sample
#                  data for those DDs (e.g. a previous program's outputs)
#   CARDDEMO_CURRENT_DATE  frozen FUNCTION CURRENT-DATE, YYYYMMDDHHMMSSss
#                  (default 2022071800000000, matching INTCALC's PARM)
#   KEEP_WORK=1    keep the work dir for inspection
set -euo pipefail

PROGRAM=${1:?usage: run.sh <CBACT04C|CBTRN02C> [OUT_DIR]}
HARNESS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LEGACY_DIR=$(dirname "$HARNESS_DIR")
OUT_DIR=${2:-$LEGACY_DIR/out/$PROGRAM}

if [[ -z ${CARDDEMO_HOME:-} ]]; then
  if [[ -d $LEGACY_DIR/../app/cbl ]]; then
    CARDDEMO_HOME=$LEGACY_DIR/..
  elif [[ -d $LEGACY_DIR/.carddemo/app/cbl ]]; then
    CARDDEMO_HOME=$LEGACY_DIR/.carddemo
  else
    echo "run.sh: CardDemo sources not found; set CARDDEMO_HOME" >&2
    exit 2
  fi
fi
CARDDEMO_HOME=$(cd "$CARDDEMO_HOME" && pwd)
if [[ -n ${INPUT_DIR:-} && ! -d $INPUT_DIR ]]; then
  echo "run.sh: INPUT_DIR '$INPUT_DIR' is not a directory" >&2
  exit 2
fi
SAMPLE_DIR=$CARDDEMO_HOME/app/data/ASCII

# Dataset specs mirror the JCL. INPUTS: DD:CDFILES-TYPE:sample-file,
# OUTPUTS: DD:CDFILES-TYPE (exported after the run, in this order).
case $PROGRAM in
  CBACT04C) # app/jcl/INTCALC.jcl
    PARM=2022071800
    INPUTS=(TCATBALF:TCATBALF:tcatbal.txt XREFFILE:XREFFILE:cardxref.txt
            ACCTFILE:ACCTFILE:acctdata.txt DISCGRP:DISCGRP:discgrp.txt)
    OUTPUTS=(TRANSACT:SEQ350 ACCTFILE:ACCTFILE)
    ;;
  CBTRN02C) # app/jcl/POSTTRAN.jcl
    PARM=
    INPUTS=(DALYTRAN:SEQ350:dailytran.txt XREFFILE:XREFFILE:cardxref.txt
            ACCTFILE:ACCTFILE:acctdata.txt TCATBALF:TCATBALF:tcatbal.txt)
    OUTPUTS=(TRANFILE:TRANFILE DALYREJS:SEQ430 ACCTFILE:ACCTFILE
             TCATBALF:TCATBALF)
    ;;
  *)
    echo "run.sh: unknown program '$PROGRAM' (CBACT04C|CBTRN02C)" >&2
    exit 2
    ;;
esac

# -fsign=EBCDIC: signed zoned decimals use the mainframe overpunch
# characters ('{' 'A'-'I' '}' 'J'-'R') found in the ASCII sample data.
# -fassign-clause=ibm: ASSIGN TO <ddname> is an external name, resolved
# at runtime from DD_<ddname>.
COBC_FLAGS=(-fsign=EBCDIC -fassign-clause=ibm)

WORK=$(mktemp -d "${TMPDIR:-/tmp}/carddemo-$PROGRAM.XXXXXX")
if [[ ${KEEP_WORK:-0} == 1 ]]; then
  echo "run.sh: work dir $WORK" >&2
else
  trap 'rm -rf "$WORK"' EXIT
fi
mkdir -p "$WORK/bin" "$WORK/data" "$WORK/text" "$OUT_DIR"
rm -f "$OUT_DIR"/*.txt

export TZ=UTC
export COB_LIBRARY_PATH=$WORK/bin

# --- compile ---------------------------------------------------------------
cobc -x "${COBC_FLAGS[@]}" -o "$WORK/bin/cdfiles" "$HARNESS_DIR/cdfiles.cbl"
cobc -x "${COBC_FLAGS[@]}" -o "$WORK/bin/pgmdrv" "$HARNESS_DIR/pgmdrv.cbl"
cobc -m "${COBC_FLAGS[@]}" -o "$WORK/bin/CEE3ABD.so" "$HARNESS_DIR/cee3abd.cbl"
cobc -m "${COBC_FLAGS[@]}" -I "$CARDDEMO_HOME/app/cpy" \
  -o "$WORK/bin/$PROGRAM.so" "$CARDDEMO_HOME/app/cbl/$PROGRAM.cbl"

# --- load ------------------------------------------------------------------
for spec in "${INPUTS[@]}"; do
  IFS=: read -r dd type sample <<<"$spec"
  src=$SAMPLE_DIR/$sample
  if [[ -n ${INPUT_DIR:-} && -f $INPUT_DIR/$dd.txt ]]; then
    src=$INPUT_DIR/$dd.txt
  fi
  tr -d '\r' <"$src" >"$WORK/text/$dd.txt"
  env DD_TEXTFILE="$WORK/text/$dd.txt" "DD_$type=$WORK/data/$dd" \
    "$WORK/bin/cdfiles" LOAD "$type" 2>>"$WORK/load.log" || {
      cat "$WORK/load.log" >&2; exit 3; }
done

# --- run -------------------------------------------------------------------
dd_env=()
for spec in "${INPUTS[@]}" "${OUTPUTS[@]}"; do
  dd=${spec%%:*}
  dd_env+=("DD_$dd=$WORK/data/$dd")
done
rc=0
(cd "$WORK" && env "${dd_env[@]}" \
   COB_CURRENT_DATE="${CARDDEMO_CURRENT_DATE:-2022071800000000}" \
   "$WORK/bin/pgmdrv" "$PROGRAM" "$PARM") \
  >"$OUT_DIR/SYSOUT.txt" 2>"$WORK/syserr.log" || rc=$?
echo "$rc" >"$OUT_DIR/RC.txt"
# libcob on Debian prints a harmless libxml2 version warning to stderr.
grep -v '^Warning: program compiled against libxml' "$WORK/syserr.log" >&2 || true
if (( rc != 0 && rc != 4 )); then
  echo "run.sh: $PROGRAM ended with RC=$rc (abend?); outputs not exported" >&2
  exit "$rc"
fi

# --- unload ----------------------------------------------------------------
for spec in "${OUTPUTS[@]}"; do
  IFS=: read -r dd type <<<"$spec"
  env COB_LS_FIXED=TRUE DD_TEXTFILE="$OUT_DIR/$dd.txt" \
    "DD_$type=$WORK/data/$dd" \
    "$WORK/bin/cdfiles" UNLOAD "$type" 2>>"$WORK/unload.log" || {
      cat "$WORK/unload.log" >&2; exit 3; }
done

echo "run.sh: $PROGRAM RC=$rc -> $OUT_DIR" >&2
for f in "$OUT_DIR"/*.txt; do
  printf '  %-12s %6d records\n' "$(basename "$f")" "$(wc -l <"$f")" >&2
done
