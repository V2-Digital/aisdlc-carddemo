package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReturnCodeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void emptyDalytranGivesRc0AndUnchangedMasters(@TempDir Path tmp) throws Exception {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Path src = Path.of("src/test/resources/probes/accept-only/in");
        for (String f : new String[] {"XREFFILE", "ACCTFILE", "TCATBALF"}) {
            Files.copy(src.resolve(f + ".txt"), in.resolve(f + ".txt"));
        }
        Files.writeString(in.resolve("DALYTRAN.txt"), "");
        Path out = tmp.resolve("out");
        assertEquals(0, new Cbtrn02c(in, out, CLOCK, l -> {}).run());
        assertEquals(Files.readString(in.resolve("ACCTFILE.txt")), Files.readString(out.resolve("ACCTFILE.txt")));
        assertEquals(Files.readString(in.resolve("TCATBALF.txt")), Files.readString(out.resolve("TCATBALF.txt")));
        assertEquals("", Files.readString(out.resolve("TRANFILE.txt")));
    }

    @Test
    void missingInputIsStatus35Abend(@TempDir Path tmp) throws Exception {
        StringBuilder s = new StringBuilder();
        assertEquals(16, new Cbtrn02c(tmp, tmp.resolve("o"), CLOCK, l -> s.append(l).append('\n')).run());
        assertEquals("START OF EXECUTION OF PROGRAM CBTRN02C\nERROR OPENING DALYTRAN\nFILE STATUS IS: NNNN0035\n"
                + "ABENDING PROGRAM\n", s.toString());
    }
}
