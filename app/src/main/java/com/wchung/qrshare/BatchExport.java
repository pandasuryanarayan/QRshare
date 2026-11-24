package com.wchung.qrshare;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.zxing.WriterException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Renders a list of payloads into one zip of PNGs. */
public final class BatchExport {

    public static final int MAX_ENTRIES = 100;

    public static final class Result {
        public final File file;
        public final int written;
        public final int skipped;

        Result(File file, int written, int skipped) {
            this.file = file;
            this.written = written;
            this.skipped = skipped;
        }
    }

    private BatchExport() {
    }

    public static Result export(Context context, List<String> entries, int eccIndex,
                                int foreground, int background, Bitmap logo) throws IOException {
        File dir = new File(context.getCacheDir(), "qrcodes");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create the export folder");
        }
        File zip = new File(dir, "batch_" + System.currentTimeMillis() + ".zip");
        int written = 0;
        int skipped = 0;

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip));
        try {
            for (int i = 0; i < entries.size(); i++) {
                String payload = entries.get(i);
                if (payload.trim().isEmpty()) {
                    continue;
                }
                if (!QrCapacity.fits(payload, QrCapacity.ECC_L)) {
                    skipped++;
                    continue;
                }
                int ecc = QrCapacity.pickEcc(eccIndex, QrCapacity.utf8Length(payload));
                if (ecc < 0) {
                    skipped++;
                    continue;
                }
                try {
                    Bitmap bitmap = QrEngine.encode(payload, ecc, foreground, background, logo);
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
                    out.putNextEntry(new ZipEntry(String.format(
                            java.util.Locale.US, "qr_%03d.png", written + 1)));
                    out.write(png.toByteArray());
                    out.closeEntry();
                    written++;
                } catch (WriterException e) {
                    skipped++;
                }
            }
        } finally {
            out.close();
        }
        return new Result(zip, written, skipped);
    }
}
