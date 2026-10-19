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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.hmdm.persistence.CustomerDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.UserRoleSettingsDAO;
import com.hmdm.persistence.domain.UserRoleSettings;
import com.hmdm.persistence.AndroidEnterpriseConfigDAO;
import com.hmdm.persistence.GlobalSettingsDAO;
import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import com.hmdm.security.SecurityContext;
import com.hmdm.service.AndroidEnterpriseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import com.hmdm.persistence.CommonDAO;
import com.hmdm.persistence.domain.Settings;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.EnterpriseConfigRequest;
import com.hmdm.rest.json.EnterpriseConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Api(tags = {"Settings"}, authorizations = {@Authorization("Bearer Token")})
@Singleton
@Path("/private/settings")
public class SettingsResource {

    private static final Logger log = LoggerFactory.getLogger(SettingsResource.class);

    private CommonDAO commonDAO;
    private UserRoleSettingsDAO userRoleSettingsDAO;
    private UnsecureDAO unsecureDAO;
    private AndroidEnterpriseConfigDAO enterpriseConfigDAO;
    private AndroidEnterpriseService enterpriseService;
    private GlobalSettingsDAO globalSettingsDAO;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public SettingsResource() {
    }

    @Inject
    public SettingsResource(CommonDAO commonDAO, UserRoleSettingsDAO userRoleSettingsDAO, UnsecureDAO unsecureDAO,
                            AndroidEnterpriseConfigDAO enterpriseConfigDAO, AndroidEnterpriseService enterpriseService,
                            GlobalSettingsDAO globalSettingsDAO) {
        this.commonDAO = commonDAO;
        this.userRoleSettingsDAO = userRoleSettingsDAO;
        this.unsecureDAO = unsecureDAO;
        this.enterpriseConfigDAO = enterpriseConfigDAO;
        this.enterpriseService = enterpriseService;
        this.globalSettingsDAO = globalSettingsDAO;
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get settings",
            notes = "Gets the current settings",
            response = Settings.class
    )
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSettings() {
        try {
            Settings settings = Optional.ofNullable(this.commonDAO.getSettings()).orElse(new Settings());
            settings.setSingleCustomer(unsecureDAO.isSingleCustomer());
            if (!settings.isSingleCustomer()) {
                this.commonDAO.loadCustomerSettings(settings);
            }
            return Response.OK(settings);
        } catch (Exception e) {
            log.error("Unexpected error when getting the settings for customer", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Get user role settings",
            notes = "Gets the current settings for role of the current user",
            response = UserRoleSettings.class
    )
    @GET
    @Path("/userRole/{roleId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserRoleSettings(@PathParam("roleId") int roleId) {
        try {
            UserRoleSettings settings = this.userRoleSettingsDAO.getUserRoleSettings(roleId);
            if (settings == null) {
                final UserRoleSettings defaultSettings = new UserRoleSettings();
                defaultSettings.setRoleId(roleId);

                settings = defaultSettings;
            }
            return Response.OK(settings);
        } catch (Exception e) {
            log.error("Unexpected error when getting the user role settings for current user", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save default design",
            notes = "Save the settings for Default Design for mobile application",
            response = Settings.class
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/design")
    public Response updateDefaultDesignSettings(Settings settings) {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to update settings by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            this.commonDAO.saveDefaultDesignSettings(settings);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when saving default design settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save user role common settings",
            notes = "Save the settings for user roles",
            response = Settings.class
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/userRoles/common")
    public Response updateUserRoleCommonSettings(List<UserRoleSettings> settings) {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to update settings by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            this.userRoleSettingsDAO.saveCommonSettings(settings);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when saving user roles common settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save language settings",
            notes = "Save the language settings for MDM web application",
            response = Settings.class
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/lang")
    public Response updateLanguageSettings(Settings settings) {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to update settings by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            this.commonDAO.saveLanguageSettings(settings);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when saving language settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @ApiOperation(
            value = "Save misc settings",
            notes = "Save the misc settings for MDM web application",
            response = Settings.class
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/misc")
    public Response updateMiscSettings(Settings settings) {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to update settings by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            if (!unsecureDAO.isSingleCustomer()) {
                // These settings are not allowed to setup in multi-tenant mode
                settings.setCreateNewDevices(false);
                settings.setNewDeviceGroupId(null);
                settings.setNewDeviceConfigurationId(null);
            }
            this.commonDAO.saveMiscSettings(settings);
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when saving misc settings", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    // Android Management API Enterprise Configuration
    // =================================================================================================================

    @ApiOperation(
            value = "Get enterprise configuration",
            notes = "Gets the Android Management API enterprise configuration for the current customer"
    )
    @GET
    @Path("/enterprise")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEnterpriseConfig() {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to get enterprise config by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        Optional<Integer> customerId = SecurityContext.get().getCurrentCustomerId();
        if (!customerId.isPresent()) {
            return Response.PERMISSION_DENIED();
        }

        try {
            AndroidEnterpriseConfig config = enterpriseConfigDAO.findByCustomerId(customerId.get());
            if (config == null) {
                return Response.OK(null);
            }
            return Response.OK(toResponse(config));
        } catch (Exception e) {
            log.error("Unexpected error when getting enterprise config", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @ApiOperation(
            value = "Save enterprise configuration",
            notes = "Saves the Android Management API enterprise configuration for the current customer"
    )
    @POST
    @Path("/enterprise")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveEnterpriseConfig(EnterpriseConfigRequest request) {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to save enterprise config by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        Optional<Integer> customerId = SecurityContext.get().getCurrentCustomerId();
        if (!customerId.isPresent()) {
            return Response.PERMISSION_DENIED();
        }

        try {
            String requestedPassword = isBlank(request.getAesPassword()) ? null : request.getAesPassword();
            String currentPassword = globalSettingsDAO.getEnterpriseAesPassword();
            if (isBlank(currentPassword)) {
                currentPassword = null;
            }
            String effectivePassword = requestedPassword != null ? requestedPassword : currentPassword;

            if (effectivePassword == null) {
                return Response.ERROR("error.enterprise.aespassword.required");
            }

            String serviceAccountJson = request.getServiceAccountJson();
            boolean serviceAccountProvided = !isBlank(serviceAccountJson);
            if (serviceAccountProvided) {
                enterpriseService.validateServiceAccountJson(serviceAccountJson);
            }

            AndroidEnterpriseConfig config = enterpriseConfigDAO.findByCustomerId(customerId.get());
            boolean isNew = config == null;
            if (isNew && !serviceAccountProvided) {
                return Response.ERROR("error.enterprise.serviceaccount.required");
            }

            boolean passwordChanged = requestedPassword != null && !requestedPassword.equals(currentPassword);
            if (passwordChanged) {
                if (currentPassword == null && serviceAccountProvided) {
                    // The administrator is re-pasting the service account JSON, so there is no
                    // need to decrypt the existing ciphertext: the new value will replace it.
                    enterpriseService.setGlobalAesPassword(requestedPassword);
                } else {
                    try {
                        // This also verifies a first-time password against the existing ciphertext.
                        enterpriseService.changeGlobalAesPassword(currentPassword, requestedPassword);
                    } catch (RuntimeException ex) {
                        if (!serviceAccountProvided) {
                            throw ex;
                        }
                        // Existing ciphertexts cannot be decrypted with the stored password.
                        // The administrator supplied a fresh service account JSON for this config,
                        // so reset the global password and let the new value replace the old one.
                        log.warn("Could not re-encrypt existing enterprise configs; resetting the AES password "
                                + "and replacing the current service account JSON", ex);
                        enterpriseService.setGlobalAesPassword(requestedPassword);
                    }
                }
                config = enterpriseConfigDAO.findByCustomerId(customerId.get());
                isNew = config == null;
                if (isNew && !serviceAccountProvided) {
                    return Response.ERROR("error.enterprise.serviceaccount.required");
                }
            }

            if (isNew) {
                config = new AndroidEnterpriseConfig();
                config.setCustomerId(customerId.get());
            }

            config.setEnterpriseId(request.getEnterpriseId());
            config.setProjectId(request.getProjectId());
            config.setDpcPackageName(request.getDpcPackageName() != null ? request.getDpcPackageName() : "com.mdmesh.agent");
            config.setEnabled(request.isEnabled());

            if (serviceAccountProvided) {
                config.setServiceAccountJsonEnc(
                        enterpriseService.encryptServiceAccount(serviceAccountJson, effectivePassword));
            }

            if (isNew) {
                config.setCreatedAt(System.currentTimeMillis());
                config.setUpdatedAt(System.currentTimeMillis());
                enterpriseConfigDAO.insert(config);
            } else {
                config.setUpdatedAt(System.currentTimeMillis());
                enterpriseConfigDAO.update(config);
            }

            return Response.OK(toResponse(config));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid Service Account")) {
                return Response.ERROR("error.enterprise.serviceaccount.invalid");
            }
            if (e.getMessage() != null && e.getMessage().contains("AES decryption failed")) {
                return Response.ERROR("error.enterprise.aespassword.invalid");
            }
            log.error("Unexpected error when saving enterprise config", e);
            return Response.INTERNAL_ERROR();
        } catch (Exception e) {
            log.error("Unexpected error when saving enterprise config", e);
            return Response.INTERNAL_ERROR();
        }
    }

    private EnterpriseConfigResponse toResponse(AndroidEnterpriseConfig config) {
        EnterpriseConfigResponse response = new EnterpriseConfigResponse();
        response.setId(config.getId());
        response.setCustomerId(config.getCustomerId());
        response.setEnterpriseId(config.getEnterpriseId());
        response.setProjectId(config.getProjectId());
        response.setDpcPackageName(config.getDpcPackageName());
        response.setEnabled(config.isEnabled());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        response.setAesPasswordConfigured(enterpriseService.isAesPasswordConfigured());
        return response;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @ApiOperation(
            value = "Delete enterprise configuration",
            notes = "Deletes the Android Management API enterprise configuration for the current customer"
    )
    @DELETE
    @Path("/enterprise")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEnterpriseConfig() {
        if (!SecurityContext.get().hasPermission("settings")) {
            log.error("Unauthorized attempt to delete enterprise config by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        Optional<Integer> customerId = SecurityContext.get().getCurrentCustomerId();
        if (!customerId.isPresent()) {
            return Response.PERMISSION_DENIED();
        }

        try {
            enterpriseConfigDAO.deleteByCustomerId(customerId.get());
            return Response.OK();
        } catch (Exception e) {
            log.error("Unexpected error when deleting enterprise config", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
