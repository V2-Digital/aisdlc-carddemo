package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** DISPLAY output: collected, echoed to a stream, optionally written to a file. */
public final class Sysout {
    private final List<String> lines = new ArrayList<>();
    private final PrintStream echo;

    public Sysout(PrintStream echo) {
        this.echo = echo;
    }

    public void display(String line) {
        lines.add(line);
        if (echo != null) echo.print(line + "\n");
    }

    public List<String> lines() {
        return lines;
    }

    public void writeTo(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
