package com.v2.carddemo.cbact04c;

/** Mirrors 9999-ABEND-PROGRAM (CEE3ABD U0999). */
final class AbendException extends RuntimeException {
    final String status;

    AbendException(String status) {
        super("ABEND U0999, file status " + status);
        this.status = status;
    }
}
