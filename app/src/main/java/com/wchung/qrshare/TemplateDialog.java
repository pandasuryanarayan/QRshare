package com.wchung.qrshare;

import android.app.Activity;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/** Two-step picker that fills a form and hands back a ready-made payload. */
public final class TemplateDialog {

    public interface Callback {
        void onPayload(String payload);
    }

    private static final int[] TITLES = {
            R.string.template_wifi,
            R.string.template_contact,
            R.string.template_upi,
            R.string.template_event,
            R.string.template_geo,
            R.string.template_email,
            R.string.template_sms,
            R.string.template_tel,
            R.string.template_totp
    };

    private static final int[] FIELDS = {
            R.array.fields_wifi,
            R.array.fields_contact,
            R.array.fields_upi,
            R.array.fields_event,
            R.array.fields_geo,
            R.array.fields_email,
            R.array.fields_sms,
            R.array.fields_tel,
            R.array.fields_totp
    };

    private TemplateDialog() {
    }

    public static void show(Activity activity, Callback callback) {
        String[] names = new String[TITLES.length];
        for (int i = 0; i < TITLES.length; i++) {
            names[i] = activity.getString(TITLES[i]);
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.template_title)
                .setItems(names, (dialog, which) -> showForm(activity, which, callback))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void showForm(Activity activity, int which, Callback callback) {
        String[] labels = activity.getResources().getStringArray(FIELDS[which]);
        final EditText[] inputs = new EditText[labels.length];

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, 20);
        container.setPadding(horizontal, dp(activity, 8), horizontal, 0);

        for (int i = 0; i < labels.length; i++) {
            EditText field = new EditText(activity);
            field.setHint(labels[i]);
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            field.setSingleLine(false);
            inputs[i] = field;
            container.addView(field, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(container);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.template_fill)
                .setView(scroll)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String[] values = new String[inputs.length];
                    for (int i = 0; i < inputs.length; i++) {
                        values[i] = inputs[i].getText().toString();
                    }
                    if (values[0].trim().isEmpty()) {
                        Toast.makeText(activity, R.string.template_fill, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String payload = build(which, values);
                    callback.onPayload(payload);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    static String build(int which, String[] values) {
        switch (which) {
            case 0:
                return Payloads.wifi(values[0], values[1], values[2], isYes(values[3]));
            case 1:
                return Payloads.vcard(values[0], values[1], values[2], values[3]);
            case 2:
                return Payloads.upi(values[0], values[1], values[2], values[3]);
            case 3:
                return Payloads.event(values[0], values[1], values[2], values[3]);
            case 4:
                return Payloads.geo(values[0], values[1]);
            case 5:
                return Payloads.mailto(values[0], values[1], values[2]);
            case 6:
                return Payloads.sms(values[0], values[1]);
            case 7:
                return Payloads.tel(values[0]);
            case 8:
                return Payloads.totp(values[0], values[1], values[2]);
            default:
                return values[0];
        }
    }

    private static boolean isYes(String value) {
        String v = value == null ? "" : value.trim();
        return v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("y") || v.equalsIgnoreCase("true");
    }

    private static int dp(Activity activity, int value) {
        return (int) (activity.getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
