"""Builds crafted CBACT04C input datasets: gen.py <case> <dir>."""
import sys
from decimal import Decimal

POS, NEG = "{ABCDEFGHI", "}JKLMNOPQR"


def z(v, w):
    u = int(Decimal(v) * 100)
    d = str(abs(u)).rjust(w, "0")[-w:]
    return d[:-1] + (NEG if u < 0 else POS)[int(d[-1])]


def pad(s, n):
    return s.ljust(n)


def tcat(a, t, c, b): return pad(f"{a:011d}{t}{c}{z(b, 11)}", 50)
def acct(a, bal, cr, db, grp=""):
    return pad(f"{a:011d}Y{z(bal, 12)}" + " " * 54 + z(cr, 12) + z(db, 12) + pad(grp, 10), 300)
def xref(a): return pad(f"4{a:011d}0000{a:09d}{a:011d}", 50)
def disc(g, t, c, r): return pad(pad(g, 10) + t + c + z(r, 6), 50)


def case(name):
    A = list(range(1, 10))
    accts = [acct(a, "100.00", "5.00", "-2.00", "GRP" if a == 2 else "") for a in A]
    xr = [xref(a) for a in A]
    ds = [disc("DEFAULT", "01", "0001", "15.00"), disc("DEFAULT", "03", "0001", "0"),
          disc("DEFAULT", "02", "0001", "1.00"), disc("GRP", "01", "0001", "12.00")]
    tc = [tcat(1, "01", "0001", "-100.00"), tcat(1, "03", "0001", "50.00"),
          tcat(2, "01", "0001", "1164.87"),
          tcat(3, "01", "0001", "-0.01"), tcat(3, "02", "0001", "0.79"),
          tcat(4, "01", "0001", "0"),
          tcat(5, "01", "0001", "999999999.99"),
          tcat(6, "01", "0001", "-1164.87"), tcat(7, "01", "0001", "0.01"),
          tcat(8, "01", "0001", "10.00")]
    if name == "missing-default":
        ds = ds[:1] + ds[3:]
        tc = [tcat(1, "02", "0001", "5.00")]
        ds = [d for d in ds if d[10:12] != "02"]
        ds = [disc("DEFAULT", "01", "0001", "15.00")]
    elif name == "missing-account":
        accts = [a for a in accts if not a.startswith("00000000003")]
    elif name == "missing-xref":
        xr = [x for x in xr if x[25:36] != "00000000003"]
    elif name == "single-account":
        tc = [tcat(1, "01", "0001", "1164.87")]
    elif name == "empty":
        tc = []
    return {"TCATBALF": tc, "XREFFILE": sorted(xr), "DISCGRP": sorted(ds), "ACCTFILE": accts}


name, out = sys.argv[1], sys.argv[2]
for dd, recs in case(name).items():
    with open(f"{out}/{dd}.txt", "w", newline="\n", encoding="latin-1") as f:
        f.write("".join(r + "\n" for r in recs))
