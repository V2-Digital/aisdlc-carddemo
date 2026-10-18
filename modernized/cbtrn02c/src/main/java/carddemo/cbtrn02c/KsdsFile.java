package carddemo.cbtrn02c;

import java.util.TreeMap;

/** In-memory stand-in for a VSAM KSDS: records keyed by a byte range, iterated in key order. */
public final class KsdsFile {
    private final TreeMap<String, byte[]> map = new TreeMap<>();
    private final int keyOff;
    private final int keyLen;

    public KsdsFile(int keyOff, int keyLen) {
        this.keyOff = keyOff;
        this.keyLen = keyLen;
    }

    public void load(Iterable<byte[]> recs) {
        for (byte[] r : recs) {
            map.put(RecordIo.str(r, keyOff, keyLen), r);
        }
    }

    public byte[] read(String key) {
        byte[] r = map.get(key);
        return r == null ? null : r.clone();
    }

    /** @return false on duplicate key (status 22) */
    public boolean write(byte[] rec) {
        return map.putIfAbsent(RecordIo.str(rec, keyOff, keyLen), rec.clone()) == null;
    }

    /** @return false if the record is not there (status 23) */
    public boolean rewrite(byte[] rec) {
        String k = RecordIo.str(rec, keyOff, keyLen);
        if (!map.containsKey(k)) {
            return false;
        }
        map.put(k, rec.clone());
        return true;
    }

    public Iterable<byte[]> records() {
        return map.values();
    }

    public int size() {
        return map.size();
    }
}
