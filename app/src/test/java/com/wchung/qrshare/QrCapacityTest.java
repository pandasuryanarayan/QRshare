package com.wchung.qrshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrCapacityTest {

    @Test
    public void levelsRunFromMostDataToMostRobust() {
        assertTrue(QrCapacity.maxBytes(QrCapacity.ECC_L) > QrCapacity.maxBytes(QrCapacity.ECC_M));
        assertTrue(QrCapacity.maxBytes(QrCapacity.ECC_M) > QrCapacity.maxBytes(QrCapacity.ECC_Q));
        assertTrue(QrCapacity.maxBytes(QrCapacity.ECC_Q) > QrCapacity.maxBytes(QrCapacity.ECC_H));
    }

    @Test
    public void hIsTheOldHardcodedLimitThatCausedTheAnr() {
        assertEquals(1273, QrCapacity.maxBytes(QrCapacity.ECC_H));
        assertEquals(2331, QrCapacity.maxBytes(QrCapacity.ECC_M));
        assertEquals(2953, QrCapacity.maxBytes(QrCapacity.ECC_L));
    }

    @Test
    public void fallsBackToWeakerEccInsteadOfFailing() {
        assertEquals(QrCapacity.ECC_M, QrCapacity.pickEcc(QrCapacity.ECC_H, 2000));
        assertEquals(QrCapacity.ECC_L, QrCapacity.pickEcc(QrCapacity.ECC_H, 2800));
        assertEquals(QrCapacity.ECC_H, QrCapacity.pickEcc(QrCapacity.ECC_H, 1000));
    }

    @Test
    public void reportsWhenNothingFits() {
        assertEquals(-1, QrCapacity.pickEcc(QrCapacity.ECC_L, 2954));
        assertEquals(QrCapacity.ECC_L, QrCapacity.pickEcc(QrCapacity.ECC_L, 2953));
    }

    @Test
    public void utf8LengthCountsBytesNotCharacters() {
        assertEquals(1, QrCapacity.utf8Length("a"));
        assertEquals(3, QrCapacity.utf8Length("\u20b9"));
        assertEquals(0, QrCapacity.utf8Length(null));
    }

    @Test
    public void tightFlagWarnsNearTheLimit() {
        assertFalse(QrCapacity.isTight(100, QrCapacity.ECC_M));
        assertTrue(QrCapacity.isTight(2200, QrCapacity.ECC_M));
    }
}
