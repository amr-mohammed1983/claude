package dev.filenetmcp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Java 8 stand-ins for the Java 9+ helpers (Map.of, String.isBlank, InputStream.readAllBytes). */
public final class Util {
    private Util() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Insertion-ordered map from alternating key/value arguments. */
    public static Map<String, Object> mapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("keyValues must be pairs");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
