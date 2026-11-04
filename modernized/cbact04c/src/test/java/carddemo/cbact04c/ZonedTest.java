package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripsEverySignDigit() {
        for (int d = 0; d <= 9; d++) {
            BigDecimal pos = new BigDecimal("12345.0" + d).add(BigDecimal.ZERO);
            String e = Zoned.encode(pos, 9);
            assertEquals(pos.setScale(2), Zoned.decode(e));
            assertTrue("{ABCDEFGHI".indexOf(e.charAt(10)) >= 0);
            BigDecimal neg = pos.negate();
            String n = Zoned.encode(neg, 9);
            assertEquals(neg.setScale(2), Zoned.decode(n));
            assertTrue("}JKLMNOPQR".indexOf(n.charAt(10)) >= 0);
        }
    }

    @Test
    void zeroIsWrittenAsPositive() {
        assertEquals("00000000000{", Zoned.encode(BigDecimal.ZERO, 10));
    }

    @Test
    void negativeZeroDecodesToZero() {
        assertEquals(0, Zoned.decode("0000000000}").signum());
    }

    @Test
    void knownEncodings() {
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 9));
        assertEquals("0000000187P", Zoned.encode(new BigDecimal("-18.77"), 9));
    }

    @Test
    void badSignRejected() {
        assertThrows(IllegalArgumentException.class, () -> Zoned.decode("0000000000x"));
    }
}
