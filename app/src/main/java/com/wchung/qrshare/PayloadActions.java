package com.wchung.qrshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.widget.Toast;

/** Turns a decoded payload into the action a user actually wants to take. */
public final class PayloadActions {

    private PayloadActions() {
    }

    /** @return a button label, or null when there is nothing sensible to do. */
    public static String label(Context context, String payload) {
        String kind = Payloads.schemeOf(payload);
        switch (kind) {
            case "link":
                return context.getString(R.string.action_open_link);
            case "phone":
                return context.getString(R.string.action_call);
            case "email":
                return context.getString(R.string.action_email);
            case "sms":
                return context.getString(R.string.action_sms);
            case "location":
                return context.getString(R.string.action_map);
            case "upi":
                return context.getString(R.string.action_upi);
            case "wifi":
                return context.getString(R.string.action_wifi);
            case "contact":
                return context.getString(R.string.action_contact);
            default:
                return null;
        }
    }

    public static boolean perform(Activity activity, String payload) {
        String kind = Payloads.schemeOf(payload);
        try {
            switch (kind) {
                case "wifi":
                    return openWifi(activity, payload);
                case "contact":
                    return addContact(activity, payload);
                case "link":
                case "phone":
                case "email":
                case "sms":
                case "location":
                case "upi":
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payload.trim())));
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            Toast.makeText(activity, R.string.unreadable_data, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Android has no API to join a network silently, so the useful thing is to
     * put the password on the clipboard and open the Wi-Fi picker.
     */
    private static boolean openWifi(Activity activity, String payload) {
        String ssid = Payloads.wifiSsid(payload);
        String password = Payloads.wifiPassword(payload);
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && password != null && !password.isEmpty()) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(activity.getString(R.string.app_name), password));
        }
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        String message = password == null || password.isEmpty()
                ? activity.getString(R.string.wifi_open, ssid)
                : activity.getString(R.string.wifi_open_with_password, ssid);
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        return true;
    }

    private static boolean addContact(Activity activity, String payload) {
        Intent intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
        intent.putExtra(ContactsContract.Intents.Insert.NAME, Payloads.vcardField(payload, "FN"));
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, Payloads.vcardField(payload, "TEL"));
        intent.putExtra(ContactsContract.Intents.Insert.EMAIL, Payloads.vcardField(payload, "EMAIL"));
        activity.startActivity(intent);
        return true;
    }
}
