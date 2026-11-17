package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC6: abend conditions exit 16 and leave --out empty. */
class AbendTest {
    private void abend(Path tmp, String acctFile, String xrefFile, String disc, String expectedMsg) throws IOException {
        Path in = tmp.resolve("in");
        Path out = tmp.resolve("out");
        Files.createDirectories(in);
        Files.createDirectories(out);
        Files.writeString(in.resolve("TCATBALF.txt"), Fx.tcat("00000000001", "01", "0001", "0000012000{") + "\n");
        Files.writeString(in.resolve("ACCTFILE.txt"), acctFile);
        Files.writeString(in.resolve("XREFFILE.txt"), xrefFile);
        Files.writeString(in.resolve("DISCGRP.txt"), disc);
        Object[] r = Fx.cli("--parm", "2022071800", "--in", in.toString(), "--out", out.toString());
        assertEquals(16, r[0]);
        assertTrue(((String) r[1]).contains(expectedMsg), (String) r[1]);
        assertTrue(((String) r[1]).contains("ABENDING PROGRAM"));
        try (Stream<Path> s = Files.list(out)) {
            assertEquals(List.of(), s.toList());
        }
    }

    private static final String ACCT = Fx.acct("00000000001", "DEFAULT") + "\n";
    private static final String XREF = Fx.xref("4111111111111111", "00000000001") + "\n";
    private static final String DISC = Fx.disc("DEFAULT", "01", "0001", "00150{") + "\n";

    @Test
    void missingAccount(@TempDir Path tmp) throws IOException {
        abend(tmp, Fx.acct("00000000009", "DEFAULT") + "\n", XREF, DISC, "ERROR READING ACCOUNT FILE");
    }

    @Test
    void missingXref(@TempDir Path tmp) throws IOException {
        abend(tmp, ACCT, Fx.xref("4111111111111111", "00000000009") + "\n", DISC, "ERROR READING XREF FILE");
    }

    @Test
    void missingDefaultRate(@TempDir Path tmp) throws IOException {
        abend(tmp, ACCT, XREF, Fx.disc("DEFAULT", "02", "0001", "00150{") + "\n",
                "ERROR READING DEFAULT DISCLOSURE GROUP");
    }
}
