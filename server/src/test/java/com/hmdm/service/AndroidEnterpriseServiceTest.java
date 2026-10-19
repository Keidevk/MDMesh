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
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers the configuration-resolution checks of the Single-Tenant
 * {@link AndroidEnterpriseService#createEnrollmentToken(boolean, long)}.
 *
 * <p>These tests exercise the branch that decides whether the single global enterprise configuration
 * is usable. They never reach the Android Management API: every scenario throws before the network
 * client is used, or fails client construction with an intentionally invalid (non-decryptable)
 * service-account blob.</p>
 */
public class AndroidEnterpriseServiceTest {

    private static final int CONFIG_ID = 42;
    private static final String TEST_AES_SECRET = "test-enterprise-aes-secret";

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
        service = buildService(new StubConfigDAO(global), null);

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

        StubConfigDAO(AndroidEnterpriseConfig single) {
            super(null);
            this.single = single;
        }

        @Override
        public AndroidEnterpriseConfig findSingleConfig() {
            return single;
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
