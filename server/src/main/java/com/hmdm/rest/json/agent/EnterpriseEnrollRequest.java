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

package com.hmdm.rest.json.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@ApiModel(description = "Enterprise enrollment request from DPC")
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnterpriseEnrollRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("Enrollment token from Android Management API")
    private String enrollmentToken;

    @ApiModelProperty("Enterprise device ID (enterprises/.../devices/...)")
    private String deviceId;

    @ApiModelProperty("Device capabilities")
    private AgentCapabilities capabilities;

    @ApiModelProperty("Hardware ID (serial number, etc.)")
    private String hardwareId;

    @ApiModelProperty("Android SDK version")
    private Integer androidSdkInt;

    @ApiModelProperty("Android release version")
    private String androidRelease;

    @ApiModelProperty("Device manufacturer")
    private String manufacturer;

    @ApiModelProperty("Device model")
    private String model;

    @ApiModelProperty("Whether device is device owner (fully managed)")
    private Boolean isDeviceOwner;

    public EnterpriseEnrollRequest() {
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(AgentCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public void setHardwareId(String hardwareId) {
        this.hardwareId = hardwareId;
    }

    public Integer getAndroidSdkInt() {
        return androidSdkInt;
    }

    public void setAndroidSdkInt(Integer androidSdkInt) {
        this.androidSdkInt = androidSdkInt;
    }

    public String getAndroidRelease() {
        return androidRelease;
    }

    public void setAndroidRelease(String androidRelease) {
        this.androidRelease = androidRelease;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getIsDeviceOwner() {
        return isDeviceOwner;
    }

    public void setIsDeviceOwner(Boolean isDeviceOwner) {
        this.isDeviceOwner = isDeviceOwner;
    }
}