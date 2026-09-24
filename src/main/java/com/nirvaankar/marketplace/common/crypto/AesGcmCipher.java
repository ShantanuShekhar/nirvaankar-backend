package com.nirvaankar.marketplace.common.crypto;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-GCM for sensitive fields (seller bank account numbers).
 * Key from {@code nirvaankar.security.data-encryption-key} /
 * {@code NIRVAANKAR_DATA_ENCRYPTION_KEY} (Base64-encoded 32-byte key).
 * <p>
 * Local: put the key in {@code .secrets/encryption.yml} (gitignored).
 * Specifying only {@code $env:NIRVAANKAR_DATA_ENCRYPTION_KEY} in a random
 * PowerShell window does <em>not</em> apply to an IDE-launched JVM.
 */
@Component
public class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCipher(
            @Value("${nirvaankar.security.data-encryption-key:${NIRVAANKAR_DATA_ENCRYPTION_KEY:}}") String base64Key) {
        if (StringUtils.hasText(base64Key)) {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalStateException(
                        "nirvaankar.security.data-encryption-key must be Base64 of exactly 32 bytes");
            }
            this.key = new SecretKeySpec(raw, "AES");
        } else {
            this.key = null;
        }
    }

    public boolean configured() {
        return key != null;
    }

    public byte[] encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not encrypt sensitive data");
        }
    }

    public String decrypt(byte[] payload) {
        requireKey();
        try {
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(payload, GCM_IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not decrypt sensitive data");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "Bank verification requires NIRVAANKAR_DATA_ENCRYPTION_KEY to be configured");
        }
    }
}
