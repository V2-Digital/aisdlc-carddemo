"""Regenerates the fixtures in this directory (python3 -I gen_fixtures.py). Fixed-width, LF, overpunch signs."""
import os
base = os.path.dirname(os.path.abspath(__file__))
def sg(v, n):
    d = str(abs(v)).zfill(n)[-n:]
    return d[:-1] + ("{ABCDEFGHI"[int(d[-1])] if v >= 0 else "}JKLMNOPQR"[int(d[-1])])
def txn(i, amt, card, typ="01", cat="0001", ts="2022-06-10"):
    r = (f"{i:<16}" + typ + cat).ljust(132) + sg(amt, 11)
    r = r.ljust(262) + f"{card:<16}"
    return (r.ljust(278) + ts + " 19:27:53.000000").ljust(350)
def xref(card, acct): return (card + " " * 9 + acct).ljust(50)
def acct(i, bal, lim, cr, db, exp):
    r = (i + "Y" + sg(bal, 12) + sg(lim, 12)).ljust(58) + f"{exp:<10}"
    return (r.ljust(78) + sg(cr, 12) + sg(db, 12)).ljust(300)
def tc(a, t, c, bal, fill): return (a + t + c + sg(bal, 11) + fill).ljust(50)
A1 = '00000000001'
C1, C2 = '4000000000000001', '4000000000000002'
ok = acct(A1, 0, 100000, 0, 0, "2030-01-01")
cases = {
 'c100_unknown_card': ([txn('T1', 500, '9999999999999999')], [xref(C1, A1)], [ok], []),
 'c101_missing_account': ([txn('T1', 500, C2)], [xref(C1, A1), xref(C2, '00000000099')], [ok], []),
 'c102_boundary': ([txn('T1', 100000, C1), txn('T2', 1, C1)], [xref(C1, A1)], [ok], []),
 'c103_over_102': ([txn('T1', 999999, C1), txn('T2', 100, C1)], [xref(C1, A1)], [acct(A1, 0, 100000, 0, 0, "2022-01-01")], []),
 'c103_blank_expiry': ([txn('T1', 100, C1)], [xref(C1, A1)], [acct(A1, 0, 100000, 0, 0, "")], []),
 'c_zero_and_negative': ([txn('T1', 0, C1), txn('T2', -1500, C1), txn('T3', -1500, C1, "02", "0002")], [xref(C1, A1)], [ok], []),
 'c_zero_result_sign': ([txn('T1', -500, C1), txn('T2', 500, C1)], [xref(C1, A1)], [ok], []),
 'c_first_create_filler': ([txn('T1', 100, C1)], [xref(C1, A1)], [ok], []),
 'c_create_after_read_filler': ([txn('T1', 100, C1), txn('T2', 100, C1, "02", "0002"), txn('T3', 100, C1, "03", "0003")], [xref(C1, A1)], [ok], [tc(A1, '01', '0001', 1000, 'Z' * 22)]),
 'c_update_existing': ([txn('T1', 100, C1)], [xref(C1, A1)], [ok], [tc(A1, '01', '0001', 1000, '0' * 22)]),
 'c_no_rejects_rc0': ([txn('T1', 100, C1), txn('T2', 200, C1)], [xref(C1, A1)], [ok], []),
 'c_empty_dailytran': ([], [xref(C1, A1)], [ok], []),
 'c_overflow_balance': ([txn('T1', 2, C1)], [xref(C1, A1)], [acct(A1, 999999999999, 100000, 999999999999, 0, "2030-01-01")], [tc(A1, '01', '0001', 99999999999, '0' * 22)]),
 'c_overflow_temp_bal': ([txn('T1', 100000000000, C1)], [xref(C1, A1)], [acct(A1, 0, 100, 0, 0, "2030-01-01")], []),
 'c_d1_negative_debit': ([txn('T1', 60000, C1)], [xref(C1, A1)], [acct(A1, 0, 100000, 0, -50000, "2030-01-01")], []),
 'c_same_account_twice': ([txn('T1', 60000, C1), txn('T2', 60000, C1)], [xref(C1, A1)], [ok], []),
 'c_duplicate_tran_id_rc16': ([txn('DUP', 100, C1), txn('DUP', 200, C1)], [xref(C1, A1)], [ok], []),
}
for n, (d, x, a, t) in cases.items():
    os.makedirs(f'{base}/{n}', exist_ok=True)
    for f, rs in (('DALYTRAN', d), ('XREFFILE', x), ('ACCTFILE', a), ('TCATBALF', t)):
        with open(f'{base}/{n}/{f}.txt', 'w', newline='') as fh:
            fh.write(''.join(r + '\n' for r in rs))
