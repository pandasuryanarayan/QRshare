package com.wchung.qrshare;

import android.graphics.Bitmap;
import android.graphics.Color;

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
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, LEVELS[QrCapacity.clamp(eccIndex)]);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        BitMatrix matrix = new QRCodeWriter().encode(
                data, BarcodeFormat.QR_CODE, EXPORT_SIZE, EXPORT_SIZE, hints);
        return toBitmap(matrix, foreground, background);
    }

    private static Bitmap toBitmap(BitMatrix matrix, int foreground, int background) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[rowOffset + x] = matrix.get(x, y) ? foreground : background;
            }
            index += width;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
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

    /** Colours that keep enough contrast for a reliable scan. */
    public static boolean hasEnoughContrast(int foreground, int background) {
        double fg = luminance(foreground);
        double bg = luminance(background);
        double diff = Math.abs(fg - bg) / 255.0d;
        return diff >= 0.35d;
    }

    private static double luminance(int color) {
        double r = Color.red(color) * 0.299d;
        double g = Color.green(color) * 0.587d;
        double b = Color.blue(color) * 0.114d;
        return r + g + b;
    }
}
