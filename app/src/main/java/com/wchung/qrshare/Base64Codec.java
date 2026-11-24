package com.wchung.qrshare;

/**
 * Minimal, dependency-free Base64 so the file-packing logic stays pure Java and
 * can be unit tested on the JVM (android.util.Base64 is a stub outside a device).
 */
final class Base64Codec {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] INDEX = new int[128];

    static {
        for (int i = 0; i < INDEX.length; i++) {
            INDEX[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEX[ALPHABET[i]] = i;
        }
    }

    private Base64Codec() {
    }

    static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 2 < data.length) {
            int n = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            sb.append(ALPHABET[(n >> 18) & 63])
                    .append(ALPHABET[(n >> 12) & 63])
                    .append(ALPHABET[(n >> 6) & 63])
                    .append(ALPHABET[n & 63]);
            i += 3;
        }
        int remaining = data.length - i;
        if (remaining == 1) {
            int n = (data[i] & 0xFF) << 16;
            sb.append(ALPHABET[(n >> 18) & 63])
                    .append(ALPHABET[(n >> 12) & 63])
                    .append("==");
        } else if (remaining == 2) {
            int n = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(ALPHABET[(n >> 18) & 63])
                    .append(ALPHABET[(n >> 12) & 63])
                    .append(ALPHABET[(n >> 6) & 63])
                    .append('=');
        }
        return sb.toString();
    }

    static byte[] decode(String text) {
        int length = text.length();
        if (length % 4 != 0) {
            throw new IllegalArgumentException("Invalid Base64 length");
        }
        int padding = 0;
        if (length > 0 && text.charAt(length - 1) == '=') {
            padding++;
        }
        if (length > 1 && text.charAt(length - 2) == '=') {
            padding++;
        }
        int outLength = length / 4 * 3 - padding;
        byte[] out = new byte[outLength];
        int o = 0;
        for (int i = 0; i < length; i += 4) {
            int c0 = indexOf(text.charAt(i));
            int c1 = indexOf(text.charAt(i + 1));
            int c2 = text.charAt(i + 2) == '=' ? 0 : indexOf(text.charAt(i + 2));
            int c3 = text.charAt(i + 3) == '=' ? 0 : indexOf(text.charAt(i + 3));
            int n = (c0 << 18) | (c1 << 12) | (c2 << 6) | c3;
            if (o < outLength) {
                out[o++] = (byte) ((n >> 16) & 0xFF);
            }
            if (o < outLength) {
                out[o++] = (byte) ((n >> 8) & 0xFF);
            }
            if (o < outLength) {
                out[o++] = (byte) (n & 0xFF);
            }
        }
        return out;
    }

    private static int indexOf(char c) {
        int value = c < 128 ? INDEX[c] : -1;
        if (value < 0) {
            throw new IllegalArgumentException("Invalid Base64 character: " + c);
        }
        return value;
    }
}
