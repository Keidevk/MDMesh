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

package com.hmdm.rest.json.view.devicelist;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Collections;
import java.util.List;

/**
 * <p>A flat list of enterprise-enrolled devices returned by the agent admin resource. Unlike the
 * legacy device list this is not nested in a {@code PaginatedData} wrapper: the frontend expects a
 * {@code {devices, totalItemsCount}} shape.</p>
 */
@ApiModel(description = "A list of enterprise-enrolled devices")
public class EnterpriseDeviceListView {

    private final List<DeviceView> devices;
    private final long totalItemsCount;

    public EnterpriseDeviceListView(List<DeviceView> devices) {
        this.devices = devices != null ? devices : Collections.emptyList();
        this.totalItemsCount = this.devices.size();
    }

    @ApiModelProperty("Enterprise-enrolled devices")
    public List<DeviceView> getDevices() {
        return devices;
    }

    @ApiModelProperty("Total number of enterprise-enrolled devices")
    public long getTotalItemsCount() {
        return totalItemsCount;
    }
}
