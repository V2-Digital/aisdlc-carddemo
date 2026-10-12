package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoFloatingPointTest {
    @Test void mainSourcesUseNoDoubleOrFloat() throws IOException {
        Pattern p = Pattern.compile("\\b(double|float|Double|Float)\\b");
        try (Stream<Path> s = Files.walk(Path.of(System.getProperty("repo.root"), "modernized/cbact04c/src/main"))) {
            for (Path f : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                assertFalse(p.matcher(Files.readString(f)).find(), f.toString());
            }
        }
    }
}
