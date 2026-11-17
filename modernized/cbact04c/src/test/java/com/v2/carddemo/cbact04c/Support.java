package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class Support {
    static final String CLOCK = "2022071800000000";

    record Result(int rc, String sysout, Path out) {
    }

    private Support() {
    }

    static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("repo root not found");
        }
        return p;
    }

    static Result run(Path tcat, Path xref, Path disc, Path acct, Path out) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, false, StandardCharsets.ISO_8859_1);
        int rc = new Cbact04c(tcat, xref, disc, acct, out, Clock.fixed(CLOCK), ps).run("2022071800");
        ps.flush();
        return new Result(rc, buf.toString(StandardCharsets.ISO_8859_1), out);
    }

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.ISO_8859_1);
    }

    static Path write(Path dir, String name, String... lines) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
        return p;
    }
}
