package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZonedTest {
    @Test
    void decodesSigns() {
        assertEquals(new BigDecimal("1.50"), Zoned.decode("0000000015{"));
        assertEquals(new BigDecimal("18.77"), Zoned.decode("0000000187G"));
        assertEquals(new BigDecimal("-12.34"), Zoned.decode("0000000123M"));
        assertEquals(new BigDecimal("-0.01"), Zoned.decode("0000000000J"));
        assertEquals(new BigDecimal("-1.20"), Zoned.decode("0000000012}"));
        assertEquals(new BigDecimal("12.34"), Zoned.decode("00000001234"));
    }

    @Test
    void encodesSigns() {
        assertEquals("0000000015{", Zoned.encode(new BigDecimal("1.50"), 11));
        assertEquals("0000000187G", Zoned.encode(new BigDecimal("18.77"), 11));
        assertEquals("0000000123M", Zoned.encode(new BigDecimal("-12.34"), 11));
        assertEquals("0000000012}", Zoned.encode(new BigDecimal("-1.20"), 11));
        assertEquals("0000000000J", Zoned.encode(new BigDecimal("-0.01"), 11));
    }

    @Test
    void zeroAlwaysBrace() {
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("0.00"), 11));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11));
    }

    @Test
    void truncateKeepsSignAndDropsHighOrder() {
        assertEquals(new BigDecimal("-1.00"), Zoned.truncate(new BigDecimal("-1000000001.00"), 11));
        assertEquals(new BigDecimal("5.00"), Zoned.truncate(new BigDecimal("1000000005.00"), 11));
    }
}
