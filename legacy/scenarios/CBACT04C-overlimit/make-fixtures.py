"""Build input/ and expected/ from the golden files + spec s6 hand arithmetic.
Expected output is NOT taken from the new program's output."""
import sys
root = sys.argv[1]            # repo root
G = root + "/legacy/golden/"
S = root + "/legacy/scenarios/CBACT04C-overlimit/"
rd = lambda p: open(p, newline="").read().split("\n")[:-1]
def wr(p, lines): open(p, "w", newline="").write("".join(l + "\n" for l in lines))
acct = rd(G + "CBTRN02C/ACCTFILE.txt")
def edit(l, a, b, new): return l[:a-1] + new + l[b:]
# input edits (1-based cols): limit 25-36, group 113-122
acct[0] = edit(acct[0], 25, 36, "00000010000{")
acct[1] = edit(acct[1], 25, 36, "00000017349G")
acct[2] = edit(acct[2], 25, 36, "00000015000{")
acct[2] = edit(acct[2], 113, 122, "ZEROAPR   ")
wr(S + "input/ACCTFILE.txt", acct)
wr(S + "input/TCATBALF.txt", rd(G + "CBTRN02C/TCATBALF.txt"))
# expected ACCTFILE: golden after-run, plus edits; a/c1 1302.66+25.00, a/c3 1857.65+25.00 (no interest)
g = rd(G + "CBACT04C-after-CBTRN02C/ACCTFILE.txt")
exp = list(g)
exp[0] = edit(exp[0], 13, 24, "00000013276F"); exp[0] = edit(exp[0], 25, 36, "00000010000{")
exp[1] = edit(exp[1], 25, 36, "00000017349G")
exp[2] = edit(exp[2], 13, 24, "00000018826E"); exp[2] = edit(exp[2], 25, 36, "00000015000{")
exp[2] = edit(exp[2], 113, 122, "ZEROAPR   ")
# last account (50) is rewritten-not (D1): golden keeps original there; same as golden
wr(S + "expected/ACCTFILE.txt", exp)
# expected TRANSACT: golden has exactly one interest line per account (line k = a/c k).
# Order: fee a/c1, int a/c1, int a/c2, fee a/c3 (a/c3 interest gone: ZEROAPR), int a/c4..50
gt = rd(G + "CBACT04C-after-CBTRN02C/TRANSACT.txt")
xref = {l[25:36]: l[:16] for l in rd(root + "/app/data/ASCII/cardxref.txt")}
def fee(a, n):
    d = ("Over-limit fee for a/c %011d" % a).ljust(100)
    r = ("2022071800%06d" % n) + "010006" + "System".ljust(10) + d + "0000000250{" + "000000000" + " " * 110 \
        + xref["%011d" % a] + "2022-07-18-00.00.00.000000" * 2 + " " * 20
    assert len(r) == 350, len(r)
    return r
def renum(rec, n): return rec[:10] + "%06d" % n + rec[16:]
out = [fee(1, 1), renum(gt[0], 2), renum(gt[1], 3), fee(3, 4)]
out += [renum(r, i + 5) for i, r in enumerate(gt[3:])]
assert len(out) == 51
wr(S + "expected/TRANSACT.txt", out)
# SYSOUT: golden minus the 4 DEFAULT-fallback lines of a/c3 (2 categories x 2 lines)
so = rd(G + "CBACT04C-after-CBTRN02C/SYSOUT.txt")
drop = []
for i, l in enumerate(so):
    if l.startswith("00000000003"): drop += [i + 1, i + 2]
so = [l for i, l in enumerate(so) if i not in drop]
wr(S + "expected/SYSOUT.txt", so)
wr(S + "expected/RC.txt", rd(G + "CBACT04C-after-CBTRN02C/RC.txt"))
