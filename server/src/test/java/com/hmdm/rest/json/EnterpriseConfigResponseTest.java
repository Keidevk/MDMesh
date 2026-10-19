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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the "never contains secrets" promise of the enterprise settings response: the web UI must
 * never receive the encrypted service-account blob or the AES password.
 */
public class EnterpriseConfigResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void serializedResponse_neverExposesSecrets() throws Exception {
        EnterpriseConfigResponse response = new EnterpriseConfigResponse();
        response.setId(7);
        response.setCustomerId(3);
        response.setEnterpriseId("enterprises/LC01test");
        response.setProjectId("test-project");
        response.setDpcPackageName("com.mdmesh.agent");
        response.setEnabled(true);
        response.setCreatedAt(1000L);
        response.setUpdatedAt(2000L);
        response.setAesPasswordConfigured(true);

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("enterprises/LC01test"));
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"aesPasswordConfigured\":true"));

        String lower = json.toLowerCase();
        assertFalse("service account blob must not leak: " + json, lower.contains("serviceaccount"));
        assertFalse("aes password must not leak: " + json, lower.contains("aespassword\":"));
        assertFalse("private key material must not leak: " + json, lower.contains("private_key"));
    }
}
