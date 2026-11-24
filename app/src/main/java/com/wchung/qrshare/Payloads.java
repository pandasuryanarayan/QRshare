package com.wchung.qrshare;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builders for the standard QR payload schemes (Wi-Fi, vCard, UPI, geo, VEVENT,
 * mailto, smsto, tel, otpauth). Pure Java so the escaping rules are unit tested.
 */
public final class Payloads {

    private static final String CRLF = "\r\n";

    private Payloads() {
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(nz(value), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return nz(value);
        }
    }

    /** Wi-Fi strings treat \\ ; , : and " as delimiters, so they must be escaped. */
    static String escapeWifi(String value) {
        String v = nz(value);
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** vCard 3.0 text values escape backslash, semicolon, comma and newline. */
    static String escapeVcard(String value) {
        String v = nz(value);
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' || c == ';' || c == ',') {
                sb.append('\\');
                sb.append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c != '\r') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String wifi(String ssid, String password, String security, boolean hidden) {
        String type = nz(security).isEmpty() ? "WPA" : nz(security);
        if (!type.equalsIgnoreCase("nopass")) {
            // WPA and WEP are upper case in the spec, "nopass" is lower case.
            type = type.toUpperCase(Locale.US);
        } else {
            type = "nopass";
        }
        StringBuilder sb = new StringBuilder("WIFI:");
        sb.append("T:").append(escapeWifi(type)).append(';');
        sb.append("S:").append(escapeWifi(ssid)).append(';');
        if (!nz(password).isEmpty()) {
            sb.append("P:").append(escapeWifi(password)).append(';');
        }
        if (hidden) {
            sb.append("H:true;");
        }
        sb.append(';');
        return sb.toString();
    }

    public static String vcard(String name, String phone, String email, String org) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCARD").append(CRLF);
        sb.append("VERSION:3.0").append(CRLF);
        sb.append("N:").append(escapeVcard(name)).append(";;;;").append(CRLF);
        sb.append("FN:").append(escapeVcard(name)).append(CRLF);
        if (!nz(org).isEmpty()) {
            sb.append("ORG:").append(escapeVcard(org)).append(CRLF);
        }
        if (!nz(phone).isEmpty()) {
            sb.append("TEL;TYPE=CELL:").append(escapeVcard(phone)).append(CRLF);
        }
        if (!nz(email).isEmpty()) {
            sb.append("EMAIL;TYPE=INTERNET:").append(escapeVcard(email)).append(CRLF);
        }
        sb.append("END:VCARD");
        return sb.toString();
    }

    public static String upi(String vpa, String payeeName, String amount, String note) {
        StringBuilder sb = new StringBuilder("upi://pay?pa=").append(enc(vpa));
        if (!nz(payeeName).isEmpty()) {
            sb.append("&pn=").append(enc(payeeName));
        }
        if (!nz(amount).isEmpty()) {
            sb.append("&am=").append(enc(amount));
        }
        sb.append("&cu=INR");
        if (!nz(note).isEmpty()) {
            sb.append("&tn=").append(enc(note));
        }
        return sb.toString();
    }

