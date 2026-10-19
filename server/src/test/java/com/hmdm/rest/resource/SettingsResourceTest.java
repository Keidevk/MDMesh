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

package com.hmdm.rest.resource;

import com.hmdm.persistence.AndroidEnterpriseConfigDAO;
import com.hmdm.persistence.GlobalSettingsDAO;
import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.domain.UserRolePermission;
import com.hmdm.rest.json.EnterpriseConfigRequest;
import com.hmdm.rest.json.EnterpriseConfigResponse;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import com.hmdm.service.AndroidEnterpriseService;
import com.hmdm.util.CryptoUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Covers the Android Enterprise branches of {@link SettingsResource}. The DAOs and the global
 * settings store are hand-rolled fakes (same style as {@code AndroidEnterpriseServiceTest}); the
 * real {@link AndroidEnterpriseService} is used so the encryption round-trip is exercised for real.
 * The security context is initialised directly, so no container is needed.
 */
public class SettingsResourceTest {

    private static final int CUSTOMER_ID = 1;
    private static final String SERVICE_ACCOUNT_JSON;

    static {
        try {
            SERVICE_ACCOUNT_JSON = serviceAccountJson();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build the test service-account JSON", e);
        }
    }

    private StubConfigDAO configDAO;
    private StubGlobalSettingsDAO settingsDAO;
    private SettingsResource resource;

