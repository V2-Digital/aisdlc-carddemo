package com.v2.carddemo.cbact04c;

/** Program abend (CEE3ABD U0999); the message lines were already DISPLAYed. */
final class Abend extends RuntimeException {
    Abend(String reason) {
        super(reason, null, false, false);
    }
}
