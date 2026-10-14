package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private static final String TAIL = "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n";

    @Test
    void missingAccount(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{");
        f.acct.remove(1);
        f.run(t);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000002\nERROR READING ACCOUNT FILE\n" + TAIL), f.sysout);
        assertTrue(f.err.contains("CEE3ABD: ABEND U0999"));
        // work done before the abend is kept: account 1 rewritten, one transaction written
        assertEquals(1, f.transact.size());
        assertEquals("00000000000{", Records.slice(f.acctOut.get(0), 79, 12));
    }

    @Test
    void missingXref(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{");
        f.xref.remove(1);
        f.run(t);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000002\nERROR READING XREF FILE\n" + TAIL), f.sysout);
    }

    @Test
    void missingDefaultRate(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{");
        f.disc.clear();
        f.run(t);
        assertEquals(16, f.rc);
        assertTrue(f.sysout.endsWith("TRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\n" + TAIL),
                f.sysout);
        assertEquals(0, f.transact.size());
    }
}
