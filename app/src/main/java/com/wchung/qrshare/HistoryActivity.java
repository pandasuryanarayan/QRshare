package com.wchung.qrshare;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private LinearLayout list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        list = findViewById(R.id.history_list);
        empty = findViewById(R.id.history_empty);
        toolbar.setNavigationOnClickListener(v -> finish());

        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmClear() {
        if (HistoryStore.load(this).isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.history_clear, (dialog, which) -> {
                    HistoryStore.clear(this);
                    QrWidget.refresh(this);
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void render() {
        list.removeAllViews();
        List<HistoryStore.Entry> entries = HistoryStore.load(this);
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        for (HistoryStore.Entry entry : entries) {
            View row = inflater.inflate(R.layout.item_history, list, false);
            TextView text = row.findViewById(R.id.item_text);
            TextView meta = row.findViewById(R.id.item_meta);

            text.setText(entry.text);
            meta.setText(String.format(Locale.getDefault(), "%s · %s",
                    entry.kind, format.format(new Date(entry.time))));

            row.setOnClickListener(v -> copy(entry.text));
            row.setOnLongClickListener(v -> {
                confirmDelete(entry);
                return true;
            });
            list.addView(row);
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.app_name), text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(HistoryStore.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_delete)
                .setMessage(entry.text.length() > 120
                        ? entry.text.substring(0, 120) + "…" : entry.text)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    HistoryStore.remove(this, entry.text, entry.time);
                    QrWidget.refresh(this);
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
