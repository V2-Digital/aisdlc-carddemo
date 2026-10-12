package carddemo.cbtrn02c;

/** CEE3ABD U0999: the program has displayed its error lines and must end with RC 16. */
final class Abend extends RuntimeException {
    Abend() {
        super("ABEND U0999", null, false, false);
    }
}
