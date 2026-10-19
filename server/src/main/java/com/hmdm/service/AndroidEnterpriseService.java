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
import com.google.api.services.androidmanagement.v1.model.ClearAppsDataParams;
import com.google.api.services.androidmanagement.v1.model.Command;
import com.google.api.services.androidmanagement.v1.model.Device;
import com.google.api.services.androidmanagement.v1.model.EnrollmentToken;
import com.google.api.services.androidmanagement.v1.model.Operation;
import com.google.api.services.androidmanagement.v1.model.WipeParams;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.hmdm.persistence.AndroidEnterpriseConfigDAO;
import com.hmdm.persistence.GlobalSettingsDAO;
import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import com.hmdm.util.CryptoUtil;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Singleton
public class AndroidEnterpriseService {

    private static final Logger logger = LoggerFactory.getLogger(AndroidEnterpriseService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final long DEFAULT_DURATION_MILLIS = 86400_000L;

    private final AndroidEnterpriseConfigDAO configDAO;
    private final GlobalSettingsDAO globalSettingsDAO;
    private final HttpTransport httpTransport;

    @Inject
    public AndroidEnterpriseService(AndroidEnterpriseConfigDAO configDAO,
                                    GlobalSettingsDAO globalSettingsDAO) {
        this.configDAO = configDAO;
        this.globalSettingsDAO = globalSettingsDAO;
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Failed to initialize HTTP transport for Android Enterprise", e);
        }
    }

