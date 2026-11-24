package com.wchung.qrshare;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Tiny JSON-backed history of generated and scanned codes. */
public final class HistoryStore {

    private static final String FILE_NAME = "history.json";
    private static final int MAX_ENTRIES = 200;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Entry {
        public final String text;
        public final String kind;
        public final long time;

        Entry(String text, String kind, long time) {
            this.text = text;
            this.kind = kind;
            this.time = time;
        }
    }

    private HistoryStore() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static synchronized List<Entry> load(Context context) {
        List<Entry> entries = new ArrayList<>();
        File f = file(context);
        if (!f.exists()) {
            return entries;
        }
        try {
            String json = read(f);
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String text = o.optString("text", "");
                if (!text.isEmpty()) {
                    entries.add(new Entry(text, o.optString("kind", "text"), o.optLong("time", 0L)));
                }
            }
        } catch (IOException | JSONException e) {
            // A corrupt history file should never stop the app from opening.
        }
        return entries;
    }

    public static synchronized void add(Context context, String text, String kind) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        List<Entry> entries = load(context);
        if (!entries.isEmpty() && entries.get(0).text.equals(text)) {
            return;
        }
        List<Entry> updated = new ArrayList<>();
        updated.add(new Entry(text, kind, System.currentTimeMillis()));
        for (Entry e : entries) {
            if (updated.size() >= MAX_ENTRIES) {
                break;
            }
            if (!e.text.equals(text)) {
                updated.add(e);
            }
        }
        save(context, updated);
    }

    public static synchronized void remove(Context context, String text, long time) {
        List<Entry> entries = load(context);
        List<Entry> updated = new ArrayList<>();
        for (Entry e : entries) {
            if (!(e.time == time && e.text.equals(text))) {
                updated.add(e);
            }
        }
        save(context, updated);
    }

    public static synchronized void clear(Context context) {
        File f = file(context);
        if (f.exists() && !f.delete()) {
            // Nothing to do: the next save overwrites it anyway.
        }
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject();
            try {
                o.put("text", e.text);
                o.put("kind", e.kind);
                o.put("time", e.time);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(o);
        }
        try {
            FileOutputStream out = new FileOutputStream(file(context));
            try {
                out.write(array.toString().getBytes(UTF8));
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
            // History is best effort; never surface a failure to the user.
        }
    }

    private static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }
}
