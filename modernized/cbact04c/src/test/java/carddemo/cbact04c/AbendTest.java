package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @Test
    void accountMissing(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        fx.put("ACCTFILE", List.of(Fx.acct("00000000002", "0000100000{", "")));
        fx.run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), fx.sysout);
    }

    @Test
    void xrefMissing(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        fx.put("XREFFILE", List.of(Fx.xref("4222222222222222", "00000000002")));
        fx.run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), fx.sysout);
    }

    @Test
    void defaultGroupMissing(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        fx.put("DISCGRP", List.of(Fx.disc("GRP1", "01", "0001", "00200{")));
        fx.run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), fx.sysout);
    }

    @Test
    void inputUnreadable(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        Files.delete(d.resolve("TCATBALF.txt"));
        fx.run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.endsWith("FILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"), fx.sysout);
    }
}
