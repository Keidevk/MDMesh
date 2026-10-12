/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.SyncResponseInt;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtil {

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    public CryptoUtil() {
    }

    public static String getMD5String(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(value.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return getHexString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getHexString(byte[] digest) {
        char[] hexChars = new char[digest.length * 2];

        for(int i = 0; i < digest.length; ++i) {
            int v = digest[i] & 255;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 15];
        }

        return (new String(hexChars)).toUpperCase();
    }

    /**
     * <p>Encodes the specified content into Base-64 URL-safe format according to RFC 3548.</p>
     *
     * @param digest a content to encode.
     * @return a base-64 encoded string representing the specified content.
     */
    public static String getBase64String(byte[] digest) {
        String hashString = BaseEncoding.base64Url().encode(digest);
        return hashString;
    }

    public static String calculateChecksum(InputStream fileContent) throws NoSuchAlgorithmException, IOException {
        // Calculate checksum
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new BufferedInputStream(fileContent);
             DigestInputStream dis = new DigestInputStream(is, md)) {
            /* Read decorated stream (dis) to EOF as normal... */
            int b;
            while ((b = dis.read()) != -1) {
                // digest will consume the content when read() called
            }
        }

        // to calculate message digest of the input string
        // returned as array of byte
        byte[] digest = md.digest();

        // Convert byte array into signum representation
        BigInteger no = new BigInteger(1, digest);

        // Convert message digest into hex value
        String hashtext = no.toString(16);

        // Add preceding 0s to make it 32 bit
        while (hashtext.length() < 32) {
            hashtext = "0" + hashtext;
        }

        return hashtext;
    }

    public static String getSHA1String(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update( value.getBytes());
            byte[] digest = md.digest();

            char[] hexChars = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                hexChars[i * 2] = hexArray[v >>> 4];
                hexChars[i * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException( e );
        }
    }

    public static String getSHA256String(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(value.getBytes());
            byte[] digest = md.digest();

            char[] hexChars = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                hexChars[i * 2] = hexArray[v >>> 4];
                hexChars[i * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Constant-time comparison of two hex strings, to avoid leaking via timing. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String getDataSignature(String hashSecret, Object data) {
        ObjectMapper objectMapper = new ObjectMapper();
        String s = "";
        try {
            s = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        s = s.replaceAll("\\s", "");
        String signature = CryptoUtil.getSHA1String(hashSecret + s);
        return signature;
    }

    public static boolean checkRequestSignature(String signature, String value) {
        if (signature == null) {
            return false;
        }
        try {
            String goodSignature = CryptoUtil.getSHA1String(value);
            if (!signature.equalsIgnoreCase(goodSignature)) {
                return false;
            }
        } catch (Exception e) {
        }
        return true;
    }

    // SecureRandom: randomHexString also generates the default JWT signing key (TokenProvider).
    private static final Random RANDOM = new SecureRandom();

    public static String randomHexString(int length) {
        StringBuilder sb = new StringBuilder();
        Random random = RANDOM;

        for (int i = 0; i < length; i++) {
            sb.append(hexArray[random.nextInt(16)]);
        }

        return sb.toString();
    }

    // ==================== AES-GCM Encryption (for Service Account JSON) ====================

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    /**
     * Derives an AES-256 key from a password using PBKDF2.
     * The password should be a high-entropy secret (e.g., from config).
     */
    private static SecretKey deriveKey(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive AES key", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a password-derived key.
     * Returns Base64(salt + iv + ciphertext + tag).
     */
    public static String encryptAES(String plaintext, String password) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine: salt (16) + iv (12) + ciphertext + tag (16)
            byte[] combined = new byte[salt.length + iv.length + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(ciphertext, 0, combined, salt.length + iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts ciphertext encrypted with encryptAES().
     */
    public static String decryptAES(String ciphertextB64, String password) {
        if (ciphertextB64 == null || ciphertextB64.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextB64);

            if (combined.length < 16 + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new IllegalArgumentException("Ciphertext too short");
            }

            byte[] salt = Arrays.copyOfRange(combined, 0, 16);
            byte[] iv = Arrays.copyOfRange(combined, 16, 16 + GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, 16 + GCM_IV_LENGTH, combined.length);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
