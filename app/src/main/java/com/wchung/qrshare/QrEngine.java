package com.wchung.qrshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Bitmap-level QR encoding and decoding. */
public final class QrEngine {

    /**
     * The exported symbol is rendered at a fixed high resolution instead of the
     * screen width, so a shared PNG looks the same on every device and dense
     * symbols keep enough pixels per module to stay scannable.
     */
    public static final int EXPORT_SIZE = 1024;

    /** Widget bitmaps travel through RemoteViews, which has a small transaction limit. */
    public static final int WIDGET_SIZE = 320;

    private static final int MAX_DECODE_DIMENSION = 2048;

    private static final ErrorCorrectionLevel[] LEVELS = {
            ErrorCorrectionLevel.L,
            ErrorCorrectionLevel.M,
            ErrorCorrectionLevel.Q,
            ErrorCorrectionLevel.H
    };

    private QrEngine() {
    }

    public static Bitmap encode(String data, int eccIndex, int foreground, int background)
            throws WriterException {
        return encode(data, eccIndex, foreground, background, null, EXPORT_SIZE);
    }

    public static Bitmap encode(String data, int eccIndex, int foreground, int background,
                                Bitmap logo) throws WriterException {
        return encode(data, eccIndex, foreground, background, logo, EXPORT_SIZE);
    }

    public static Bitmap encode(String data, int eccIndex, int foreground, int background,
                                Bitmap logo, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, LEVELS[QrCapacity.clamp(eccIndex)]);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        BitMatrix matrix = new QRCodeWriter().encode(
                data, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = toBitmap(matrix, foreground, background);
        return logo == null ? bitmap : withLogo(bitmap, logo, background);
    }

    private static Bitmap toBitmap(BitMatrix matrix, int foreground, int background) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[rowOffset + x] = matrix.get(x, y) ? foreground : background;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    /** Draws the logo in the centre, on a plate of the background colour so it reads as a gap. */
    private static Bitmap withLogo(Bitmap base, Bitmap logo, int background) {
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        int size = out.getWidth();
        int target = Math.max(24, (int) (size * 0.22f));
        int plate = target + Math.max(8, (int) (target * 0.10f));
        float left = (size - plate) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(background);
        canvas.drawRoundRect(new RectF(left, left, left + plate, left + plate),
                plate * 0.18f, plate * 0.18f, paint);

        Bitmap scaled = Bitmap.createScaledBitmap(logo, target, target, true);
        float inset = (plate - target) / 2f;
        canvas.drawBitmap(scaled, left + inset, left + inset, null);
        return out;
    }

    /**
     * Decodes a QR code from a bitmap, trying harder and also attempting an
     * inverted read so light-on-dark codes still work.
     */
    public static String decode(Bitmap bitmap) throws NotFoundException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

        MultiFormatReader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
            return result.getText();
        } catch (NotFoundException first) {
            Result result = reader.decode(
                    new BinaryBitmap(new HybridBinarizer(source.invert())), hints);
            return result.getText();
        } finally {
            reader.reset();
        }
    }

    /** Decodes a QR code from any image URI, downsampling large pictures first. */
    public static String decodeUri(Context context, Uri uri) throws NotFoundException, IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Cannot open " + uri);
        }
        Bitmap bitmap;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            bitmap = decodeSampled(buffer.toByteArray());
        } finally {
            in.close();
        }
        if (bitmap == null) {
            throw new NotFoundException("Not an image");
        }
        return decode(bitmap);
    }

    private static Bitmap decodeSampled(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (largest / sample > MAX_DECODE_DIMENSION) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    /** Colours that keep enough contrast for a reliable scan. */
    public static boolean hasEnoughContrast(int foreground, int background) {
        double diff = Math.abs(luminance(foreground) - luminance(background)) / 255.0d;
        return diff >= 0.35d;
    }

    private static double luminance(int color) {
        return Color.red(color) * 0.299d
                + Color.green(color) * 0.587d
                + Color.blue(color) * 0.114d;
    }
}
