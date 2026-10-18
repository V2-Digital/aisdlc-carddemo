package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OverpunchTest {
    @Test
    void allTwentySignCharactersRoundTrip() {
        String chars = "{ABCDEFGHI}JKLMNOPQR";
        for (int i = 0; i < chars.length(); i++) {
            byte[] b = ("0000012345" + 0).getBytes(StandardCharsets.ISO_8859_1);
            b[10] = (byte) chars.charAt(i);
            long v = Overpunch.decode(b, 0, 11);
            byte[] out = new byte[11];
            Overpunch.encode(v, out, 0, 11);
            assertEquals(chars.charAt(i), (char) out[10], "sign char " + chars.charAt(i));
            assertEquals(new String(b, StandardCharsets.ISO_8859_1), new String(out, StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    void knownValues() {
        assertEquals(-4788, Overpunch.decode("00000000478Q".getBytes(), 0, 12));
        assertEquals(50477, Overpunch.decode("0000005047G".getBytes(), 0, 11));
        assertEquals(-91900, Overpunch.decode("0000009190}".getBytes(), 0, 11));
    }

    @Test
    void zeroIsWrittenAsBrace() {
        byte[] b = new byte[5];
        Overpunch.encode(0, b, 0, 5);
        assertEquals("0000{", new String(b));
    }

    @Test
    void truncateKeepsSignAndLowDigits() {
        assertEquals(1, Overpunch.truncate(100_000_000_001L, 11));
        assertEquals(-1, Overpunch.truncate(-100_000_000_001L, 11));
        assertEquals(99_999_999_999L, Overpunch.truncate(99_999_999_999L, 11));
    }
}