    static String icsDate(String input) {
        String s = nz(input);
        if (s.isEmpty()) {
            return "";
        }
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        in.setLenient(false);
        try {
            Date parsed = in.parse(s);
            if (parsed == null) {
                return s;
            }
            return new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).format(parsed);
        } catch (ParseException e) {
            return s.replaceAll("[^0-9T]", "");
        }
    }

    public static String event(String title, String location, String start, String end) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VEVENT").append(CRLF);
        sb.append("SUMMARY:").append(escapeVcard(title)).append(CRLF);
        if (!nz(location).isEmpty()) {
            sb.append("LOCATION:").append(escapeVcard(location)).append(CRLF);
        }
        String s = icsDate(start);
        if (!s.isEmpty()) {
            sb.append("DTSTART:").append(s).append(CRLF);
        }
        String e = icsDate(end);
        if (!e.isEmpty()) {
            sb.append("DTEND:").append(e).append(CRLF);
        }
        sb.append("END:VEVENT");
        return sb.toString();
    }

    public static String geo(String latitude, String longitude) {
        return "geo:" + nz(latitude) + "," + nz(longitude);
    }

    public static String mailto(String to, String subject, String body) {
        StringBuilder sb = new StringBuilder("mailto:").append(nz(to));
        char sep = '?';
        if (!nz(subject).isEmpty()) {
            sb.append(sep).append("subject=").append(enc(subject));
            sep = '&';
        }
        if (!nz(body).isEmpty()) {
            sb.append(sep).append("body=").append(enc(body));
        }
        return sb.toString();
    }

    public static String sms(String number, String body) {
        String n = nz(number);
        return nz(body).isEmpty() ? "sms:" + n : "smsto:" + n + ":" + nz(body);
    }

    public static String tel(String number) {
        return "tel:" + nz(number).replace(" ", "");
    }

    public static String totp(String secret, String issuer, String account) {
        String label = nz(issuer).isEmpty() ? nz(account) : nz(issuer) + ":" + nz(account);
        StringBuilder sb = new StringBuilder("otpauth://totp/").append(enc(label));
        sb.append("?secret=").append(nz(secret).replace(" ", "").toUpperCase(Locale.US));
        if (!nz(issuer).isEmpty()) {
            sb.append("&issuer=").append(enc(issuer));
        }
        sb.append("&algorithm=SHA1&digits=6&period=30");
        return sb.toString();
    }

    /** The scheme of a payload, used for the history label. */
    public static String schemeOf(String payload) {
        if (payload == null) {
            return "text";
        }
        String p = payload.trim();
        if (p.startsWith("WIFI:")) {
            return "wifi";
        }
        if (p.startsWith("BEGIN:VCARD")) {
            return "contact";
        }
        if (p.startsWith("BEGIN:VEVENT")) {
            return "event";
        }
        if (p.startsWith("upi://")) {
            return "upi";
        }
        if (p.startsWith("otpauth://")) {
            return "2fa";
        }
        if (p.startsWith("geo:")) {
            return "location";
        }
        if (p.startsWith("mailto:")) {
            return "email";
        }
        if (p.startsWith("sms:") || p.startsWith("smsto:")) {
            return "sms";
        }
        if (p.startsWith("tel:")) {
            return "phone";
        }
        if (p.startsWith("QRZ1|")) {
            return "file";
        }
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return "link";
        }
        return "text";
    }

    /** Parses the fields of a WIFI: payload, undoing the delimiter escaping. */
    static java.util.Map<String, String> parseWifi(String payload) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        if (payload == null) {
            return fields;
        }
        String body = payload.startsWith("WIFI:") ? payload.substring(5) : payload;
        StringBuilder current = new StringBuilder();
        java.util.List<String> tokens = new java.util.ArrayList<>();
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == ';') {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        for (String token : tokens) {
            int separator = token.indexOf(':');
            if (separator > 0) {
                fields.put(token.substring(0, separator), token.substring(separator + 1));
            }
        }
        return fields;
    }

    public static String wifiSsid(String payload) {
        String ssid = parseWifi(payload).get("S");
        return ssid == null ? "" : ssid;
    }

    public static String wifiPassword(String payload) {
        String password = parseWifi(payload).get("P");
        return password == null ? "" : password;
    }

    /** Reads one property out of a vCard payload, unescaping its value. */
    public static String vcardField(String payload, String key) {
        if (payload == null || key == null) {
            return "";
        }
        String[] lines = payload.split("\r\n|\n|\r");
        for (String line : lines) {
            if (line.startsWith(key + ":") || line.startsWith(key + ";")) {
                int separator = line.indexOf(':');
                if (separator < 0) {
                    return "";
                }
                return line.substring(separator + 1)
                        .replace("\\n", "\n")
                        .replace("\\,", ",")
                        .replace("\\;", ";");
            }
        }
        return "";
    }
}
