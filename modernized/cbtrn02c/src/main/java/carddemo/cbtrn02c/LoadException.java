package carddemo.cbtrn02c;

/** Input dataset cannot be loaded (long line, duplicate key, bad numeric): exit 16. */
final class LoadException extends RuntimeException {
    LoadException(String message) {
        super(message);
    }
}
