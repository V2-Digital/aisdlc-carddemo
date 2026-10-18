package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class ReturnCodeTest {
    @Test
    void noRejectsRc0() throws IOException {
        assertEquals(0, Fx.basic().txn("T1", 100, Fx.CARD).run().rc);
    }

    @Test
    void rejectsRc4() throws IOException {
        assertEquals(4, Fx.basic().txn("T1", 100, "9999999999999999").run().rc);
    }

    @Test
    void missingInputFileRc16() throws IOException {
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD);
        RecordIo.write(f.p("DALYTRAN"), f.daily);
        RecordIo.write(f.p("XREFFILE"), f.xref);
        RecordIo.write(f.p("TCATBALF"), f.tcat); // ACCTFILE missing
        f.runFiles();
        assertEquals(16, f.rc);
        assertTrue(f.sysout.contains("ABENDING PROGRAM"));
    }

    @Test
    void duplicateTranIdRc16() throws IOException {
        Fx f = Fx.basic().txn("DUP", 100, Fx.CARD).txn("DUP", 100, Fx.CARD).run();
        assertEquals(16, f.rc);
        assertTrue(Files.exists(f.p("TRANFILE")));
        assertTrue(f.sysout.contains("ERROR WRITING TO TRANSACTION FILE"));
    }
}