    @Before
    public void setUp() throws Exception {
        configDAO = new StubConfigDAO();
        settingsDAO = new StubGlobalSettingsDAO();
        AndroidEnterpriseService service = new AndroidEnterpriseService(configDAO, settingsDAO);
        // Only the enterprise branches are exercised, so the non-enterprise DAOs can be null.
        resource = new SettingsResource(null, null, null, configDAO, service, settingsDAO);

        SecurityContext.init(userWithSettingsPermission(CUSTOMER_ID));
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    // ---------------------------------------------------- GET ----------------------------------------------------

    @Test
    public void getEnterpriseConfig_whenMissing_returnsOkWithNull() {
        Response response = resource.getEnterpriseConfig();

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertNull(response.getData());
    }

    @Test
    public void getEnterpriseConfig_mapsStoredConfigWithoutSecrets() {
        configDAO.stored = storedConfig("enterprises/LC01test", "old-password");
        settingsDAO.password = "old-password";

        Response response = resource.getEnterpriseConfig();

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        EnterpriseConfigResponse data = (EnterpriseConfigResponse) response.getData();
        assertEquals("enterprises/LC01test", data.getEnterpriseId());
        assertEquals(CUSTOMER_ID, data.getCustomerId());
        assertTrue(data.isEnabled());
        assertTrue(data.isAesPasswordConfigured());
    }

    @Test
    public void getEnterpriseConfig_deniedWithoutSettingsPermission() {
        SecurityContext.init(userWithoutPermissions(CUSTOMER_ID));

        assertEquals(Response.ResponseStatus.ERROR, resource.getEnterpriseConfig().getStatus());
    }

    // --------------------------------------------------- SAVE ---------------------------------------------------

    @Test
    public void saveEnterpriseConfig_requiresAesPassword() {
        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setServiceAccountJson(SERVICE_ACCOUNT_JSON);

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertEquals("error.enterprise.aespassword.required", response.getMessage());
    }

    @Test
    public void saveEnterpriseConfig_requiresServiceAccountForNewConfig() {
        settingsDAO.password = "existing-password";
        EnterpriseConfigRequest request = request("enterprises/LC01test");

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertEquals("error.enterprise.serviceaccount.required", response.getMessage());
    }

    @Test
    public void saveEnterpriseConfig_rejectsInvalidServiceAccount() {
        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setAesPassword("new-password");
        request.setServiceAccountJson("{ not a service account }");

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertEquals("error.enterprise.serviceaccount.invalid", response.getMessage());
        assertNull("An invalid service account must not be persisted", configDAO.stored);
    }

    @Test
    public void saveEnterpriseConfig_encryptsAndInsertsNewConfig() {
        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setAesPassword("new-password");
        request.setServiceAccountJson(SERVICE_ACCOUNT_JSON);

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals(1, configDAO.inserted.size());
        assertNotNull(configDAO.stored);
        assertNotNull(configDAO.stored.getCreatedAt());
        assertEquals(SERVICE_ACCOUNT_JSON,
                CryptoUtil.decryptAES(configDAO.stored.getServiceAccountJsonEnc(), "new-password"));
        assertEquals("new-password", settingsDAO.password);
    }

    @Test
    public void saveEnterpriseConfig_keepsCiphertextWhenServiceAccountOmitted() {
        AndroidEnterpriseConfig stored = storedConfig("enterprises/LC01old", "old-password");
        String originalCiphertext = stored.getServiceAccountJsonEnc();
        configDAO.stored = stored;
        settingsDAO.password = "old-password";

        EnterpriseConfigRequest request = request("enterprises/LC01new");
        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals("enterprises/LC01new", configDAO.stored.getEnterpriseId());
        assertEquals("The existing ciphertext must be preserved", originalCiphertext,
                configDAO.stored.getServiceAccountJsonEnc());
        assertEquals(1, configDAO.updated.size());
        assertTrue(configDAO.inserted.isEmpty());
    }

    @Test
    public void saveEnterpriseConfig_reencryptsOnPasswordChange() {
        configDAO.stored = storedConfig("enterprises/LC01test", "old-password");
        settingsDAO.password = "old-password";

        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setAesPassword("new-password");
        request.setServiceAccountJson(SERVICE_ACCOUNT_JSON);

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals(SERVICE_ACCOUNT_JSON,
                CryptoUtil.decryptAES(configDAO.stored.getServiceAccountJsonEnc(), "new-password"));
        assertEquals("new-password", settingsDAO.password);
    }

    @Test
    public void saveEnterpriseConfig_resetsPasswordWhenExistingCiphertextUndecryptable() {
        // Stored blob was encrypted with a different password than the one now configured.
        configDAO.stored = storedConfig("enterprises/LC01test", "some-other-password");
        settingsDAO.password = "old-password";

        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setAesPassword("new-password");
        request.setServiceAccountJson(SERVICE_ACCOUNT_JSON);

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals("new-password", settingsDAO.password);
        assertEquals(SERVICE_ACCOUNT_JSON,
                CryptoUtil.decryptAES(configDAO.stored.getServiceAccountJsonEnc(), "new-password"));
    }

    @Test
    public void saveEnterpriseConfig_failsWhenCiphertextUndecryptableWithoutNewJson() {
        configDAO.stored = storedConfig("enterprises/LC01test", "some-other-password");
        settingsDAO.password = "old-password";

        EnterpriseConfigRequest request = request("enterprises/LC01test");
        request.setAesPassword("new-password");

        Response response = resource.saveEnterpriseConfig(request);

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertEquals("error.enterprise.aespassword.invalid", response.getMessage());
        assertEquals("The password must not change on failure", "old-password", settingsDAO.password);
    }

    // -------------------------------------------------- DELETE --------------------------------------------------

    @Test
    public void deleteEnterpriseConfig_delegatesToDao() {
        Response response = resource.deleteEnterpriseConfig();

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        assertEquals(Integer.valueOf(CUSTOMER_ID), configDAO.deletedCustomerId);
    }

    @Test
    public void deleteEnterpriseConfig_deniedWithoutSettingsPermission() {
        SecurityContext.init(userWithoutPermissions(CUSTOMER_ID));

        assertEquals(Response.ResponseStatus.ERROR, resource.deleteEnterpriseConfig().getStatus());
        assertNull(configDAO.deletedCustomerId);
    }

    // ------------------------------------------------- helpers -------------------------------------------------

    private static EnterpriseConfigRequest request(String enterpriseId) {
        EnterpriseConfigRequest request = new EnterpriseConfigRequest();
        request.setEnterpriseId(enterpriseId);
        request.setProjectId("test-project");
        request.setDpcPackageName("com.mdmesh.agent");
        request.setEnabled(true);
        return request;
    }

    private static AndroidEnterpriseConfig storedConfig(String enterpriseId, String password) {
        AndroidEnterpriseConfig config = new AndroidEnterpriseConfig();
        config.setId(42);
        config.setCustomerId(CUSTOMER_ID);
        config.setEnterpriseId(enterpriseId);
        config.setProjectId("test-project");
        config.setDpcPackageName("com.mdmesh.agent");
        config.setEnabled(true);
        config.setServiceAccountJsonEnc(CryptoUtil.encryptAES(SERVICE_ACCOUNT_JSON, password));
        config.setCreatedAt(1000L);
        config.setUpdatedAt(1000L);
        return config;
    }

    private static User userWithSettingsPermission(int customerId) {
        return user(customerId, true);
    }

    private static User userWithoutPermissions(int customerId) {
        return user(customerId, false);
    }

    private static User user(int customerId, boolean settingsPermission) {
        List<UserRolePermission> permissions = new ArrayList<>();
        if (settingsPermission) {
            UserRolePermission permission = new UserRolePermission();
            permission.setName("settings");
            permissions.add(permission);
        }

        UserRole role = new UserRole();
        role.setPermissions(permissions);
        role.setSuperAdmin(false);

        User user = new User();
        user.setLogin("tester");
        user.setCustomerId(customerId);
        user.setUserRole(role);
        return user;
    }

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

    /** Hand-rolled stub — avoids depending on a mocking library that predates modern JDKs. */
    private static class StubConfigDAO extends AndroidEnterpriseConfigDAO {
        private AndroidEnterpriseConfig stored;
        private final List<AndroidEnterpriseConfig> inserted = new ArrayList<>();
        private final List<AndroidEnterpriseConfig> updated = new ArrayList<>();
        private Integer deletedCustomerId;

        StubConfigDAO() {
            super(null);
        }

        @Override
        public AndroidEnterpriseConfig findByCustomerId(int customerId) {
            return stored;
        }

        @Override
        public AndroidEnterpriseConfig findByEnterpriseId(String enterpriseId) {
            return stored;
        }

        @Override
        public List<AndroidEnterpriseConfig> findAll() {
            return stored == null ? Collections.emptyList() : Collections.singletonList(stored);
        }

        @Override
        public void insert(AndroidEnterpriseConfig config) {
            inserted.add(config);
            stored = config;
        }

        @Override
        public void update(AndroidEnterpriseConfig config) {
            updated.add(config);
            stored = config;
        }

        @Override
        public void deleteByCustomerId(int customerId) {
            deletedCustomerId = customerId;
        }
    }

    private static class StubGlobalSettingsDAO extends GlobalSettingsDAO {
        private String password;

        StubGlobalSettingsDAO() {
            super(null);
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
