package com.wchung.qrshare;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;

public class IntentUtilTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Test
    public void textKeepsItsOwnLineEndings() {
        byte[] bytes = "line one\nline two".getBytes(UTF8);
        assertEquals("line one\nline two", IntentUtil.decodeText(bytes));
    }

    @Test
    public void stripsUtf8Bom() {
        byte[] body = "hello".getBytes(UTF8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertEquals("hello", IntentUtil.decodeText(withBom));
    }

    @Test
    public void filePayloadRoundTripsThroughCompression() {
        byte[] original = new byte[600];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) ('a' + (i % 5));
        }
        String payload = IntentUtil.packFile("text/plain", original);
        assertTrue(payload.startsWith("QRZ1|text/plain|gz|"));
        assertTrue(IntentUtil.isFilePayload(payload));
        assertEquals("text/plain", IntentUtil.mimeOfFilePayload(payload));
        assertArrayEquals(original, IntentUtil.unpackFile(payload));
    }

    @Test
    public void randomDataFallsBackToRaw() {
        byte[] random = new byte[256];
        new java.util.Random(7).nextBytes(random);
        String payload = IntentUtil.packFile("application/octet-stream", random);
        assertTrue(payload.contains("|raw|"));
        assertArrayEquals(random, IntentUtil.unpackFile(payload));
    }

    @Test
    public void plainTextIsNotAFilePayload() {
        assertFalse(IntentUtil.isFilePayload("https://example.com"));
        assertNull(IntentUtil.mimeOfFilePayload("nope"));
        assertNull(IntentUtil.unpackFile("nope"));
    }
}
