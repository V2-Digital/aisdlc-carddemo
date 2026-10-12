package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();

    @Test
    void stockDataMatchesGolden(@TempDir Path tmp) throws Exception {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Path ascii = ROOT.resolve("app/data/ASCII");
        Files.copy(ascii.resolve("dailytran.txt"), in.resolve("DALYTRAN.txt"));
        Files.copy(ascii.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ascii.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ascii.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Path out = tmp.resolve("out");
        StringBuilder sysout = new StringBuilder();
        Clock clock = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);
        int rc = new Cbtrn02c(in, out, clock, l -> sysout.append(l).append('\n')).run();

        Path golden = ROOT.resolve("legacy/golden/CBTRN02C");
        assertEquals(4, rc);
        assertEquals(Files.readString(golden.resolve("SYSOUT.txt")), sysout.toString());
        for (String f : new String[] {"TRANFILE", "DALYREJS", "ACCTFILE", "TCATBALF"}) {
            assertEquals(Files.readString(golden.resolve(f + ".txt")), Files.readString(out.resolve(f + ".txt")), f);
        }
    }

    @Test
    void timestampFormat() {
        Clock c = Clock.fixed(Instant.parse("2022-07-18T12:34:56.780Z"), ZoneOffset.UTC);
        assertEquals("2022-07-18-12.34.56.780000", new Cbtrn02c(Path.of("."), Path.of("."), c, l -> {}).timestamp());
    }

    @Test
    void zonedZeroAndTruncation() {
        assertEquals("00000000000".substring(1) + "{", Zoned.encode(0, 11));
        assertEquals("0000005047F", Zoned.encode(99999999999L + 50477, 11)); // F7: 999,999,999.99 + 504.77 wraps
        assertEquals(0, Zoned.decode("0000000000}"));
        assertEquals(1005, Zoned.decode("00000001005"));
    }
}
