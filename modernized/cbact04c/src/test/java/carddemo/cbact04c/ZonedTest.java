package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void decodesOverpunch() {
        assertEquals(bd("14.56"), Zoned.decode("0000000145F", 2));
        assertEquals(bd("-14.56"), Zoned.decode("0000000145O", 2));
        assertEquals(bd("0.00"), Zoned.decode("0000000000{", 2));
        assertEquals(bd("0.00"), Zoned.decode("0000000000}", 2));
        assertEquals(bd("12.34"), Zoned.decode("00000001234", 2));
    }

    @Test
    void roundTrips() {
        for (String s : new String[] {"0000000145F", "0000000145O", "0000000000{", "9999999999I", "0000000100}", "0000000100{"}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s, 2), 2, 11));
        }
    }

    @Test
    void zeroIsAlwaysPlusZero() {
        assertEquals("0000000000{", Zoned.encode(bd("-0.004"), 2, 11));
        assertEquals("0000000000{", Zoned.encode(BigDecimal.ZERO, 2, 11));
        assertEquals("0000000000{", Zoned.encode(Zoned.decode("0000000000}", 2), 2, 11));
    }

    @Test
    void overflowDropsHighOrderDigits() {
        assertEquals("0000000000E", Zoned.encode(bd("10000000000.05"), 2, 11));
        assertEquals(bd("0.05"), Zoned.truncate(bd("10000000000.05"), 2, 11));
        assertEquals(bd("-0.05"), Zoned.truncate(bd("-10000000000.05"), 2, 11));
    }
}
