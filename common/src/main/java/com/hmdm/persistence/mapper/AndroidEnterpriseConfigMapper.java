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

package com.hmdm.persistence.mapper;

import com.hmdm.persistence.domain.AndroidEnterpriseConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface AndroidEnterpriseConfigMapper {

    @Insert({"INSERT INTO android_enterprise_config (customer_id, enterprise_id, service_account_json_enc, project_id, dpc_package_name, enabled, created_at, updated_at) " +
            "VALUES (#{customerId}, #{enterpriseId}, #{serviceAccountJsonEnc}, #{projectId}, #{dpcPackageName}, #{enabled}, #{createdAt}, #{updatedAt})"})
    @SelectKey(statement = "SELECT currval('android_enterprise_config_id_seq')", keyColumn = "id", keyProperty = "id",
            before = false, resultType = int.class)
    void insert(AndroidEnterpriseConfig config);

    @Select({"SELECT * FROM android_enterprise_config WHERE customer_id = #{customerId}"})
    AndroidEnterpriseConfig findByCustomerId(@Param("customerId") int customerId);

    @Select({"SELECT * FROM android_enterprise_config WHERE enterprise_id = #{enterpriseId}"})
    AndroidEnterpriseConfig findByEnterpriseId(@Param("enterpriseId") String enterpriseId);

    @Update({"UPDATE android_enterprise_config SET enterprise_id = #{enterpriseId}, service_account_json_enc = #{serviceAccountJsonEnc}, " +
            "project_id = #{projectId}, dpc_package_name = #{dpcPackageName}, enabled = #{enabled}, updated_at = #{updatedAt} " +
            "WHERE customer_id = #{customerId}"})
    void update(AndroidEnterpriseConfig config);

    @Delete({"DELETE FROM android_enterprise_config WHERE customer_id = #{customerId}"})
    void deleteByCustomerId(@Param("customerId") int customerId);
}