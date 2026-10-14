package com.v2.carddemo.cbact04c;

/** Stands in for CALL 'CEE3ABD' (exit code 16 under the harness). */
final class AbendException extends RuntimeException {
    final int code;

    AbendException(int code) {
        super("CEE3ABD: ABEND U0999");
        this.code = code;
    }
}
