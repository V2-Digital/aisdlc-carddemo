#!/usr/bin/env bash
# Produce (default) or check (--check) the golden outputs in legacy/golden/.
#
# Every scenario runs twice in fresh work dirs; the two runs must be
# byte-identical, otherwise the harness is not deterministic and we fail.
#
# Scenarios:
#   CBTRN02C                 POSTTRAN on the stock sample data
#   CBACT04C                 INTCALC on the stock sample data
#   CBACT04C-after-CBTRN02C  INTCALC on ACCTFILE/TCATBALF as left by
#                            POSTTRAN (the nightly order); the stock
#                            TCATBAL balances are all zero, so this is the
#                            scenario where interest is actually non-zero
set -euo pipefail

HARNESS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
LEGACY_DIR=$(dirname "$HARNESS_DIR")
GOLDEN_DIR=$LEGACY_DIR/golden
MODE=${1:-write}

run_all() { # <out-root>
  local root=$1
  "$HARNESS_DIR/run.sh" CBTRN02C "$root/CBTRN02C"
  "$HARNESS_DIR/run.sh" CBACT04C "$root/CBACT04C"
  INPUT_DIR=$root/CBTRN02C \
    "$HARNESS_DIR/run.sh" CBACT04C "$root/CBACT04C-after-CBTRN02C"
}

TMP=$(mktemp -d "${TMPDIR:-/tmp}/carddemo-golden.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
run_all "$TMP/run1"
run_all "$TMP/run2"
if ! diff -r "$TMP/run1" "$TMP/run2"; then
  echo "golden.sh: FAIL - two runs differ (non-deterministic)" >&2
  exit 1
fi
echo "golden.sh: two runs byte-identical" >&2

case $MODE in
  --check)
    if diff -r "$GOLDEN_DIR" "$TMP/run1"; then
      echo "golden.sh: OK - outputs match legacy/golden" >&2
    else
      echo "golden.sh: FAIL - outputs differ from legacy/golden" >&2
      exit 1
    fi
    ;;
  write)
    rm -rf "$GOLDEN_DIR"
    cp -r "$TMP/run1" "$GOLDEN_DIR"
    echo "golden.sh: wrote $GOLDEN_DIR" >&2
    ;;
  *)
    echo "usage: golden.sh [--check]" >&2
    exit 2
    ;;
esac
