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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidmanagement.v1.AndroidManagement;
import com.google.api.services.androidmanagement.v1.model.Command;
import com.google.api.services.androidmanagement.v1.model.Device;
import com.google.api.services.androidmanagement.v1.model.EnrollmentToken;
import com.google.api.services.androidmanagement.v1.model.Operation;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.hmdm.persistence.AndroidEnterpriseConfigDAO;
import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import com.hmdm.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class AndroidEnterpriseService {

    private static final Logger logger = LoggerFactory.getLogger(AndroidEnterpriseService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String AES_PASSWORD = "headwind-mdm-enterprise-aes-key-2024";
    private static final long DEFAULT_TOKEN_DURATION_MS = 24L * 60 * 60 * 1000;

    private final AndroidEnterpriseConfigDAO configDAO;
    private final HttpTransport httpTransport;

    @Inject
    public AndroidEnterpriseService(AndroidEnterpriseConfigDAO configDAO) throws GeneralSecurityException, IOException {
        this.configDAO = configDAO;
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    }

    private AndroidManagement createClient(int customerId) {
        AndroidEnterpriseConfig config = configDAO.findByCustomerId(customerId);
        if (config == null || !config.isEnabled()) {
            logger.warn("Enterprise config not found or disabled for customer {}", customerId);
            return null;
        }

        try {
            String serviceAccountJson = CryptoUtil.decryptAES(config.getServiceAccountJsonEnc(), AES_PASSWORD);
            GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes()))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidmanagement"));

            return new AndroidManagement.Builder(httpTransport, JSON_FACTORY, request -> {
                try {
                    credentials.refreshIfExpired();
                    request.getHeaders().setAuthorization("Bearer " + credentials.getAccessToken().getTokenValue());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to refresh credentials", e);
                }
            }).setApplicationName("Headwind-MDM").build();
        } catch (Exception e) {
            logger.error("Failed to create AndroidManagement client for customer {}", customerId, e);
            return null;
        }
    }

    public void validateServiceAccountJson(String serviceAccountJson) {
        try {
            ServiceAccountCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes()))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidmanagement"));
        } catch (IOException e) {
            throw new RuntimeException("Invalid Service Account JSON: " + e.getMessage(), e);
        }
    }

    public EnrollmentTokenResult createEnrollmentToken(int customerId, Integer configurationId, boolean oneTime, long durationMillis) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        AndroidEnterpriseConfig config = configDAO.findByCustomerId(customerId);
        if (config == null) {
            throw new IllegalStateException("Enterprise config not found");
        }

        try {
            EnrollmentToken token = new EnrollmentToken();
            token.setDuration(String.valueOf(durationMillis) + "ms");
            // oneTime is not directly settable in newer API, handled by token expiration
            token.setPolicyName("enterprises/" + config.getEnterpriseId() + "/policies/default");

            EnrollmentToken created = client.enterprises().enrollmentTokens()
                    .create(config.getEnterpriseId(), token)
                    .execute();

            logger.info("Created enrollment token {} for customer {} (config {})", created.getName(), customerId, configurationId);

            String qrCodeData = buildQrCodeData(config, created.getValue());

            return new EnrollmentTokenResult(created, qrCodeData, configurationId);
        } catch (IOException e) {
            logger.error("Failed to create enrollment token for customer {}", customerId, e);
            throw new RuntimeException("Failed to create enrollment token: " + e.getMessage(), e);
        }
    }

    private String buildQrCodeData(AndroidEnterpriseConfig config, String enrollmentTokenValue) {
        return String.format(
                "{" +
                "\"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME\":\"%s/.receiver.AdminReceiver\"," +
                "\"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION\":\"https://play.google.com/managed/download?enterpriseId=%s&packageName=%s\"," +
                "\"android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE\":{" +
                "\"com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN\":\"%s\"" +
                "}" +
                "}",
                config.getDpcPackageName(),
                config.getEnterpriseId().replace("enterprises/", ""),
                config.getDpcPackageName(),
                enrollmentTokenValue
        );
    }

    public List<Device> listDevices(int customerId) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        AndroidEnterpriseConfig config = configDAO.findByCustomerId(customerId);
        try {
            return client.enterprises().devices()
                    .list(config.getEnterpriseId())
                    .execute()
                    .getDevices();
        } catch (IOException e) {
            logger.error("Failed to list devices for customer {}", customerId, e);
            throw new RuntimeException("Failed to list devices: " + e.getMessage(), e);
        }
    }

    public Device getDevice(int customerId, String enterpriseDeviceId) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        try {
            return client.enterprises().devices()
                    .get(enterpriseDeviceId)
                    .execute();
        } catch (IOException e) {
            logger.error("Failed to get device {} for customer {}", enterpriseDeviceId, customerId, e);
            throw new RuntimeException("Failed to get device: " + e.getMessage(), e);
        }
    }

    public Operation issueCommand(int customerId, String enterpriseDeviceId, Command command) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        try {
            return client.enterprises().devices()
                    .issueCommand(enterpriseDeviceId, command)
                    .execute();
        } catch (IOException e) {
            logger.error("Failed to issue command to device {} for customer {}", enterpriseDeviceId, customerId, e);
            throw new RuntimeException("Failed to issue command: " + e.getMessage(), e);
        }
    }

    public void deleteDevice(int customerId, String enterpriseDeviceId) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        try {
            client.enterprises().devices()
                    .delete(enterpriseDeviceId)
                    .execute();
            logger.info("Deleted device {} for customer {}", enterpriseDeviceId, customerId);
        } catch (IOException e) {
            logger.error("Failed to delete device {} for customer {}", enterpriseDeviceId, customerId, e);
            throw new RuntimeException("Failed to delete device: " + e.getMessage(), e);
        }
    }

    public AndroidEnterpriseConfig getConfig(int customerId) {
        return configDAO.findByCustomerId(customerId);
    }

    public AndroidEnterpriseConfig getConfigByEnterpriseId(String enterpriseId) {
        return configDAO.findByEnterpriseId(enterpriseId);
    }

    public EnrollmentToken getEnrollmentToken(int customerId, String tokenName) {
        AndroidManagement client = createClient(customerId);
        if (client == null) {
            throw new IllegalStateException("Enterprise not configured for this customer");
        }

        try {
            return client.enterprises().enrollmentTokens()
                    .get(tokenName)
                    .execute();
        } catch (IOException e) {
            logger.error("Failed to get enrollment token {} for customer {}", tokenName, customerId, e);
            return null;
        }
    }

    public void saveConfig(AndroidEnterpriseConfig config) {
        if (config.getId() == null) {
            config.setCreatedAt(System.currentTimeMillis());
            config.setUpdatedAt(System.currentTimeMillis());
            configDAO.insert(config);
        } else {
            config.setUpdatedAt(System.currentTimeMillis());
            configDAO.update(config);
        }
    }

    public void deleteConfig(int customerId) {
        configDAO.deleteByCustomerId(customerId);
    }

    public static class EnrollmentTokenResult {
        private final EnrollmentToken token;
        private final String qrCodeData;
        private final Integer configurationId;

        public EnrollmentTokenResult(EnrollmentToken token, String qrCodeData, Integer configurationId) {
            this.token = token;
            this.qrCodeData = qrCodeData;
            this.configurationId = configurationId;
        }

        public EnrollmentToken getToken() {
            return token;
        }

        public String getQrCodeData() {
            return qrCodeData;
        }

        public Integer getConfigurationId() {
            return configurationId;
        }
    }

    // Command builders - params field set via reflection or direct access (newer API)
    public static Command buildWipeCommand(boolean userWipe) {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_WIPE");
        try {
            java.lang.reflect.Field paramsField = Command.class.getDeclaredField("params");
            paramsField.setAccessible(true);
            Map<String, Object> params = new HashMap<>();
            params.put("userWipe", userWipe);
            paramsField.set(cmd, params);
        } catch (Exception e) {
            logger.warn("Could not set params on Command: {}", e.getMessage());
        }
        return cmd;
    }

    public static Command buildLockCommand(String message) {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_LOCK");
        if (message != null && !message.isEmpty()) {
            try {
                java.lang.reflect.Field paramsField = Command.class.getDeclaredField("params");
                paramsField.setAccessible(true);
                Map<String, Object> params = new HashMap<>();
                params.put("lockMessage", message);
                paramsField.set(cmd, params);
            } catch (Exception e) {
                logger.warn("Could not set params on Command: {}", e.getMessage());
            }
        }
        return cmd;
    }

    public static Command buildInstallAppCommand(String packageName, Long versionCode) {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_INSTALL_APP");
        try {
            java.lang.reflect.Field paramsField = Command.class.getDeclaredField("params");
            paramsField.setAccessible(true);
            Map<String, Object> params = new HashMap<>();
            params.put("packageName", packageName);
            if (versionCode != null) {
                params.put("versionCode", versionCode);
            }
            paramsField.set(cmd, params);
        } catch (Exception e) {
            logger.warn("Could not set params on Command: {}", e.getMessage());
        }
        return cmd;
    }

    public static Command buildUninstallAppCommand(String packageName) {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_UNINSTALL_APP");
        try {
            java.lang.reflect.Field paramsField = Command.class.getDeclaredField("params");
            paramsField.setAccessible(true);
            Map<String, Object> params = new HashMap<>();
            params.put("packageName", packageName);
            paramsField.set(cmd, params);
        } catch (Exception e) {
            logger.warn("Could not set params on Command: {}", e.getMessage());
        }
        return cmd;
    }

    public static Command buildClearAppDataCommand(String packageName) {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_CLEAR_APP_DATA");
        try {
            java.lang.reflect.Field paramsField = Command.class.getDeclaredField("params");
            paramsField.setAccessible(true);
            Map<String, Object> params = new HashMap<>();
            params.put("packageName", packageName);
            paramsField.set(cmd, params);
        } catch (Exception e) {
            logger.warn("Could not set params on Command: {}", e.getMessage());
        }
        return cmd;
    }

    public static Command buildRebootCommand() {
        Command cmd = new Command();
        cmd.setType("COMMAND_TYPE_REBOOT");
        return cmd;
    }
}