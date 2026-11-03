package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ClockTest {
    @Test
    void formatsDb2Timestamp() {
        assertEquals("2023-01-02-03.04.05.060000", Clock.db2(Clock.fixed("2023010203040506").now16()));
    }

    @Test
    void precedenceNowThenCarddemoThenCob() {
        assertEquals("1111111111111111", Clock.resolve("1111111111111111", Map.of("CARDDEMO_CURRENT_DATE", "2222222222222222")).now16());
        assertEquals("2222222222222222", Clock.resolve(null, Map.of("CARDDEMO_CURRENT_DATE", "2222222222222222", "COB_CURRENT_DATE", "3333333333333333")).now16());
        assertEquals("3333333333333333", Clock.resolve(null, Map.of("COB_CURRENT_DATE", "3333333333333333")).now16());
        assertEquals(16, Clock.resolve(null, Map.of()).now16().length());
    }

    @Test
    void rejectsNon16DigitForm() {
        assertThrows(IllegalArgumentException.class, () -> Clock.fixed("20230102"));
    }

    @Test
    void timestampsInTransactionsComeFromClock(@org.junit.jupiter.api.io.TempDir java.nio.file.Path t) throws Exception {
        java.nio.file.Path in = Fx.stage(t.resolve("in"), Fx.DATA.resolve("tcatbal.txt"), Fx.DATA.resolve("acctdata.txt"));
        Fx.run(in, t.resolve("out"), "2023010203040506");
        String rec = Fx.lines(t.resolve("out/TRANSACT.txt")).get(0);
        assertEquals("2023-01-02-03.04.05.060000", rec.substring(278, 304));  // orig TS
        assertEquals("2023-01-02-03.04.05.060000", rec.substring(304, 330));  // proc TS
        assertEquals(350, rec.length());
    }
}
