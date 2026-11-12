package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** F1: the last account in TCATBALF is never rewritten (legacy defect, preserved). */
class DefectF1Test {
    @Test
    void lastAccountNotRewrittenOnGoldenData(@TempDir Path dir) throws Exception {
        Map<String, Path> in = Support.stock();
        in.put("ACCTFILE", Support.golden("CBTRN02C", "ACCTFILE.txt"));
        in.put("TCATBALF", Support.golden("CBTRN02C", "TCATBALF.txt"));
        Support.Result r = Support.run(dir, in, "2022071800");
        String before = acct50(Files.readAllLines(Support.golden("CBTRN02C", "ACCTFILE.txt")));
        String after = acct50(Files.readAllLines(dir.resolve("ACCTFILE.txt")));
        assertEquals(before, after);
        assertEquals("00000019458G", after.substring(12, 24));
        assertNotEquals("00000000000{", after.substring(78, 90), "cycle credit not zeroed");
        assertTrue(r.text("TRANSACT").contains("2022071800000050"));
        assertTrue(r.text("TRANSACT").contains("0000000187G"), "interest 18.77 is posted as a txn");
    }

    @Test
    void singleAccountIsNeverRewritten(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, Support.tcat("00000000001", "01", "0001", "00000100000") + "\n",
                Support.xref("1111222233334444", "00000000001") + "\n",
                Support.acct("00000000001", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "01", "0001", "00120{") + "\n");
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(Files.readString(in.get("ACCTFILE")), r.text("ACCTFILE"));
        assertEquals(1, r.text("TRANSACT").lines().count());
    }

    private static String acct50(List<String> lines) {
        return lines.stream().filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
    }
}
