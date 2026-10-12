package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Java output must equal output captured from legacy/harness/run.sh for the same crafted input. */
class HarnessProbeTest {
    private static final Path PROBES = Path.of("src/test/resources/probes");

    static Stream<String> cases() throws Exception {
        try (Stream<Path> s = Files.list(PROBES)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    void matchesHarness(String name, @TempDir Path out) throws Exception {
        Path dir = PROBES.resolve(name);
        StringBuilder sysout = new StringBuilder();
        Clock clock = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);
        int rc = new Cbtrn02c(dir.resolve("in"), out, clock, l -> sysout.append(l).append('\n')).run();

        Path exp = dir.resolve("expected");
        assertEquals(Files.readString(exp.resolve("RC.txt")).trim(), String.valueOf(rc), name + " RC");
        assertEquals(Files.readString(exp.resolve("SYSOUT.txt")), sysout.toString(), name + " SYSOUT");
        int compared = 0;
        for (String f : new String[] {"TRANFILE", "DALYREJS", "ACCTFILE", "TCATBALF"}) {
            if (Files.exists(exp.resolve(f + ".txt"))) { // harness exports no datasets on abend
                assertEquals(Files.readString(exp.resolve(f + ".txt")), Files.readString(out.resolve(f + ".txt")),
                        name + " " + f);
                compared++;
            }
        }
        assertTrue(compared == 4 || rc == 16, name + ": expected all 4 datasets unless abend");
    }
}
