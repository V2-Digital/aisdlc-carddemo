package com.v2.carddemo.cbact04c;

import java.nio.file.Path;
import java.util.Map;

/** Resolves a DD name to a file: env DD_&lt;DD&gt;, else INPUT_DIR / OUTPUT_DIR + /&lt;DD&gt;.txt. */
public final class DdFiles {
    private final Map<String, String> env;
    private final Path inDir;
    private final Path outDir;

    public DdFiles(Map<String, String> env, Path inDir, Path outDir) {
        this.env = env;
        this.inDir = inDir;
        this.outDir = outDir;
    }

    Path input(String dd) {
        String e = env.get("DD_" + dd);
        return e != null ? Path.of(e) : inDir.resolve(dd + ".txt");
    }

    Path output(String dd) {
        String e = env.get("DD_" + dd);
        return e != null ? Path.of(e) : outDir.resolve(dd + ".txt");
    }
}
