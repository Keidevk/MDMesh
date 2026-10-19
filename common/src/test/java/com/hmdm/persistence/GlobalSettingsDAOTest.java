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

package com.hmdm.persistence;

import com.hmdm.persistence.mapper.GlobalSettingsMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Covers the thin DAO around the instance-wide Android Enterprise AES password. The mapper is a
 * hand-rolled fake (same style as {@code AndroidEnterpriseServiceTest}) so no database is needed.
 */
public class GlobalSettingsDAOTest {

    @Test
    public void getEnterpriseAesPassword_delegatesToMapper() {
        StubMapper mapper = new StubMapper();
        mapper.password = "stored-secret";

        assertEquals("stored-secret", new GlobalSettingsDAO(mapper).getEnterpriseAesPassword());
    }

    @Test
    public void getEnterpriseAesPassword_propagatesNull() {
        assertNull(new GlobalSettingsDAO(new StubMapper()).getEnterpriseAesPassword());
    }

    @Test
    public void setEnterpriseAesPassword_delegatesToMapper() {
        StubMapper mapper = new StubMapper();
        new GlobalSettingsDAO(mapper).setEnterpriseAesPassword("new-secret");

        assertEquals("new-secret", mapper.password);
        assertEquals(1, mapper.setCalls);
    }

    private static class StubMapper implements GlobalSettingsMapper {
        private String password;
        private int setCalls;

        @Override
        public String getEnterpriseAesPassword() {
            return password;
        }

        @Override
        public void setEnterpriseAesPassword(String password) {
            this.password = password;
            this.setCalls++;
        }
    }
}
