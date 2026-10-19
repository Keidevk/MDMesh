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

import java.util.List;

public interface AndroidEnterpriseConfigMapper {

    @Insert({"INSERT INTO android_enterprise_config (customerId, enterpriseId, serviceAccountJsonEnc, projectId, dpcPackageName, enabled, createdAt, updatedAt) " +
            "VALUES (#{customerId}, #{enterpriseId}, #{serviceAccountJsonEnc}, #{projectId}, #{dpcPackageName}, #{enabled}, #{createdAt}, #{updatedAt})"})
    @SelectKey(statement = "SELECT currval('android_enterprise_config_id_seq')", keyColumn = "id", keyProperty = "id",
            before = false, resultType = int.class)
    void insert(AndroidEnterpriseConfig config);

    @Select({"SELECT * FROM android_enterprise_config WHERE customerId = #{customerId}"})
    AndroidEnterpriseConfig findByCustomerId(@Param("customerId") int customerId);

    @Select({"SELECT * FROM android_enterprise_config WHERE enterpriseId = #{enterpriseId}"})
    AndroidEnterpriseConfig findByEnterpriseId(@Param("enterpriseId") String enterpriseId);

    @Select({"SELECT * FROM android_enterprise_config WHERE id = #{id}"})
    AndroidEnterpriseConfig findById(@Param("id") int id);

    @Select({"SELECT * FROM android_enterprise_config ORDER BY id LIMIT 1"})
    AndroidEnterpriseConfig findSingleConfig();

    @Select({"SELECT * FROM android_enterprise_config ORDER BY id"})
    List<AndroidEnterpriseConfig> findAll();

    @Update({"UPDATE android_enterprise_config SET enterpriseId = #{enterpriseId}, serviceAccountJsonEnc = #{serviceAccountJsonEnc}, " +
            "projectId = #{projectId}, dpcPackageName = #{dpcPackageName}, enabled = #{enabled}, updatedAt = #{updatedAt} " +
            "WHERE customerId = #{customerId}"})
    void update(AndroidEnterpriseConfig config);

    @Delete({"DELETE FROM android_enterprise_config WHERE customerId = #{customerId}"})
    void deleteByCustomerId(@Param("customerId") int customerId);
}