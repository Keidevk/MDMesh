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

package com.hmdm.service;

import com.google.api.services.androidmanagement.v1.model.ClearAppsDataParams;
import com.google.api.services.androidmanagement.v1.model.Command;
import com.google.api.services.androidmanagement.v1.model.WipeParams;
import com.hmdm.persistence.AndroidEnterpriseConfigDAO;
import com.hmdm.persistence.GlobalSettingsDAO;
import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import com.hmdm.util.CryptoUtil;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers the configuration-resolution checks of the Single-Tenant
 * {@link AndroidEnterpriseService#createEnrollmentToken(boolean, long)}, the instance-wide AES
 * password management and the command builders.
 *
 * <p>These tests never reach the Android Management API: every scenario either throws before the
 * network client is used, or fails client construction with an intentionally invalid
 * (non-decryptable) service-account blob.</p>
 */
public class AndroidEnterpriseServiceTest {

    private static final int CONFIG_ID = 42;
    private static final String TEST_AES_SECRET = "test-enterprise-aes-secret";
    private static final String SERVICE_ACCOUNT_JSON;

    static {
        try {
            SERVICE_ACCOUNT_JSON = serviceAccountJson();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build the test service-account JSON", e);
        }
    }

    private AndroidEnterpriseService service;

    @Before
    public void setUp() throws Exception {
        // No DAO needed for the constructor itself; each test installs its own stub.
        service = buildService(new StubConfigDAO(null));
    }

    private static AndroidEnterpriseService buildService(StubConfigDAO dao) {
        return buildService(dao, TEST_AES_SECRET);
    }

    private static AndroidEnterpriseService buildService(StubConfigDAO dao, String aesPassword) {
        return new AndroidEnterpriseService(dao, new StubGlobalSettingsDAO(aesPassword));
    }

    private static AndroidEnterpriseService buildService(StubConfigDAO dao, StubGlobalSettingsDAO settings) {
        return new AndroidEnterpriseService(dao, settings);
    }

    @Test
    public void noGlobalConfig_throws() throws Exception {
        service = buildService(new StubConfigDAO(null));

        assertIllegalState(
                () -> service.createEnrollmentToken(true, 1000L),
                "Android Enterprise is not configured or disabled");
    }

    @Test
    public void globalConfigDisabled_throws() throws Exception {
        AndroidEnterpriseConfig disabled = enabledConfig(CONFIG_ID);
        disabled.setEnabled(false);
        service = buildService(new StubConfigDAO(disabled));

        assertIllegalState(
                () -> service.createEnrollmentToken(true, 1000L),
                "Android Enterprise is not configured or disabled");
    }

    @Test
    public void missingGlobalAesPassword_throwsClearError() throws Exception {
        AndroidEnterpriseConfig global = enabledConfig(CONFIG_ID);
        global.setServiceAccountJsonEnc("any-encrypted-value");
        service = buildService(new StubConfigDAO(global), (String) null);

        try {
            service.createEnrollmentToken(true, 1000L);
            fail("Expected IllegalStateException for a missing AES password");
        } catch (IllegalStateException e) {
            assertTrue("Expected a missing-password error but got: " + e.getMessage(),
                    e.getMessage().contains("encryption password"));
        }
    }

    @Test
    public void globalConfigResolved_proceedsToClientConstruction() throws Exception {
        // The global config is enabled, so resolution must succeed and the method must move on to
        // building the Android Management client. The encrypted blob is not a valid service account,
        // so client creation fails — proving we got past the config checks.
        AndroidEnterpriseConfig global = enabledConfig(CONFIG_ID);
        global.setServiceAccountJsonEnc("not-a-valid-encrypted-service-account");
        service = buildService(new StubConfigDAO(global));

        try {
            service.createEnrollmentToken(true, 1000L);
            fail("Expected IllegalStateException from client construction");
        } catch (IllegalStateException e) {
            assertTrue("Expected client-construction failure but got: " + e.getMessage(),
                    e.getMessage().contains("Android Management client"));
        }
    }

    // ------------------------------------------- command builders -------------------------------------------

    @Test
    public void buildWipeCommand_usesBareTypeAndNoParamsByDefault() {
        Command command = AndroidEnterpriseService.buildWipeCommand(false);
        assertEquals("WIPE", command.getType());
        assertNull(command.getWipeParams());
    }

    @Test
    public void buildWipeCommand_withExternalStorageSetsWipeDataFlags() {
        Command command = AndroidEnterpriseService.buildWipeCommand(true);
        assertEquals("WIPE", command.getType());
        WipeParams params = command.getWipeParams();
        assertNotNull(params);
        assertEquals(Collections.singletonList("WIPE_EXTERNAL_STORAGE"), params.getWipeDataFlags());
    }

    @Test
    public void buildLockCommand_usesBareTypeWithoutParams() {
        Command command = AndroidEnterpriseService.buildLockCommand();
        assertEquals("LOCK", command.getType());
        assertNull(command.getWipeParams());
        assertNull(command.getClearAppsDataParams());
    }

    @Test
    public void buildClearAppDataCommand_setsPackageNamesList() {
        Command command = AndroidEnterpriseService.buildClearAppDataCommand("com.example.app");
        assertEquals("CLEAR_APP_DATA", command.getType());
        ClearAppsDataParams params = command.getClearAppsDataParams();
        assertNotNull(params);
        assertEquals(Collections.singletonList("com.example.app"), params.getPackageNames());
    }

    @Test
    public void buildRebootCommand_usesBareType() {
        assertEquals("REBOOT", AndroidEnterpriseService.buildRebootCommand().getType());
    }

    // --------------------------------------- AES password management ---------------------------------------

    @Test
    public void isAesPasswordConfigured_nullIsFalse() {
        assertFalse(buildService(new StubConfigDAO(null), (String) null).isAesPasswordConfigured());
    }

    @Test
    public void isAesPasswordConfigured_blankIsFalse() {
        assertFalse(buildService(new StubConfigDAO(null), "   ").isAesPasswordConfigured());
    }

    @Test
    public void isAesPasswordConfigured_nonBlankIsTrue() {
        assertTrue(buildService(new StubConfigDAO(null), "secret").isAesPasswordConfigured());
    }

    @Test
    public void encryptServiceAccount_roundTrips() {
        String encrypted = service.encryptServiceAccount(SERVICE_ACCOUNT_JSON, TEST_AES_SECRET);
        assertEquals(SERVICE_ACCOUNT_JSON, CryptoUtil.decryptAES(encrypted, TEST_AES_SECRET));
    }

    @Test
    public void encryptServiceAccount_emptyInputReturnsEmpty() {
        assertEquals("", service.encryptServiceAccount("", TEST_AES_SECRET));
    }

    @Test
    public void changeGlobalAesPassword_reencryptsEveryConfig() {
        AndroidEnterpriseConfig config = enabledConfig(CONFIG_ID);
        config.setServiceAccountJsonEnc(CryptoUtil.encryptAES("plain-account", "old-password"));

        StubConfigDAO dao = new StubConfigDAO(null);
        dao.setAll(Collections.singletonList(config));
        StubGlobalSettingsDAO settings = new StubGlobalSettingsDAO("old-password");
        service = buildService(dao, settings);

        service.changeGlobalAesPassword("old-password", "new-password");

        assertEquals("plain-account", CryptoUtil.decryptAES(config.getServiceAccountJsonEnc(), "new-password"));
        assertEquals(1, dao.updated.size());
        assertSame(config, dao.updated.get(0));
        assertEquals("new-password", settings.password);
    }

    @Test
    public void changeGlobalAesPassword_skipsBlankCiphertexts() {
        AndroidEnterpriseConfig withNull = enabledConfig(1);
        withNull.setServiceAccountJsonEnc(null);
        AndroidEnterpriseConfig withEmpty = enabledConfig(2);
        withEmpty.setServiceAccountJsonEnc("");

        StubConfigDAO dao = new StubConfigDAO(null);
        dao.setAll(java.util.Arrays.asList(withNull, withEmpty));
        StubGlobalSettingsDAO settings = new StubGlobalSettingsDAO("old-password");
        service = buildService(dao, settings);

        service.changeGlobalAesPassword("old-password", "new-password");

        assertTrue(dao.updated.isEmpty());
        assertEquals("new-password", settings.password);
    }

    @Test
    public void changeGlobalAesPassword_firstPassword_verifiesExistingCiphertext() {
        AndroidEnterpriseConfig config = enabledConfig(CONFIG_ID);
        config.setServiceAccountJsonEnc(CryptoUtil.encryptAES("plain-account", "first-password"));

        StubConfigDAO dao = new StubConfigDAO(null);
        dao.setAll(Collections.singletonList(config));
        StubGlobalSettingsDAO settings = new StubGlobalSettingsDAO(null);
        service = buildService(dao, settings);

        service.changeGlobalAesPassword(null, "first-password");

        assertTrue("A verified first password must not rewrite the config", dao.updated.isEmpty());
        assertEquals("first-password", settings.password);
    }

    @Test
    public void changeGlobalAesPassword_firstPassword_wrongPasswordAborts() {
        AndroidEnterpriseConfig config = enabledConfig(CONFIG_ID);
        config.setServiceAccountJsonEnc(CryptoUtil.encryptAES("plain-account", "actual-password"));

        StubConfigDAO dao = new StubConfigDAO(null);
        dao.setAll(Collections.singletonList(config));
        StubGlobalSettingsDAO settings = new StubGlobalSettingsDAO(null);
        service = buildService(dao, settings);

        try {
            service.changeGlobalAesPassword(null, "wrong-password");
            fail("Expected the first password to be refused when it cannot decrypt the stored ciphertext");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("AES decryption failed"));
        }
        assertNull("A refused password must not be persisted", settings.password);
        assertTrue(dao.updated.isEmpty());
    }

    @Test
    public void changeGlobalAesPassword_wrongOldPassword_abortsWithoutPersisting() {
        AndroidEnterpriseConfig first = enabledConfig(1);
        first.setServiceAccountJsonEnc(CryptoUtil.encryptAES("plain-account", "actual-password"));

        StubConfigDAO dao = new StubConfigDAO(null);
        dao.setAll(Collections.singletonList(first));
        StubGlobalSettingsDAO settings = new StubGlobalSettingsDAO("actual-password");
        service = buildService(dao, settings);

        try {
            service.changeGlobalAesPassword("wrong-old-password", "new-password");
            fail("Expected re-encryption to fail when the old password cannot decrypt");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("AES decryption failed"));
        }
        assertTrue(dao.updated.isEmpty());
        assertEquals("actual-password", settings.password);
    }

    // ---------------------------------------------- persistence ----------------------------------------------

    @Test
    public void saveConfig_withoutId_insertsWithTimestamps() {
        StubConfigDAO dao = new StubConfigDAO(null);
        service = buildService(dao);

        AndroidEnterpriseConfig config = new AndroidEnterpriseConfig();
        service.saveConfig(config);

        assertEquals(1, dao.inserted.size());
        assertSame(config, dao.inserted.get(0));
        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
        assertTrue(dao.updated.isEmpty());
    }

    @Test
    public void saveConfig_withId_updatesWithoutInserting() {
        StubConfigDAO dao = new StubConfigDAO(null);
        service = buildService(dao);

        AndroidEnterpriseConfig config = new AndroidEnterpriseConfig();
        config.setId(CONFIG_ID);
        service.saveConfig(config);

        assertTrue(dao.inserted.isEmpty());
        assertEquals(1, dao.updated.size());
        assertNotNull(config.getUpdatedAt());
        assertNull(config.getCreatedAt());
    }

    @Test
    public void getConfig_delegatesToDao() {
        AndroidEnterpriseConfig stored = enabledConfig(CONFIG_ID);
        StubConfigDAO dao = new StubConfigDAO(stored);
        service = buildService(dao);

        assertSame(stored, service.getConfig(7));
        assertSame(stored, service.getConfigByEnterpriseId("enterprises/LC01test"));
    }

    @Test
    public void deleteConfig_delegatesToDao() {
        StubConfigDAO dao = new StubConfigDAO(null);
        service = buildService(dao);

        service.deleteConfig(9);

        assertEquals(Integer.valueOf(9), dao.deletedCustomerId);
    }

    @Test
    public void getConfigByEnterpriseId_propagatesMissingConfig() {
        service = buildService(new StubConfigDAO(null));
        assertNull(service.getConfigByEnterpriseId("enterprises/unknown"));
    }

    // ---------------------------------------------- validation ----------------------------------------------

    @Test
    public void validateServiceAccountJson_acceptsGeneratedKey() {
        // Must not throw.
        service.validateServiceAccountJson(SERVICE_ACCOUNT_JSON);
    }

    @Test
    public void validateServiceAccountJson_rejectsGarbage() {
        try {
            service.validateServiceAccountJson("{ not a service account }");
            fail("Expected invalid service-account JSON to be rejected");
        } catch (RuntimeException e) {
            assertTrue("Expected an 'Invalid Service Account JSON' error but got: " + e.getMessage(),
                    e.getMessage().contains("Invalid Service Account JSON"));
        }
    }

    // ------------------------------------------------- helpers -------------------------------------------------

    private static AndroidEnterpriseConfig enabledConfig(int id) {
        AndroidEnterpriseConfig c = new AndroidEnterpriseConfig();
        c.setId(id);
        c.setEnterpriseId("enterprises/LC01test");
        c.setProjectId("test-project");
        c.setDpcPackageName("com.mdmesh.agent");
        c.setEnabled(true);
        return c;
    }

    /**
     * Builds a syntactically valid service-account JSON with a freshly generated RSA key, so
     * {@code ServiceAccountCredentials.fromStream()} can parse it without touching the network.
     */
    private static String serviceAccountJson() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        return "{"
                + "\"type\":\"service_account\","
                + "\"project_id\":\"test-project\","
                + "\"private_key_id\":\"key-id\","
                + "\"private_key\":\"" + pem.replace("\n", "\\n") + "\","
                + "\"client_email\":\"test@test-project.iam.gserviceaccount.com\","
                + "\"client_id\":\"1234567890\","
                + "\"token_uri\":\"https://oauth2.googleapis.com/token\""
                + "}";
    }

    private static void assertIllegalState(ThrowingCall call, String expectedMessagePart) {
        try {
            call.run();
            fail("Expected IllegalStateException containing: " + expectedMessagePart);
        } catch (IllegalStateException e) {
            assertTrue("Expected message to contain '" + expectedMessagePart + "' but was: " + e.getMessage(),
                    e.getMessage().contains(expectedMessagePart));
        } catch (Exception e) {
            fail("Expected IllegalStateException but got " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    /** Hand-rolled stub — avoids depending on a mocking library that predates modern JDKs. */
    private static class StubConfigDAO extends AndroidEnterpriseConfigDAO {
        private final AndroidEnterpriseConfig single;
        private final List<AndroidEnterpriseConfig> all = new ArrayList<>();
        private final List<AndroidEnterpriseConfig> inserted = new ArrayList<>();
        private final List<AndroidEnterpriseConfig> updated = new ArrayList<>();
        private Integer deletedCustomerId;

        StubConfigDAO(AndroidEnterpriseConfig single) {
            super(null);
            this.single = single;
        }

        void setAll(List<AndroidEnterpriseConfig> configs) {
            this.all.clear();
            this.all.addAll(configs);
        }

        @Override
        public AndroidEnterpriseConfig findSingleConfig() {
            return single;
        }

        @Override
        public AndroidEnterpriseConfig findByCustomerId(int customerId) {
            return single;
        }

        @Override
        public AndroidEnterpriseConfig findByEnterpriseId(String enterpriseId) {
            return single;
        }

        @Override
        public List<AndroidEnterpriseConfig> findAll() {
            return all;
        }

        @Override
        public void insert(AndroidEnterpriseConfig config) {
            inserted.add(config);
        }

        @Override
        public void update(AndroidEnterpriseConfig config) {
            updated.add(config);
        }

        @Override
        public void deleteByCustomerId(int customerId) {
            this.deletedCustomerId = customerId;
        }
    }

    private static class StubGlobalSettingsDAO extends GlobalSettingsDAO {
        private String password;

        StubGlobalSettingsDAO(String password) {
            super(null);
            this.password = password;
        }

        @Override
        public String getEnterpriseAesPassword() {
            return password;
        }

        @Override
        public void setEnterpriseAesPassword(String password) {
            this.password = password;
        }
    }
}
