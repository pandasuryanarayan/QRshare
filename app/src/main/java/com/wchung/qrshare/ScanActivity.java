package com.wchung.qrshare;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera-based QR reader built on CameraX and ZXing, with no Play Services. */
public class ScanActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "scan_result";
    public static final String EXTRA_RETURN = "return_result";

    private static final long RETRY_MS = 220L;

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private MultiFormatReader reader;
    private Camera camera;

    private volatile boolean delivering;
    private volatile boolean busy;
    private long lastAttempt;
    private boolean torchOn;
    private boolean returnResult;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String> imageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        returnResult = getIntent().getBooleanExtra(EXTRA_RETURN, false);
        previewView = findViewById(R.id.preview);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        reader = new MultiFormatReader();
        reader.setHints(hints);

        cameraExecutor = Executors.newSingleThreadExecutor();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        toast(getString(R.string.scan_permission_needed));
                        finish();
                    }
                });

        imageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        decodeFromImage(uri);
                    }
                });

        Button torch = findViewById(R.id.btn_torch);
        Button gallery = findViewById(R.id.btn_gallery);
        Button close = findViewById(R.id.btn_close);

        torch.setOnClickListener(v -> toggleTorch());
        gallery.setOnClickListener(v -> imageLauncher.launch("image/*"));
        close.setOnClickListener(v -> finish());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyze);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, R.string.scan_permission_needed, Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            toast(getString(R.string.torch_unavailable));
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
    }

    @ExperimentalGetImage
    private void analyze(@NonNull ImageProxy image) {
        long now = System.currentTimeMillis();
        if (busy || delivering || now - lastAttempt < RETRY_MS) {
            image.close();
            return;
        }
        lastAttempt = now;
        try {
            Image media = image.getImage();
            if (media == null) {
                return;
            }
            int width = media.getWidth();
            int height = media.getHeight();
            byte[] luma = toLuma(image, width, height);
            if (luma == null) {
                return;
            }

            LuminanceSource source =
                    new PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false);
            int rotation = image.getImageInfo().getRotationDegrees();
            for (int i = 0; i < rotation / 90; i++) {
                source = source.rotateCounterClockwise();
            }

            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decodeWithState(bitmap);
            if (result != null && result.getText() != null) {
                delivering = true;
                deliver(result.getText());
            }
        } catch (NotFoundException | ArrayIndexOutOfBoundsException e) {
            // No code in this frame; keep scanning.
        } catch (Exception e) {
            // A single bad frame must never kill the analysis pipeline.
        } finally {
            reader.reset();
            image.close();
        }
    }

    private byte[] toLuma(ImageProxy image, int width, int height) {
        try {
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int limit = buffer.limit();
            byte[] out = new byte[width * height];

            if (pixelStride == 1 && rowStride == width && limit >= out.length) {
                buffer.position(0);
                buffer.get(out, 0, out.length);
                return out;
            }

            byte[] row = new byte[Math.max(rowStride, width)];
            int pos = 0;
            for (int y = 0; y < height; y++) {
                int start = y * rowStride;
                if (start >= limit) {
                    break;
                }
                int len = Math.min(rowStride, limit - start);
                buffer.position(start);
                buffer.get(row, 0, len);
                for (int x = 0; x < width; x++) {
                    int index = x * pixelStride;
                    if (index < len) {
                        out[pos + x] = row[index];
                    }
                }
                pos += width;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void decodeFromImage(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                toast(getString(R.string.scan_no_code));
                return;
            }
            Bitmap bitmap;
            try {
                bitmap = BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
            if (bitmap == null) {
                toast(getString(R.string.scan_no_code));
                return;
            }
            String text = QrEngine.decode(bitmap);
            deliver(text);
        } catch (NotFoundException e) {
            toast(getString(R.string.scan_no_code));
        } catch (Exception e) {
            toast(getString(R.string.scan_no_code));
        }
    }

    private void deliver(String text) {
        runOnUiThread(() -> {
            vibrate();
            if (returnResult) {
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT, text);
                setResult(RESULT_OK, data);
                finish();
            } else {
                showResultDialog(text);
            }
        });
    }

    private void showResultDialog(String text) {
        String preview = text.length() > 400 ? text.substring(0, 400) + "…" : text;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.scan_result_title)
                .setMessage(preview)
                .setPositiveButton(R.string.menu_copy, (dialog, which) -> copy(text))
                .setNegativeButton(R.string.scan_close, (dialog, which) -> finish());

        if (IntentUtil.isFilePayload(text)) {
            builder.setNeutralButton(R.string.scan_result_save_file,
                    (dialog, which) -> saveFile(text));
        } else if (isOpenable(text)) {
            builder.setNeutralButton(R.string.scan_result_open, (dialog, which) -> open(text));
        }

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            if (!isFinishing()) {
                finish();
            }
        });
        dialog.show();
    }

    private boolean isOpenable(String text) {
        String t = text.trim();
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("tel:")
                || t.startsWith("mailto:") || t.startsWith("geo:") || t.startsWith("smsto:")
                || t.startsWith("sms:") || t.startsWith("upi://");
    }

    private void open(String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(text.trim()));
            startActivity(intent);
        } catch (Exception e) {
            toast(getString(R.string.unreadable_data));
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.app_name), text));
            toast(getString(R.string.copied));
        }
    }

    private void saveFile(String payload) {
        byte[] bytes = IntentUtil.unpackFile(payload);
        String mime = IntentUtil.mimeOfFilePayload(payload);
        if (bytes == null) {
            toast(getString(R.string.file_save_failed));
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null), "shared");
            if (!dir.exists() && !dir.mkdirs()) {
                toast(getString(R.string.file_save_failed));
                return;
            }
            String name = "qrshare_" + System.currentTimeMillis()
                    + extensionFor(mime);
            File file = new File(dir, name);
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            toast(getString(R.string.file_saved) + " — " + file.getAbsolutePath());
        } catch (Exception e) {
            toast(getString(R.string.file_save_failed));
        }
    }

    private String extensionFor(String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (m.contains("png")) {
            return ".png";
        }
        if (m.contains("jpeg") || m.contains("jpg")) {
            return ".jpg";
        }
        if (m.contains("pdf")) {
            return ".pdf";
        }
        if (m.contains("json")) {
            return ".json";
        }
        if (m.contains("zip")) {
            return ".zip";
        }
        if (m.startsWith("text/")) {
            return ".txt";
        }
        return ".bin";
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(40);
            }
        } catch (Exception ignored) {
            // Haptics are a nicety, not a requirement.
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
