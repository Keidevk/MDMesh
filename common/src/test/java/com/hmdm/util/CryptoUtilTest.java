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

import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers the SHA-256 + constant-time-compare helpers backing the agent per-device secret, and the
 * AES-256-GCM helpers backing the single global Android Enterprise service-account encryption.
 */
public class CryptoUtilTest {

    // Known-answer test: SHA-256("abc"), upper-cased as getSHA256String returns.
    private static final String SHA256_ABC =
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";

    @Test
    public void sha256_isDeterministicAndCorrect() {
        assertEquals(SHA256_ABC, CryptoUtil.getSHA256String("abc"));
        assertEquals(CryptoUtil.getSHA256String("abc"), CryptoUtil.getSHA256String("abc"));
    }

    @Test
    public void sha256_differsForDifferentInput() {
        assertFalse(CryptoUtil.getSHA256String("abc").equals(CryptoUtil.getSHA256String("abd")));
    }

    @Test
    public void constantTimeEquals_matchesOnlyIdentical() {
        assertTrue(CryptoUtil.constantTimeEquals(SHA256_ABC, SHA256_ABC));
        assertFalse(CryptoUtil.constantTimeEquals(SHA256_ABC, "DEADBEEF"));
        assertFalse(CryptoUtil.constantTimeEquals(null, SHA256_ABC));
        assertFalse(CryptoUtil.constantTimeEquals(SHA256_ABC, null));
    }

    // ---------------------------------------------- AES-256-GCM ----------------------------------------------

    private static final String AES_SECRET = "instance-wide-enterprise-aes-secret";

    @Test
    public void aesRoundTrip_simple() {
        String plain = "hello enterprise";
        assertEquals(plain, CryptoUtil.decryptAES(CryptoUtil.encryptAES(plain, AES_SECRET), AES_SECRET));
    }

    @Test
    public void aesRoundTrip_unicodeIsUtf8Safe() {
        String plain = "cláve-ñandú-日本語-🚀";
        assertEquals(plain, CryptoUtil.decryptAES(CryptoUtil.encryptAES(plain, AES_SECRET), AES_SECRET));
    }

    @Test
    public void aesRoundTrip_largeMultilineJson() {
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < 200; i++) {
            sb.append("  \"field").append(i).append("\": \"value ").append(i).append("\",\n");
        }
        sb.append("  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQ...\\n-----END PRIVATE KEY-----\"\n}");
        String plain = sb.toString();
        assertEquals(plain, CryptoUtil.decryptAES(CryptoUtil.encryptAES(plain, AES_SECRET), AES_SECRET));
    }

    @Test
    public void aesEncrypt_nullOrEmptyReturnsEmpty() {
        assertEquals("", CryptoUtil.encryptAES(null, AES_SECRET));
        assertEquals("", CryptoUtil.encryptAES("", AES_SECRET));
    }

    @Test
    public void aesDecrypt_nullOrEmptyReturnsEmpty() {
        assertEquals("", CryptoUtil.decryptAES(null, AES_SECRET));
        assertEquals("", CryptoUtil.decryptAES("", AES_SECRET));
    }

    @Test
    public void aesEncrypt_isNonDeterministicButDecryptable() {
        String plain = "same plaintext";
        String first = CryptoUtil.encryptAES(plain, AES_SECRET);
        String second = CryptoUtil.encryptAES(plain, AES_SECRET);

        // A random salt + IV per call must make the two ciphertexts differ...
        assertNotEquals(first, second);
        // ...while still both decrypting back to the original value.
        assertEquals(plain, CryptoUtil.decryptAES(first, AES_SECRET));
        assertEquals(plain, CryptoUtil.decryptAES(second, AES_SECRET));
    }

    @Test
    public void aesDecrypt_wrongPasswordThrows() {
        String encrypted = CryptoUtil.encryptAES("secret", AES_SECRET);
        try {
            CryptoUtil.decryptAES(encrypted, "a-different-password");
            fail("Expected decryption with the wrong password to fail");
        } catch (RuntimeException e) {
            assertTrue("Expected 'AES decryption failed' but got: " + e.getMessage(),
                    e.getMessage().contains("AES decryption failed"));
        }
    }

    @Test
    public void aesDecrypt_tamperedCiphertextThrows() {
        String plain = "tamper-me";
        byte[] combined = Base64.getDecoder().decode(CryptoUtil.encryptAES(plain, AES_SECRET));
        // Flip a bit inside the ciphertext body (well past the 16-byte salt + 12-byte IV header).
        combined[combined.length - 1] ^= 0x01;

        try {
            CryptoUtil.decryptAES(Base64.getEncoder().encodeToString(combined), AES_SECRET);
            fail("Expected GCM tag verification to reject the tampered ciphertext");
        } catch (RuntimeException e) {
            assertTrue("Expected 'AES decryption failed' but got: " + e.getMessage(),
                    e.getMessage().contains("AES decryption failed"));
        }
    }

    @Test
    public void aesDecrypt_tooShortThrowsWithCause() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[10]);
        try {
            CryptoUtil.decryptAES(tooShort, AES_SECRET);
            fail("Expected an 'AES decryption failed' wrapping of the too-short input");
        } catch (RuntimeException e) {
            // The specific reason is thrown inside the try block, so the generic catch re-wraps it
            // and only the cause keeps the "Ciphertext too short" detail.
            assertEquals("AES decryption failed", e.getMessage());
            assertNotNull(e.getCause());
            assertTrue("Expected the cause to explain the short input but was: " + e.getCause().getMessage(),
                    e.getCause().getMessage().contains("Ciphertext too short"));
        }
    }

    @Test
    public void aesDecrypt_invalidBase64Throws() {
        try {
            CryptoUtil.decryptAES("not-valid-base64!!!", AES_SECRET);
            fail("Expected invalid Base64 input to fail");
        } catch (RuntimeException e) {
            assertTrue("Expected 'AES decryption failed' but got: " + e.getMessage(),
                    e.getMessage().contains("AES decryption failed"));
        }
    }
}
