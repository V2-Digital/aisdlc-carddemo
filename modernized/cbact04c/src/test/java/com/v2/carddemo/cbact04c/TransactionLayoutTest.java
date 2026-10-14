package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionLayoutTest {
    @Test
    void idSequenceAndSpaces(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in);
        Fx.run(in, out);
        List<String> t = Fx.lines(out.resolve("TRANSACT.txt"));
        for (int i = 0; i < t.size(); i++) {
            String r = t.get(i);
            assertEquals(350, r.length());
            assertEquals("2022071800" + String.format("%06d", i + 1), r.substring(0, 16));
            assertEquals("010005System    ", r.substring(16, 32));
            assertTrue(r.substring(32 + 13 + 11, 132).isBlank(), "desc tail");
            assertTrue(r.substring(152, 222).isBlank(), "merchant name/city/zip");
            assertTrue(r.substring(330).isBlank(), "filler");
            assertEquals(r.substring(278, 304), r.substring(304, 330));
            assertEquals("2022-07-18-00.00.00.000000", r.substring(278, 304));
        }
    }
}
