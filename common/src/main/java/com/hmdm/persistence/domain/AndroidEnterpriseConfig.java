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

package com.hmdm.persistence.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

@ApiModel(description = "Android Management API enterprise configuration")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AndroidEnterpriseConfig implements CustomerData, Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("Database ID")
    private Integer id;

    @ApiModelProperty("Customer ID")
    private int customerId;

    @ApiModelProperty("Enterprise ID from Android Management API (e.g. enterprises/LC01abc123)")
    private String enterpriseId;

    @ApiModelProperty("Service Account JSON (encrypted with AES)")
    private String serviceAccountJsonEnc;

    @ApiModelProperty("Google Cloud Project ID")
    private String projectId;

    @ApiModelProperty("DPC package name (your agent app)")
    private String dpcPackageName;

    @ApiModelProperty("Whether enterprise enrollment is enabled")
    private boolean enabled;

    @ApiModelProperty("Creation timestamp")
    private Long createdAt;

    @ApiModelProperty("Last update timestamp")
    private Long updatedAt;

    public AndroidEnterpriseConfig() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public int getCustomerId() {
        return customerId;
    }

    @Override
    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getServiceAccountJsonEnc() {
        return serviceAccountJsonEnc;
    }

    public void setServiceAccountJsonEnc(String serviceAccountJsonEnc) {
        this.serviceAccountJsonEnc = serviceAccountJsonEnc;
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
}