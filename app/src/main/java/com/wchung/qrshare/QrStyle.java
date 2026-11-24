package com.wchung.qrshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Persisted appearance of the generated symbol: colours and an optional centre logo. */
public final class QrStyle {

    private static final String PREFS = "qrshare";
    private static final String KEY_FG = "fg";
    private static final String KEY_BG = "bg";
    private static final String LOGO_NAME = "qr_logo.png";

    public static final int DEFAULT_FG = Color.BLACK;
    public static final int DEFAULT_BG = Color.WHITE;

    private QrStyle() {
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int foreground(Context context) {
        return prefs(context).getInt(KEY_FG, DEFAULT_FG);
    }

    public static int background(Context context) {
        return prefs(context).getInt(KEY_BG, DEFAULT_BG);
    }

    public static void setColors(Context context, int foreground, int background) {
        prefs(context).edit().putInt(KEY_FG, foreground).putInt(KEY_BG, background).apply();
    }

    private static File logoFile(Context context) {
        return new File(context.getFilesDir(), LOGO_NAME);
    }

    public static boolean hasLogo(Context context) {
        return logoFile(context).exists();
    }

    /** Decodes a picked image once and stores it as a small PNG. */
    public static void setLogo(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Cannot open the picked image");
        }
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(in);
        } finally {
            in.close();
        }
        if (bitmap == null) {
            throw new IOException("Cannot decode the picked image");
        }
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int left = (bitmap.getWidth() - side) / 2;
        int top = (bitmap.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(bitmap, left, top, side, side);
        Bitmap small = Bitmap.createScaledBitmap(square, 256, 256, true);

        FileOutputStream out = new FileOutputStream(logoFile(context));
        try {
            small.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            out.close();
        }
    }

    public static Bitmap logo(Context context) {
        File file = logoFile(context);
        return file.exists() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    public static void clearLogo(Context context) {
        File file = logoFile(context);
        if (file.exists() && !file.delete()) {
            // Best effort; the next setLogo overwrites it anyway.
        }
    }
}
