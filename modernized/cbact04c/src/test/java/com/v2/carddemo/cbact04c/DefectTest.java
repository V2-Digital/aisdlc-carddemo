package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization of preserved legacy defects (CR-1 D1-D6). */
class DefectTest {
    private static final Path GOLD = Fx.ROOT.resolve("legacy/golden");

    /** D1 on real data: account 50 gets interest 18.77 in TRANSACT but its ACCTFILE record is unchanged. */
    @Test
    void lastAccountNotRewritten() throws IOException {
        List<String> in = Fx.lines(Fx.ROOT.resolve("legacy/golden/CBTRN02C/ACCTFILE.txt"), 300);
        InterestCalc.Result r = Fx.run(
                Fx.lines(GOLD.resolve("CBTRN02C/TCATBALF.txt"), 50),
                Fx.lines(Fx.ROOT.resolve("app/data/ASCII/cardxref.txt"), 50), in,
                Fx.lines(Fx.ROOT.resolve("app/data/ASCII/discgrp.txt"), 50));
        String tran = r.transact().get(r.transact().size() - 1);
        assertEquals("2022071800000050", tran.substring(0, 16));
        assertEquals("0000000187G", Fx.amount(tran));
        assertEquals(in.get(49), r.acctfile().get(49));
    }

    /** D1 minimal: with one account only, nothing is ever rewritten. */
    @Test
    void singleAccountNeverRewritten() {
        String a = Fx.acct("00000000001", "DEFAULT");
        InterestCalc.Result r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{")),
                List.of(Fx.xref("4111111111111111", "00000000001")), List.of(a),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00150{")));
        assertEquals(1, r.transact().size());
        assertEquals("0000000150{", Fx.amount(r.transact().get(0)));
        assertEquals(a, r.acctfile().get(0));
    }

    /** D6: PARM is not validated, only padded or cut to 10. */
    @Test
    void parmPaddedAndCut() {
        List<String> t = List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{"));
        List<String> x = List.of(Fx.xref("4111111111111111", "00000000001"));
        List<String> a = List.of(Fx.acct("00000000001", "DEFAULT"));
        List<String> d = List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"));
        assertEquals("AB        000001", new InterestCalc("AB", Fx.CLOCK, 0).run(t, x, a, d).transact().get(0).substring(0, 16));
        assertEquals("0123456789000001",
                new InterestCalc("0123456789XYZ", Fx.CLOCK, 0).run(t, x, a, d).transact().get(0).substring(0, 16));
    }
}
