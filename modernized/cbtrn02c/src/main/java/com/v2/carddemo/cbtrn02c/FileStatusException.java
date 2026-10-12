package com.v2.carddemo.cbtrn02c;

/** An I/O failure carrying the two-character COBOL file status. */
final class FileStatusException extends RuntimeException {
    final String status;

    FileStatusException(String status) {
        super("file status " + status);
        this.status = status;
    }
}
