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

@ApiModel(description = "Android Enterprise configuration request")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnterpriseConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("Enterprise ID from Android Management API (e.g. enterprises/LC01abc123)")
    private String enterpriseId;

    @ApiModelProperty("Service Account JSON (will be encrypted)")
    private String serviceAccountJson;

    @ApiModelProperty("Google Cloud Project ID")
    private String projectId;

    @ApiModelProperty("DPC package name (your agent app, default: com.mdmesh.agent)")
    private String dpcPackageName = "com.mdmesh.agent";

    @ApiModelProperty("Whether enterprise enrollment is enabled")
    private boolean enabled = true;

    public EnterpriseConfigRequest() {
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public String getServiceAccountJson() {
        return serviceAccountJson;
    }

    public void setServiceAccountJson(String serviceAccountJson) {
        this.serviceAccountJson = serviceAccountJson;
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
}