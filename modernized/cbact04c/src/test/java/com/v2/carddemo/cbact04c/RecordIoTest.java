package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordIoTest {
    @Test
    void crlfStrippedAndShortRecordsPadded() throws Exception {
        Path f = Files.createTempFile("rec", ".txt");
        Files.writeString(f, "abc\r\nde\n");
        assertEquals(List.of("abc  ", "de   "), RecordFile.read(f, 5));
    }

    @Test
    void stockTcatbalIsCrlfAndXrefIs36Bytes() throws Exception {
        assertTrue(Files.readString(Support.ascii("tcatbal.txt")).contains("\r\n"));
        List<String> t = RecordFile.read(Support.ascii("tcatbal.txt"), 50);
        assertEquals(50, t.size());
        assertTrue(t.stream().allMatch(s -> s.length() == 50 && !s.contains("\r")));
        List<String> x = RecordFile.read(Support.ascii("cardxref.txt"), 50);
        assertEquals(50, x.size());
        assertTrue(x.stream().allMatch(s -> s.length() == 50));
    }

    @Test
    void emptyFileHasNoRecords() throws Exception {
        assertTrue(RecordFile.read(Files.createTempFile("rec", ".txt"), 5).isEmpty());
    }
}
