package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Java output equals the real COBOL output for the legacy/tests/cbact04c-edge fixtures. */
class EdgeFixtureTest {
    static final Path FIX = Path.of("../../legacy/tests/cbact04c-edge");

    @TempDir
    Path tmp;

    static Stream<String> cases() throws IOException {
        try (Stream<Path> s = Files.list(FIX)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList().stream();
        }
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void matchesCobol(String name) throws IOException {
        Path exp = FIX.resolve(name).resolve("expected");
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        int rc = new Cbact04c(FIX.resolve(name).resolve("in"), tmp.resolve("out"), "2022071800",
                Main.clock("2022071800000000"), new PrintStream(so, true, StandardCharsets.ISO_8859_1),
                new PrintStream(se, true, StandardCharsets.ISO_8859_1)).run();
        assertEquals(read(exp.resolve("RC.txt")).trim(), String.valueOf(rc));
        assertEquals(read(exp.resolve("SYSOUT.txt")), so.toString(StandardCharsets.ISO_8859_1));
        if (Files.exists(exp.resolve("STDERR.txt"))) {
            assertTrue(se.toString().contains("CEE3ABD: ABEND U0999"));
            assertFalse(Files.exists(tmp.resolve("out/TRANSACT.txt")), "no outputs after abend");
        } else {
            for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
                assertEquals(read(exp.resolve(f)), read(tmp.resolve("out").resolve(f)), name + "/" + f);
            }
        }
    }
}
