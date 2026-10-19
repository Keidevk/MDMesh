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

package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

@ApiModel(description = "Android Enterprise configuration returned to the web UI (never contains secrets)")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnterpriseConfigResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("Database ID")
    private Integer id;

    @ApiModelProperty("Customer ID")
    private int customerId;

    @ApiModelProperty("Enterprise ID from Android Management API")
    private String enterpriseId;

    @ApiModelProperty("Google Cloud Project ID")
    private String projectId;

    @ApiModelProperty("DPC package name")
    private String dpcPackageName;

    @ApiModelProperty("Whether enterprise enrollment is enabled")
    private boolean enabled;

    @ApiModelProperty("Creation timestamp")
    private Long createdAt;

    @ApiModelProperty("Last update timestamp")
    private Long updatedAt;

    @ApiModelProperty("Whether the instance-wide AES encryption password is configured")
    private boolean aesPasswordConfigured;

    public EnterpriseConfigResponse() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDpcPackageName() {
        return dpcPackageName;
    }

    public void setDpcPackageName(String dpcPackageName) {
        this.dpcPackageName = dpcPackageName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isAesPasswordConfigured() {
        return aesPasswordConfigured;
    }

    public void setAesPasswordConfigured(boolean aesPasswordConfigured) {
        this.aesPasswordConfigured = aesPasswordConfigured;
    }
}
