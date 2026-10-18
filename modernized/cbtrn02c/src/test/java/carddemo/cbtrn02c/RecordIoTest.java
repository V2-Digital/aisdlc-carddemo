package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordIoTest {
    @Test
    void crlfIsStrippedAndShortLinesPadded() throws Exception {
        Path f = Files.createTempFile("rio", ".txt");
        Files.writeString(f, "AB\r\nCDEF\r\n");
        List<byte[]> r = RecordIo.read(f, 5);
        assertEquals(2, r.size());
        assertArrayEquals("AB   ".getBytes(), r.get(0));
        assertArrayEquals("CDEF ".getBytes(), r.get(1));
    }

    @Test
    void lfOnlyAndNoTrailingNewline() throws Exception {
        Path f = Files.createTempFile("rio", ".txt");
        Files.writeString(f, "AB\nCD");
        assertEquals(2, RecordIo.read(f, 3).size());
    }

    @Test
    void longLinesTruncatedAndEmptyFileIsEmpty() throws Exception {
        Path f = Files.createTempFile("rio", ".txt");
        Files.writeString(f, "ABCDEFG\n");
        assertArrayEquals("ABC".getBytes(), RecordIo.read(f, 3).get(0));
        Files.writeString(f, "");
        assertEquals(0, RecordIo.read(f, 3).size());
    }

    @Test
    void stockXrefHas36ByteLinesPaddedTo50() throws Exception {
        List<byte[]> r = RecordIo.read(Path.of(Fx.root() + "/app/data/ASCII/cardxref.txt"), 50);
        assertEquals(50, r.size());
        assertEquals(50, r.get(0).length);
        assertEquals("00000000050", RecordIo.str(r.get(0), 25, 11));
    }
}
