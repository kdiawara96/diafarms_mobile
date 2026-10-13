package com.mobile.diafarms.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IndicatifTelephoneTest {

    @Test
    public void decoupeEtAssemble() {
        assertArrayEquals(new String[] {"226", "70 12 34 56"}, IndicatifTelephone.decouper("+226 70 12 34 56", "223"));
        assertArrayEquals(new String[] {"223", "70123456"}, IndicatifTelephone.decouper("+22370123456", "226"));
        assertArrayEquals(new String[] {"223", "70 12 34 56"}, IndicatifTelephone.decouper("70 12 34 56", "223"));
        assertEquals("+226 70 12 34 56", IndicatifTelephone.assembler("226", " 70 12 34 56 "));
        assertEquals("", IndicatifTelephone.assembler("223", "  "));
    }

    @Test
    public void memeNumeroEcritAutrement() {
        assertEquals(IndicatifTelephone.chiffres("+223 70 12 34 56"), IndicatifTelephone.chiffres("70123456"));
        assertEquals(IndicatifTelephone.chiffres("0022370123456"), IndicatifTelephone.chiffres("+223 70123456"));
    }
}
