package com.mobile.diafarms.crypto;

import android.util.Base64;

import com.mobile.diafarms.network.Constants;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Réplique AESService.decrypt() côté backend (diafarms_back) : AES/CBC/PKCS5Padding,
 * IV de 16 octets préfixé au texte chiffré, le tout encodé en base64. Sert à
 * déchiffrer localement le contenu du QR code généré par QRCodeController/generate.
 */
public class AESHelper {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16;
    private static final int KEY_SIZE = 32;

    public static String decrypt(String encryptedBase64) throws GeneralSecurityException {
        byte[] combined = Base64.decode(encryptedBase64, Base64.DEFAULT);

        if (combined.length <= IV_SIZE) {
            throw new GeneralSecurityException("Donnée chiffrée invalide (trop courte)");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
        byte[] cipherBytes = Arrays.copyOfRange(combined, IV_SIZE, combined.length);

        SecretKeySpec keySpec = new SecretKeySpec(getValidatedKeyBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));

        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // Même logique de validation que AESService.getValidatedKeyBytes() côté backend.
    private static byte[] getValidatedKeyBytes() {
        byte[] keyBytes = Constants.AES_SECRET_KEY.trim().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_SIZE) {
            byte[] adjusted = new byte[KEY_SIZE];
            System.arraycopy(keyBytes, 0, adjusted, 0, Math.min(keyBytes.length, KEY_SIZE));
            return adjusted;
        }
        return keyBytes;
    }

    private AESHelper() {
    }
}
