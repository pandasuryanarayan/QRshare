package com.wchung.qrshare;

import java.nio.charset.Charset;

/**
 * Pure capacity arithmetic for QR codes, kept free of Android imports so it can
 * be unit tested on the JVM.
 *
 * Index order runs from the least redundancy (most data) to the most:
 * 0 = L (7%), 1 = M (15%), 2 = Q (25%), 3 = H (30%).
 * Byte capacities are for a version 40 symbol in 8-bit byte mode.
 */
public final class QrCapacity {

    public static final int ECC_L = 0;
    public static final int ECC_M = 1;
    public static final int ECC_Q = 2;
    public static final int ECC_H = 3;

    public static final int DEFAULT_ECC = ECC_M;

    private static final int[] MAX_BYTES = {2953, 2331, 1663, 1273};
    private static final String[] NAMES = {"L", "M", "Q", "H"};

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private QrCapacity() {
    }

    public static int clamp(int index) {
        if (index < 0) {
            return ECC_L;
        }
        if (index >= MAX_BYTES.length) {
            return ECC_H;
        }
        return index;
    }

    public static int maxBytes(int eccIndex) {
        return MAX_BYTES[clamp(eccIndex)];
    }

    public static String name(int eccIndex) {
        return NAMES[clamp(eccIndex)];
    }

    /** Byte length of the payload once encoded as UTF-8, which is what the symbol stores. */
    public static int utf8Length(String text) {
        return text == null ? 0 : text.getBytes(UTF8).length;
    }

    /**
     * Chooses the strongest error correction that still fits, starting at the
     * user's preference and stepping down towards L.
     *
     * @return the usable ECC index, or -1 when the payload does not fit at all.
     */
    public static int pickEcc(int requestedIndex, int byteLength) {
        for (int i = clamp(requestedIndex); i >= ECC_L; i--) {
            if (byteLength <= MAX_BYTES[i]) {
                return i;
            }
        }
        return -1;
    }

    /** True once the payload is inside the symbol but close enough to the limit to warn. */
    public static boolean isTight(int byteLength, int eccIndex) {
        int max = maxBytes(eccIndex);
        return max > 0 && byteLength > (max * 4) / 5;
    }

    public static boolean fits(String text, int eccIndex) {
        return utf8Length(text) <= maxBytes(eccIndex);
    }
}
