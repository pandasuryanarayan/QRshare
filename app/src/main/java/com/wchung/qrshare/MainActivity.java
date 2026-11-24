package com.wchung.qrshare;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "qrshare";
    private static final String PREF_ECC = "ecc";
    private static final long RENDER_DELAY_MS = 180L;
    private static final long CACHE_MAX_AGE_MS = 60L * 60L * 1000L;

    private static final int[] SWATCH_COLORS = {
            0xFF000000, 0xFF374151, 0xFFB91C1C, 0xFFB45309,
            0xFF047857, 0xFF0F766E, 0xFF1D4ED8, 0xFF6D28D9,
            0xFF9D174D, 0xFF111827, 0xFFFFFFFF, 0xFFF3F4F6
    };
    private static final int[] BACKGROUND_COLORS = {
            0xFFFFFFFF, 0xFFFFFBF0, 0xFF000000, 0xFF111827
    };

    private EditText input;
    private ImageView qrView;
    private TextView budgetView;
    private TextView placeholder;
    private Button clipboardButton;
    private View pagerBar;
    private TextView pageView;

    private final List<String> pages = new ArrayList<>();
    private int pageIndex;
    private int eccIndex = QrCapacity.DEFAULT_ECC;
    private Bitmap currentBitmap;
    private String lastRecorded;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable renderTask = this::render;

    private ActivityResultLauncher<Intent> scanLauncher;
    private ActivityResultLauncher<String> logoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        input = findViewById(R.id.qr_subtitle);
        qrView = findViewById(R.id.image_view_qr);
        budgetView = findViewById(R.id.tv_budget);
        placeholder = findViewById(R.id.tv_placeholder);
        clipboardButton = findViewById(R.id.btn_clipboard);
        pagerBar = findViewById(R.id.pager_bar);
        pageView = findViewById(R.id.tv_page);

        eccIndex = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(PREF_ECC, QrCapacity.DEFAULT_ECC);

        scanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String text = result.getData().getStringExtra(ScanActivity.EXTRA_RESULT);
                        if (text != null) {
                            input.setText(text);
                        }
                    }
                });

        logoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    try {
                        QrStyle.setLogo(this, uri);
                        render();
                    } catch (IOException e) {
                        toast(getString(R.string.logo_failed));
                    }
                });

        Button prev = findViewById(R.id.btn_prev);
        Button next = findViewById(R.id.btn_next);
        prev.setOnClickListener(v -> showPage(pageIndex - 1));
        next.setOnClickListener(v -> showPage(pageIndex + 1));
        clipboardButton.setOnClickListener(v -> pasteFromClipboard());

        qrView.setOnLongClickListener(this::showImageMenu);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(renderTask);
                handler.postDelayed(renderTask, RENDER_DELAY_MS);
            }
        });

        cleanupCache();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        recordHistory();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(renderTask);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- intents

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra("from_clipboard", false)) {
            pasteFromClipboard();
            return;
        }
        String action = intent.getAction();
        boolean sharing = Intent.ACTION_SEND.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action);
        if (!sharing) {
            if (pages.isEmpty()) {
                pages.add("");
                pageIndex = 0;
                updatePager();
                render();
            }
            return;
        }

        // A shared picture is almost always a screenshot of a code: read it,
        // rather than stuffing the image itself into a symbol.
        if (Intent.ACTION_SEND.equals(action) && intent.getType() != null
                && intent.getType().startsWith("image/")) {
            Uri uri = IntentUtil.streamOf(intent);
            if (uri != null && decodeSharedImage(uri)) {
                return;
            }
        }

        try {
            List<IntentUtil.Item> items = IntentUtil.readShared(this, intent);
            pages.clear();
            for (IntentUtil.Item item : items) {
                pages.add(item.text);
            }
            if (pages.isEmpty()) {
                pages.add("");
                toast(getString(R.string.unreadable_data));
            } else if (pages.size() > 1) {
                toast(getString(R.string.multiple_items_fmt, pages.size()));
            }
            pageIndex = 0;
            applyPage();
        } catch (IOException | SecurityException e) {
            pages.clear();
            pages.add("");
            applyPage();
            toast(getString(R.string.unreadable_data));
        }
    }

    private boolean decodeSharedImage(Uri uri) {
        try {
            String decoded = QrEngine.decodeUri(this, uri);
            pages.clear();
            pages.add(decoded);
            pageIndex = 0;
            applyPage();
            return true;
        } catch (NotFoundException e) {
            toast(getString(R.string.scan_no_code));
            return false;
        } catch (IOException | SecurityException e) {
            toast(getString(R.string.unreadable_data));
            return false;
        }
    }

    private void applyPage() {
        updatePager();
        String text = pages.isEmpty() ? "" : pages.get(pageIndex);
        if (!text.contentEquals(input.getText())) {
            input.setText(text);
        } else {
            render();
        }
    }

    private void updatePager() {
        if (pages.size() > 1) {
            pagerBar.setVisibility(View.VISIBLE);
            pageView.setText(getString(R.string.page_fmt, pageIndex + 1, pages.size()));
        } else {
            pagerBar.setVisibility(View.GONE);
        }
    }

    private void showPage(int index) {
        if (pages.size() <= 1) {
            return;
        }
        int target = Math.max(0, Math.min(pages.size() - 1, index));
        if (target == pageIndex) {
            return;
        }
        pageIndex = target;
        applyPage();
    }

    private void pasteFromClipboard() {
        String text = readClipboard();
        if (text == null) {
            return;
        }
        pages.clear();
        pages.add(text);
        pageIndex = 0;
        applyPage();
    }

    private String readClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        return text == null || text.length() == 0 ? null : text.toString();
    }

    // --------------------------------------------------------------- rendering

    private void render() {
        String text = input.getText().toString();
        updateClipboardOffer(text);

        if (text.trim().isEmpty()) {
            currentBitmap = null;
            qrView.setImageDrawable(null);
            showPlaceholder(R.string.qr_placeholder);
            budgetView.setText("");
            return;
        }

        int length = QrCapacity.utf8Length(text);
        int ecc = QrCapacity.pickEcc(eccIndex, length);

        if (ecc < 0) {
            currentBitmap = null;
            qrView.setImageDrawable(null);
            showPlaceholder(R.string.qr_placeholder_too_large);
            budgetView.setTextColor(ContextCompat.getColor(this, R.color.budget_over));
            budgetView.setText(getString(R.string.data_too_large_fmt,
                    IntentUtil.formatSize(length),
                    IntentUtil.formatSize(QrCapacity.maxBytes(QrCapacity.ECC_L))));
            return;
        }

        Bitmap logo = QrStyle.logo(this);
        if (logo != null) {
            int withLogo = QrCapacity.pickEcc(QrCapacity.ECC_Q, length);
            if (withLogo >= QrCapacity.ECC_Q) {
                ecc = Math.max(ecc, withLogo);
            } else {
                // No headroom for a logo at this size; the code wins.
                logo = null;
            }
        }

        try {
            currentBitmap = QrEngine.encode(text, ecc, QrStyle.foreground(this),
                    QrStyle.background(this), logo);
            placeholder.setVisibility(View.GONE);
            qrView.setVisibility(View.VISIBLE);
            qrView.setImageBitmap(currentBitmap);
            boolean tight = QrCapacity.isTight(length, ecc);
            budgetView.setTextColor(ContextCompat.getColor(this,
                    tight ? R.color.budget_warn : R.color.budget_ok));
            budgetView.setText(getString(R.string.budget_fmt,
                    IntentUtil.formatSize(length),
                    IntentUtil.formatSize(QrCapacity.maxBytes(ecc)),
                    getString(R.string.ecc_short, QrCapacity.name(ecc))));
        } catch (WriterException e) {
            currentBitmap = null;
            qrView.setImageDrawable(null);
            showPlaceholder(R.string.qr_placeholder_too_large);
            toast(getString(R.string.data_too_large));
        }
    }

    private void showPlaceholder(int textRes) {
        placeholder.setText(textRes);
        placeholder.setVisibility(View.VISIBLE);
        qrView.setVisibility(View.GONE);
    }

    private void updateClipboardOffer(String text) {
        boolean show = text.trim().isEmpty() && readClipboard() != null;
        clipboardButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void recordHistory() {
        String text = input.getText().toString();
        if (text.trim().isEmpty() || text.equals(lastRecorded)) {
            return;
        }
        lastRecorded = text;
        HistoryStore.add(this, text, Payloads.schemeOf(text));
        QrWidget.refresh(this);
    }

    // ------------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int id;
        switch (QrCapacity.clamp(eccIndex)) {
            case QrCapacity.ECC_L:
                id = R.id.ecc_l;
                break;
            case QrCapacity.ECC_Q:
                id = R.id.ecc_q;
                break;
            case QrCapacity.ECC_H:
                id = R.id.ecc_h;
                break;
            default:
                id = R.id.ecc_m;
                break;
        }
        MenuItem ecc = menu.findItem(id);
        if (ecc != null) {
            ecc.setChecked(true);
        }
        MenuItem use = menu.findItem(R.id.action_use);
        if (use != null) {
            String label = PayloadActions.label(this, input.getText().toString());
            use.setVisible(label != null);
            if (label != null) {
                use.setTitle(label);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_RETURN, true);
            scanLauncher.launch(intent);
            return true;
        }
        if (id == R.id.action_templates) {
            TemplateDialog.show(this, payload -> input.setText(payload));
            return true;
        }
        if (id == R.id.action_use) {
            PayloadActions.perform(this, input.getText().toString());
            return true;
        }
        if (id == R.id.action_style) {
            showStyleDialog();
            return true;
        }
        if (id == R.id.action_batch) {
            showBatchDialog();
            return true;
        }
        if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        if (id == R.id.action_save) {
            saveImage();
            return true;
        }
        if (id == R.id.action_share) {
            shareImage();
            return true;
        }
        if (id == R.id.action_copy) {
            copyText();
            return true;
        }
        if (id == R.id.ecc_l) {
            setEcc(QrCapacity.ECC_L);
            return true;
        }
        if (id == R.id.ecc_m) {
            setEcc(QrCapacity.ECC_M);
            return true;
        }
        if (id == R.id.ecc_q) {
            setEcc(QrCapacity.ECC_Q);
            return true;
        }
        if (id == R.id.ecc_h) {
            setEcc(QrCapacity.ECC_H);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setEcc(int index) {
        eccIndex = QrCapacity.clamp(index);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_ECC, eccIndex).apply();
        invalidateOptionsMenu();
        render();
    }

    private boolean showImageMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_list, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.copy) {
                copyImage();
                return true;
            }
            if (id == R.id.edit) {
                input.requestFocus();
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
                return true;
            }
            if (id == R.id.share) {
                shareImage();
                return true;
            }
            if (id == R.id.save) {
                saveImage();
                return true;
            }
            return false;
        });
        popup.show();
        return true;
    }

    // ----------------------------------------------------------------- style

    private void showStyleDialog() {
        String[] items = {
                getString(R.string.style_foreground),
                getString(R.string.style_background),
                QrStyle.hasLogo(this) ? getString(R.string.style_logo_change)
                        : getString(R.string.style_logo_add)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.style_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        pickForeground();
                    } else if (which == 1) {
                        pickBackground();
                    } else {
                        pickLogo();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickForeground() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.style_foreground)
                .setItems(R.array.swatch_names, (dialog, which) -> {
                    applyColors(SWATCH_COLORS[which], QrStyle.background(this));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickBackground() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.style_background)
                .setItems(R.array.background_names, (dialog, which) -> {
                    applyColors(QrStyle.foreground(this), BACKGROUND_COLORS[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyColors(int foreground, int background) {
        QrStyle.setColors(this, foreground, background);
        if (!QrEngine.hasEnoughContrast(foreground, background)) {
            toast(getString(R.string.style_low_contrast));
        }
        render();
    }

    private void pickLogo() {
        String[] items = {
                getString(R.string.style_logo_add),
                getString(R.string.style_logo_remove)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.style_logo)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        logoLauncher.launch("image/*");
                    } else {
                        QrStyle.clearLogo(this);
                        render();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ----------------------------------------------------------------- batch

    private void showBatchDialog() {
        EditText box = new EditText(this);
        box.setHint(R.string.batch_hint);
        box.setGravity(Gravity.TOP | Gravity.START);
        box.setMinLines(6);
        int padding = (int) (getResources().getDisplayMetrics().density * 20 + 0.5f);
        box.setPadding(padding, padding / 2, padding, 0);

        new AlertDialog.Builder(this)
                .setTitle(R.string.batch_title)
                .setView(box)
                .setPositiveButton(R.string.batch_generate, (dialog, which) -> {
                    List<String> entries = new ArrayList<>();
                    for (String line : box.getText().toString().split("\n")) {
                        if (!line.trim().isEmpty() && entries.size() < BatchExport.MAX_ENTRIES) {
                            entries.add(line.trim());
                        }
                    }
                    if (!entries.isEmpty()) {
                        exportBatch(entries);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportBatch(List<String> entries) {
        final int ecc = eccIndex;
        final int foreground = QrStyle.foreground(this);
        final int background = QrStyle.background(this);
        final Bitmap logo = QrStyle.logo(this);
        final Context context = getApplicationContext();

        new Thread(() -> {
            String message;
            File file = null;
            try {
                BatchExport.Result result = BatchExport.export(context, entries, ecc,
                        foreground, background, logo);
                file = result.file;
                message = getString(R.string.batch_done, result.written, result.skipped);
            } catch (IOException e) {
                message = getString(R.string.batch_failed);
            }
            final String finalMessage = message;
            final File finalFile = file;
            runOnUiThread(() -> {
                toast(finalMessage);
                if (finalFile != null) {
                    shareFile(finalFile, "application/zip");
                }
            });
        }).start();
    }

    private void shareFile(File file, String mime) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.menu_share)));
    }

    // ---------------------------------------------------------------- actions

    private void copyText() {
        String text = input.getText().toString();
        if (text.trim().isEmpty()) {
            toast(getString(R.string.nothing_to_share));
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
            toast(getString(R.string.copied));
        }
    }

    private void copyImage() {
        if (currentBitmap == null) {
            toast(getString(R.string.nothing_to_share));
            return;
        }
        try {
            Uri uri = uriForPng();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(),
                        getString(R.string.app_name), uri));
                toast(getString(R.string.copied));
            }
        } catch (IOException e) {
            toast(getString(R.string.save_failed));
        }
    }

    private void shareImage() {
        if (currentBitmap == null) {
            toast(getString(R.string.nothing_to_share));
            return;
        }
        try {
            Uri uri = uriForPng();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.menu_share)));
            recordHistory();
        } catch (IOException e) {
            toast(getString(R.string.save_failed));
        }
    }

    private void saveImage() {
        if (currentBitmap == null) {
            toast(getString(R.string.nothing_to_share));
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast(getString(R.string.save_unsupported));
            return;
        }
        String name = "qrshare_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/QRshare");
        OutputStream out = null;
        try {
            Uri uri = getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                toast(getString(R.string.save_failed));
                return;
            }
            out = getContentResolver().openOutputStream(uri);
            if (out == null) {
                toast(getString(R.string.save_failed));
                return;
            }
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            toast(getString(R.string.saved_to_gallery));
            recordHistory();
        } catch (IOException | SecurityException e) {
            toast(getString(R.string.save_failed));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Already reported or irrelevant.
                }
            }
        }
    }

    private Uri uriForPng() throws IOException {
        File dir = new File(getCacheDir(), "qrcodes");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create the export folder");
        }
        File file = new File(dir, "qr_" + System.currentTimeMillis() + ".png");
        FileOutputStream out = new FileOutputStream(file);
        try {
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            out.close();
        }
        return FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
    }

    private void cleanupCache() {
        File dir = new File(getCacheDir(), "qrcodes");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS;
        for (File file : files) {
            if (file.lastModified() < cutoff && !file.delete()) {
                // Best effort only.
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
