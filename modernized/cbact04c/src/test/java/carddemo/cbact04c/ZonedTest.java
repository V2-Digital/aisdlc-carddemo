package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void roundTripAllSigns() {
        assertEquals(new BigDecimal("29.24"), Zoned.parse("0000000292D", 2));
        assertEquals(new BigDecimal("-29.24"), Zoned.parse("0000000292M", 2));
        assertEquals("0000000292M", Zoned.format(new BigDecimal("-29.24"), 2, 11));
        assertEquals("0000000000{", Zoned.format(BigDecimal.ZERO, 2, 11));
        assertEquals(new BigDecimal("12.34"), Zoned.parse("00000001234", 2)); // plain digits positive
    }

    @Test
    void truncateDropsHighOrderDigitsKeepsSign() {
        assertEquals(new BigDecimal("234.56"), Zoned.truncate(new BigDecimal("1234.567"), 2, 3));
        assertEquals(new BigDecimal("-234.56"), Zoned.truncate(new BigDecimal("-1234.567"), 2, 3));
    }
}
