package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.function.Function;

/** In-memory KSDS: records keyed on a byte-string key, iterated in key order. */
final class Ksds {
    /** Duplicate primary/alternate key at load time (harness load failure, exit 3). */
    static final class LoadError extends RuntimeException {
        LoadError(String msg) {
            super(msg, null, false, false);
        }
    }

    final TreeMap<String, String> byKey = new TreeMap<>();
    /** Alternate index (XREFFILE acct id), null when unused. */
    final TreeMap<String, String> byAlt = new TreeMap<>();

    static Ksds load(String dd, Path p, int lrecl, Function<String, String> key,
                     Function<String, String> altKey) throws IOException {
        Ksds k = new Ksds();
        for (String rec : FixedText.read(p, lrecl)) {
            String pk = key.apply(rec);
            if (k.byKey.put(pk, rec) != null) throw new LoadError("duplicate key " + pk + " in " + dd);
            if (altKey != null) {
                String ak = altKey.apply(rec);
                if (k.byAlt.put(ak, rec) != null) throw new LoadError("duplicate key " + ak + " in " + dd);
            }
        }
        return k;
    }
}
