package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    private static String amount(String tran) {
        return tran.substring(132, 143);
    }

    @Test
    void truncatesNotRounds_acct2_29_24(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000000000{", "0000023399G", "").run(); // 2339.97 @ 15.00
        assertEquals("0000000292D", amount(fx.lines("TRANSACT").get(1)));
    }

    @Test
    void negativeBalanceGivesNegativeInterest(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000000000{", "0000023399P", "").run(); // -2339.97
        assertEquals("0000000292M", amount(fx.lines("TRANSACT").get(1))); // -29.24
    }

    @Test
    void accountBalanceAndCycleUpdated(@TempDir Path d) throws IOException {
        // acct 1 (not last): 1000.00 + 1000.00*15/1200 = 12.50 -> 1012.50, cycle fields zeroed
        Fx fx = new Fx(d).basic("0000100000{", "0000000000{", "").run();
        String a = fx.lines("ACCTFILE").get(0);
        assertEquals("00000101250{", a.substring(12, 24));
        assertEquals("00000000000{", a.substring(78, 90));
        assertEquals("00000000000{", a.substring(90, 102));
    }
}
