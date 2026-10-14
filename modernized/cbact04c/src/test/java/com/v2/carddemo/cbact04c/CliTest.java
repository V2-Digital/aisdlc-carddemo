package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {
    @TempDir Path tmp;

    @Test
    void clockArgAndEnvAndParmPadding() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        FileIo.write(f.file("TCATBALF"), f.tcat);
        FileIo.write(f.file("XREFFILE"), f.xref);
        FileIo.write(f.file("DISCGRP"), f.disc);
        FileIo.write(f.file("ACCTFILE"), f.acct);
        String[] paths = {"--TCATBALF", f.file("TCATBALF").toString(), "--XREFFILE", f.file("XREFFILE").toString(),
                "--DISCGRP", f.file("DISCGRP").toString(), "--ACCTFILE", f.file("ACCTFILE").toString(),
                "--TRANSACT", f.file("TRANSACT").toString()};
        String[] args = new String[paths.length + 4];
        args[0] = "--parm"; args[1] = "ABC"; args[2] = "--clock"; args[3] = "2023010203040506";
        System.arraycopy(paths, 0, args, 4, paths.length);
        assertEquals(0, Main.run(args, "19990101000000", new java.io.PrintStream(new java.io.ByteArrayOutputStream())));
        String t = f.lines("TRANSACT").get(0);
        assertEquals("ABC       000001", t.substring(0, 16)); // F4 padded to 10
        assertEquals("2023-01-02-03.04.05.060000", t.substring(278, 304)); // --clock beats env
        assertEquals(t.substring(278, 304), t.substring(304, 330));
        // env alone
        args[3] = ""; args[2] = "--x";
        assertEquals(0, Main.run(args, "2020030405060708", new java.io.PrintStream(new java.io.ByteArrayOutputStream())));
        assertEquals("2020-03-04-05.06.07.080000", f.lines("TRANSACT").get(0).substring(278, 304));
    }

    @Test
    void longParmTruncatedAndMissingArgIsUsageError() throws IOException {
        assertEquals(2, Main.run(new String[] {"--parm", "x"}, null, new java.io.PrintStream(new java.io.ByteArrayOutputStream())));
    }
}
