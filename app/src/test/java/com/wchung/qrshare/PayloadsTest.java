package com.wchung.qrshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PayloadsTest {

    @Test
    public void wifiEscapesDelimiters() {
        assertEquals("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss;H:true;;",
                Payloads.wifi("My;Net", "pa:ss", "WPA", true));
    }

    @Test
    public void wifiOmitsAnEmptyPassword() {
        assertEquals("WIFI:T:nopass;S:Guest;;", Payloads.wifi("Guest", "", "nopass", false));
        assertEquals("WIFI:T:WPA;S:Office;P:secret;;",
                Payloads.wifi("Office", "secret", "wpa", false));
    }

    @Test
    public void vcardHasTheRequiredProperties() {
        String card = Payloads.vcard("Asha Rao", "+919876543210", "asha@example.com", "Acme");
        assertTrue(card.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"));
        assertTrue(card.contains("FN:Asha Rao"));
        assertTrue(card.contains("TEL;TYPE=CELL:+919876543210"));
        assertTrue(card.contains("EMAIL;TYPE=INTERNET:asha@example.com"));
        assertTrue(card.endsWith("END:VCARD"));
    }

    @Test
    public void upiUsesInrAndSkipsEmptyFields() {
        assertEquals("upi://pay?pa=asha%40okhdfcbank&pn=Asha&cu=INR",
                Payloads.upi("asha@okhdfcbank", "Asha", "", ""));
        assertEquals("upi://pay?pa=a%40b&am=250&cu=INR&tn=Chai",
                Payloads.upi("a@b", "", "250", "Chai"));
    }

    @Test
    public void eventConvertsDatesToIcsFormat() {
        String event = Payloads.event("Standup", "Room 1", "2026-01-31 09:30", "2026-01-31 09:45");
        assertTrue(event.contains("DTSTART:20260131T093000"));
        assertTrue(event.contains("DTEND:20260131T094500"));
        assertTrue(event.contains("LOCATION:Room 1"));
    }

    @Test
    public void telStripsSpaces() {
        assertEquals("tel:+919876543210", Payloads.tel("+91 98765 43210"));
    }

    @Test
    public void totpCarriesTheStandardParameters() {
        String totp = Payloads.totp("JBSWY3DPEHPK3PXP", "Acme", "asha@acme.com");
        assertTrue(totp.startsWith("otpauth://totp/Acme%3Aasha%40acme.com?secret=JBSWY3DPEHPK3PXP"));
        assertTrue(totp.contains("algorithm=SHA1"));
        assertTrue(totp.contains("digits=6"));
        assertTrue(totp.contains("period=30"));
    }

    @Test
    public void schemeOfClassifiesCommonPayloads() {
        assertEquals("wifi", Payloads.schemeOf(Payloads.wifi("a", "b", "WPA", false)));
        assertEquals("upi", Payloads.schemeOf(Payloads.upi("a@b", "", "", "")));
        assertEquals("link", Payloads.schemeOf("https://example.com"));
        assertEquals("text", Payloads.schemeOf("hello"));
    }
}
