package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputFormatTest {
    @TempDir Path tmp;

    @Test void shortLinesPaddedAndCrlfStripped() throws IOException {
        Path p = tmp.resolve("x");
        Files.writeString(p, "ABC\r\nDEF\n", StandardCharsets.ISO_8859_1);
        var l = FixedFile.read(p, 10);
        assertEquals(java.util.List.of("ABC       ", "DEF       "), l);
    }

    @Test void overlongLineRejected() throws IOException {
        Path p = tmp.resolve("x");
        Files.writeString(p, "ABCDEFGHIJK\n", StandardCharsets.ISO_8859_1);
        assertThrows(java.io.IOException.class, () -> FixedFile.read(p, 10));
    }

    @Test void writeIsLrecl_plusLf_noCr() throws IOException {
        Path p = tmp.resolve("o");
        FixedFile.write(p, java.util.List.of("AB   "));
        assertArrayEquals("AB   \n".getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(p));
    }

    @Test void stockXrefIs36BytesAndStillWorks() throws IOException {
        Path x = Fx.root().resolve("app/data/ASCII/cardxref.txt");
        assertEquals(36, Files.readAllLines(x).get(0).length());
        assertEquals(50, FixedFile.read(x, 50).get(0).length());
    }

    @Test void crlfTcatbalGivesSameOutput() throws IOException {
        Fx a = new Fx(tmp).rate("12.00");
        a.tcat.add(Fx.tcatRec("00000000001", "01", "0001", "100.00"));
        a.xref.add(Fx.xrefRec("4111111111111111", "00000000001"));
        a.acct.add(Fx.acctRec("00000000001", "1.00", ""));
        a.run();
        var expected = a.trans();
        Files.writeString(tmp.resolve("TCATBALF.txt"), a.tcat.get(0) + "\r\n", StandardCharsets.ISO_8859_1);
        a.exec();
        assertEquals(expected, a.trans());
    }
}