    private AndroidManagement createClient(int customerId) {
        AndroidEnterpriseConfig config = configDAO.findByCustomerId(customerId);
        if (config == null || !config.isEnabled()) {
            logger.warn("Enterprise config not found or disabled for customer {}", customerId);
            return null;
        }
        try {
            return buildClient(config);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Creates an Android Management client bound to the single global enterprise configuration.
     * Used by the Single-Tenant enrollment flow: there is exactly one enterprise for this instance.
     */
    private AndroidManagement createGlobalClient() {
        AndroidEnterpriseConfig config = configDAO.findSingleConfig();
        if (config == null || !config.isEnabled()) {
            logger.warn("Global enterprise config not found or disabled");
            return null;
        }
        try {
            return buildClient(config);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private AndroidManagement buildClient(AndroidEnterpriseConfig config) {
        String aesPassword = getAesPassword();
        if (aesPassword == null) {
            throw new IllegalStateException("Android Enterprise encryption password is not configured. "
                    + "Set it in the Android Enterprise settings.");
        }
        try {
            String serviceAccountJson = CryptoUtil.decryptAES(config.getServiceAccountJsonEnc(), aesPassword);
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
            logger.error("Failed to create AndroidManagement client for enterprise {}", config.getEnterpriseId(), e);
            StringBuilder sb = new StringBuilder();
            sb.append("Failed to create Android Management client");
            if (config != null && config.getEnterpriseId() != null) {
                sb.append(" [enterpriseId=").append(config.getEnterpriseId()).append("]");
            }
            sb.append(" [projectId=").append(config != null ? config.getProjectId() : null).append("]");
            sb.append(": ").append(describeThrowable(e, true));
            throw new IllegalStateException(sb.toString(), e);
        }
    }

    /**
     * Builds a complete, human-readable description of a throwable chain: every nested cause
     * (class name + message) plus the full stack trace, so the failure reason can be shown in
     * the web UI even when server logs are not accessible.
     */
    private static String describeThrowable(Throwable e, boolean includeStackTrace) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) {
            if (depth > 0) {
                sb.append(" | caused by: ");
            }
            sb.append(current.getClass().getName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            } else {
                sb.append(" (no message)");
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
            depth++;
        }
        if (includeStackTrace) {
            sb.append(" || stack trace: ");
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            sb.append(sw.toString().replace("\r\n", " >> ").replace('\n', ' '));
        }
        return sb.toString();
    }

    private String getAesPassword() {
        String password = globalSettingsDAO.getEnterpriseAesPassword();
        return password == null || password.trim().isEmpty() ? null : password;
    }

    public boolean isAesPasswordConfigured() {
        return getAesPassword() != null;
    }

    public String encryptServiceAccount(String serviceAccountJson, String aesPassword) {
        return CryptoUtil.encryptAES(serviceAccountJson, aesPassword);
    }

    public void setGlobalAesPassword(String password) {
        globalSettingsDAO.setEnterpriseAesPassword(password);
    }

    /**
     * Re-encrypts every stored service-account JSON with the new instance-wide AES password.
     * The old password must be able to decrypt the existing blobs, otherwise the transaction is
     * aborted and no configuration is left with a password that cannot read it.
     */
    @Transactional
    public void changeGlobalAesPassword(String oldPassword, String newPassword) {
        for (AndroidEnterpriseConfig config : configDAO.findAll()) {
            String encrypted = config.getServiceAccountJsonEnc();
            if (encrypted == null || encrypted.isEmpty()) {
                continue;
            }
            if (oldPassword != null && !oldPassword.isEmpty()) {
                String plain = CryptoUtil.decryptAES(encrypted, oldPassword);
                config.setServiceAccountJsonEnc(CryptoUtil.encryptAES(plain, newPassword));
                configDAO.update(config);
            } else {
                // First password ever configured (no backfill): verify that it can actually decrypt
                // the existing ciphertext before persisting it.
                CryptoUtil.decryptAES(encrypted, newPassword);
            }
        }
        globalSettingsDAO.setEnterpriseAesPassword(newPassword);
    }

    public void validateServiceAccountJson(String serviceAccountJson) {
        try {
            ServiceAccountCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes()))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/androidmanagement"));
        } catch (IOException e) {
            throw new RuntimeException("Invalid Service Account JSON: " + e.getMessage(), e);
        }
    }

    public EnrollmentTokenResult createEnrollmentToken(boolean oneTime, long durationMillis) {
        // Single-Tenant: there is exactly one enterprise configuration for this instance.
        AndroidEnterpriseConfig config = configDAO.findSingleConfig();
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("Android Enterprise is not configured or disabled");
        }

        // The Android Management client is built from the global service account.
        AndroidManagement client;
        try {
            client = createGlobalClient();
        } catch (Exception e) {
            // buildClient already produced a complete diagnostic message; don't bury it.
            throw new IllegalStateException(e.getMessage() != null
                    ? e.getMessage()
                    : "Failed to create Android Management client: " + describeThrowable(e, true), e);
        }
        if (client == null) {
            throw new IllegalStateException("Android Enterprise is not configured or disabled");
        }

        try {
            EnrollmentToken token = new EnrollmentToken();
            // AM API expects a protobuf duration string terminated by 's' (e.g. "86400s").
            token.setDuration((durationMillis / 1000L) + "s");
            // Single-use tokens expire after the first enrollment.
            token.setOneTimeOnly(oneTime);

            EnrollmentToken created = client.enterprises().enrollmentTokens()
                    .create(config.getEnterpriseId(), token)
                    .execute();

            logger.info("Created enrollment token {} (oneTime={})", created.getName(), oneTime);

            String qrCodeData = created.getQrCode();
            if (qrCodeData == null || qrCodeData.trim().isEmpty()) {
                qrCodeData = buildQrCodeData(config, created.getValue());
            }

            return new EnrollmentTokenResult(created, qrCodeData, null);
        } catch (IOException e) {
            logger.error("Failed to create enrollment token", e);
            throw new IllegalStateException("Failed to create enrollment token [enterpriseId="
                    + config.getEnterpriseId() + "]: " + describeThrowable(e, true), e);
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

    // Command builders. The CommandType values must be sent WITHOUT the "COMMAND_TYPE_" prefix
    // (the enum name is serialized verbatim), and parameters must be set on the dedicated typed
    // field (wipeParams / clearAppsDataParams) — there is no generic "params" field.
    public static Command buildWipeCommand(boolean wipeExternalStorage) {
        Command cmd = new Command();
        cmd.setType("WIPE");
        if (wipeExternalStorage) {
            WipeParams wipeParams = new WipeParams();
            wipeParams.setWipeDataFlags(Collections.singletonList("WIPE_EXTERNAL_STORAGE"));
            cmd.setWipeParams(wipeParams);
        }
        return cmd;
    }

    public static Command buildLockCommand() {
        // LOCK takes no parameters (Android Management API does not support a lock message).
        Command cmd = new Command();
        cmd.setType("LOCK");
        return cmd;
    }

    public static Command buildClearAppDataCommand(String packageName) {
        Command cmd = new Command();
        cmd.setType("CLEAR_APP_DATA");
        ClearAppsDataParams params = new ClearAppsDataParams();
        params.setPackageNames(Collections.singletonList(packageName));
        cmd.setClearAppsDataParams(params);
        return cmd;
    }

    public static Command buildRebootCommand() {
        Command cmd = new Command();
        cmd.setType("REBOOT");
        return cmd;
    }
}