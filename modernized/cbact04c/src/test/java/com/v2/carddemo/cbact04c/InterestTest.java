package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InterestTest {
    private static BigDecimal interest(String bal, String rate) {
        return Zoned.wrap(new BigDecimal(bal).multiply(new BigDecimal(rate)).divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN), 9);
    }

    @Test
    void acct2TruncatesTo2924() {
        assertEquals(new BigDecimal("29.24"), interest("2339.97", "15.00"));
    }

    @Test
    void negativeTruncatesTowardZero() {
        assertEquals(new BigDecimal("-0.12"), interest("-10.01", "15.00"));
    }

    @Test
    void overflowDropsHighOrderDigits() {
        assertEquals(new BigDecimal("333324999.91"), interest("999999999.99", "9999.99"));
        assertEquals(new BigDecimal("0.99"), Zoned.wrap(new BigDecimal("1000000000.99"), 9));
    }

    @Test
    void noFloatingPointInMain() throws IOException {
        Path src = Path.of(System.getProperty("user.dir"), "src/main/java");
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String t = Files.readString(p);
                assertFalse(t.matches("(?s).*\\b(double|float|Double|Float)\\b.*"), p + " uses floating point");
            }
        }
    }

    @Test
    void pomHasOnlyJunit() throws IOException {
        String pom = Files.readString(Path.of(System.getProperty("user.dir"), "pom.xml"));
        assertEquals(1, pom.split("<dependency>", -1).length - 1);
        assertTrue(pom.contains("junit-jupiter"));
    }
}
