package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordTest {
    @TempDir
    Path tmp;

    private Fx run(int accounts, String parm) throws Exception {
        List<String> t = new ArrayList<>(), x = new ArrayList<>(), a = new ArrayList<>();
        for (int i = 1; i <= accounts; i++) {
            t.add(Fx.tcat(i, "01", "0001", "00000120000"));
            x.add(Fx.xref(String.format("%016d", i), i));
            a.add(Fx.acct(i, Fx.Z12, Fx.Z12, Fx.Z12, ""));
        }
        return new Fx(tmp).file("TCATBALF", t).file("XREFFILE", x).file("ACCTFILE", a)
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00120{"))).run(parm);
    }

    @Test
    void layoutOfInterestTransaction() throws Exception {
        List<String> tx = run(2, "2022071800").outRecs("TRANSACT", 350);
        String r = tx.get(0);
        assertEquals(350, r.length());
        assertEquals("2022071800000001", Records.field(r, 1, 16));
        assertEquals("2022071800000002", Records.field(tx.get(1), 1, 16));
        assertEquals("010005System    ", Records.field(r, 17, 16));
        assertEquals(Records.pad("Int. for a/c 00000000001", 100), Records.field(r, 33, 100));
        assertEquals("000000000", Records.field(r, 144, 9));
        assertEquals(" ".repeat(110), Records.field(r, 153, 110));
        assertEquals("0000000000000001", Records.field(r, 263, 16));
        assertEquals("2022-07-18-00.00.00.000000", Records.field(r, 279, 26));
        assertEquals(Records.field(r, 279, 26), Records.field(r, 305, 26));
        assertEquals(" ".repeat(20), Records.field(r, 331, 20));
    }

    @Test
    void shortParmIsSpacePadded() throws Exception {
        String r = run(2, "AB").outRecs("TRANSACT", 350).get(0);
        assertEquals("AB        000001", Records.field(r, 1, 16));
    }

    @Test
    void hundredthsGoInTimestamp() {
        java.time.Clock c = Main.clock("2022071813453299");
        assertEquals("2022-07-18T13:45:32.990Z", c.instant().toString());
    }

    @Test
    void suffixWrapsAfter999999() throws Exception {
        // 1,000,001 accounts is heavy; drive the wrap with a single account holding many categories instead
        int n = 1_000_001;
        StringBuilder tb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            tb.append(Fx.tcat(1, "01", "0001", "00000000100")).append('\n');
        }
        Fx f = new Fx(tmp).file("XREFFILE", List.of(Fx.xref("1111222233334444", 1)))
                .file("ACCTFILE", List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, "")))
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00120{")));
        Files.writeString(f.in.resolve("TCATBALF.txt"), tb, java.nio.charset.StandardCharsets.ISO_8859_1);
        f.run("2022071800");
        List<String> tx = f.outRecs("TRANSACT", 350);
        assertEquals(n, tx.size());
        assertEquals("2022071800999999", Records.field(tx.get(999_998), 1, 16));
        assertEquals("2022071800000000", Records.field(tx.get(999_999), 1, 16));
        assertEquals("2022071800000001", Records.field(tx.get(1_000_000), 1, 16));
    }
}
