package com.wchung.qrshare;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Turns a shared intent into QR payload strings. */
public final class IntentUtil {

    /** Guard against pulling an enormous file into memory before we even know if it fits. */
    private static final int MAX_READ = 2 * 1024 * 1024;

    private static final int MAX_ITEMS = 12;

    /** Prefix for a file carried inside a QR code: QRZ1|mime|codec|base64. */
    private static final String FILE_PREFIX = "QRZ1|";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    public static final class Item {
        public final String text;
        public final String label;

        Item(String text, String label) {
            this.text = text;
            this.label = label;
        }
    }

    private IntentUtil() {
    }

    public static List<Item> readShared(Context context, Intent intent) throws IOException {
        List<Item> items = new ArrayList<>();
        if (intent == null) {
            return items;
        }

        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null && text.length() > 0) {
            items.add(new Item(text.toString(), "text"));
            return items;
        }

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri stream = streamOf(intent);
            if (stream != null) {
                Item item = readUri(context, stream, intent.getType());
                if (item != null) {
                    items.add(item);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> uris = streamsOf(intent);
            if (uris != null) {
                for (Uri uri : uris) {
                    if (items.size() >= MAX_ITEMS) {
                        break;
                    }
                    Item item = readUri(context, uri, intent.getType());
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }
        return items;
    }

    @SuppressWarnings("deprecation")
    public static Uri streamOf(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<Uri> streamsOf(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    }

    private static Item readUri(Context context, Uri uri, String declaredMime) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mime = resolver.getType(uri);
        if (mime == null) {
            mime = declaredMime == null ? "application/octet-stream" : declaredMime;
        }
        String name = displayName(context, uri);

        InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = readAll(in);
        } finally {
            in.close();
        }

        if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")) {
            return new Item(decodeText(bytes), name == null ? "text" : name);
        }
        return new Item(packFile(mime, bytes), name == null ? mime : name);
    }

    private static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Some providers refuse the query; the mime type is a fine fallback.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_READ) {
                throw new IOException("Content larger than " + MAX_READ + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Reads text exactly as it was, honouring a UTF-8 BOM and falling back to
     * Latin-1 when the bytes are not valid UTF-8. The old implementation appended
     * a newline per line and mangled the content.
     */
    static String decodeText(byte[] bytes) {
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            String utf8 = new String(bytes, offset, bytes.length - offset, "UTF-8");
            if (utf8.indexOf('\uFFFD') < 0) {
                return utf8;
            }
        } catch (UnsupportedEncodingException ignored) {
            // UTF-8 always exists; this cannot happen.
        }
        return new String(bytes, offset, bytes.length - offset, LATIN1);
    }

    /** Compresses and base64s a file so more of it fits inside the symbol. */
    static String packFile(String mime, byte[] bytes) {
        byte[] body = bytes;
        String codec = "raw";
        byte[] deflated = deflate(bytes);
        if (deflated != null && deflated.length < bytes.length) {
            body = deflated;
            codec = "gz";
        }
        String encoded = Base64Codec.encode(body);
        return FILE_PREFIX + mime + "|" + codec + "|" + encoded;
    }

    public static boolean isFilePayload(String payload) {
        return payload != null && payload.startsWith(FILE_PREFIX);
    }

    public static String mimeOfFilePayload(String payload) {
        String[] parts = split(payload);
        return parts == null ? null : parts[0];
    }

    /** @return the original bytes, or null when the payload is not a packed file. */
    public static byte[] unpackFile(String payload) {
        String[] parts = split(payload);
        if (parts == null) {
            return null;
        }
        try {
            byte[] body = Base64Codec.decode(parts[2]);
            return "gz".equals(parts[1]) ? inflate(body) : body;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String[] split(String payload) {
        if (!isFilePayload(payload)) {
            return null;
        }
        String rest = payload.substring(FILE_PREFIX.length());
        int firstBar = rest.indexOf('|');
        if (firstBar <= 0) {
            return null;
        }
        String mime = rest.substring(0, firstBar);
        String tail = rest.substring(firstBar + 1);
        int secondBar = tail.indexOf('|');
        if (secondBar <= 0) {
            return null;
        }
        String codec = tail.substring(0, secondBar);
        String data = tail.substring(secondBar + 1);
        return new String[]{mime, codec, data};
    }

    private static byte[] deflate(byte[] input) {
        try {
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try {
                deflater.setInput(input);
                deflater.finish();
                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 2));
                byte[] buffer = new byte[4096];
                while (!deflater.finished()) {
                    int written = deflater.deflate(buffer);
                    if (written <= 0) {
                        break;
                    }
                    out.write(buffer, 0, written);
                }
                return out.toByteArray();
            } finally {
                deflater.end();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] inflate(byte[] input) {
        try {
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(input);
                ByteArrayOutputStream out = new ByteArrayOutputStream(input.length * 3);
                byte[] buffer = new byte[4096];
                while (!inflater.finished()) {
                    int written = inflater.inflate(buffer);
                    if (written <= 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) {
                            break;
                        }
                    }
                    out.write(buffer, 0, written);
                }
                return out.toByteArray();
            } finally {
                inflater.end();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static String formatSize(int bytes) {
        return String.format(Locale.US, "%,d", bytes);
    }
}
