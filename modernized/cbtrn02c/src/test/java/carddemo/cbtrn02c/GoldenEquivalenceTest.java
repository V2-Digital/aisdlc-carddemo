package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoldenEquivalenceTest {
    @Test
    void stockInputsMatchLegacyGoldenByteForByte() throws Exception {
        Path root = Fx.root();
        Path data = Path.of(System.getProperty("carddemo.data", root.resolve("app/data/ASCII").toString()));
        Path golden = Path.of(System.getProperty("carddemo.golden", root.resolve("legacy/golden/CBTRN02C").toString()));
        Path out = Files.createTempDirectory("golden");
        Map<String, Path> dd = new HashMap<>();
        dd.put("DALYTRAN", data.resolve("dailytran.txt"));
        dd.put("XREFFILE", data.resolve("cardxref.txt"));
        // ACCTFILE / TCATBALF are updated in place by default: use _OUT so sample data stays untouched
        dd.put("ACCTFILE", data.resolve("acctdata.txt"));
        dd.put("TCATBALF", data.resolve("tcatbal.txt"));
        dd.put("ACCTFILE_OUT", out.resolve("ACCTFILE.txt"));
        dd.put("TCATBALF_OUT", out.resolve("TCATBALF.txt"));
        dd.put("TRANFILE", out.resolve("TRANFILE.txt"));
        dd.put("DALYREJS", out.resolve("DALYREJS.txt"));
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        int rc = new Cbtrn02c(dd, Fx.CLOCK, new java.io.PrintStream(bo, true, "ISO-8859-1")).run();
        assertEquals(Integer.parseInt(Files.readString(golden.resolve("RC.txt")).trim()), rc);
        assertEquals(4, rc);
        for (String f : new String[] {"TRANFILE", "DALYREJS", "ACCTFILE", "TCATBALF"}) {
            assertArrayEquals(Files.readAllBytes(golden.resolve(f + ".txt")), Files.readAllBytes(out.resolve(f + ".txt")), f);
        }
        assertArrayEquals(Files.readAllBytes(golden.resolve("SYSOUT.txt")), bo.toByteArray(), "SYSOUT");
        assertEquals(262, RecordIo.read(out.resolve("TRANFILE.txt"), 350).size());
        assertEquals(38, RecordIo.read(out.resolve("DALYREJS.txt"), 430).size());
        assertEquals(50, RecordIo.read(out.resolve("ACCTFILE.txt"), 300).size());
        assertEquals(100, RecordIo.read(out.resolve("TCATBALF.txt"), 50).size());
    }
}
