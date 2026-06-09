package com.fund.mapper;

import com.fund.entity.Fund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FundMapper {

    List<Fund> selectAll(@Param("userId") Long userId);

    /** @deprecated 使用 selectByCodeAndGroupId 替代 */
    @Deprecated
    Fund selectByCode(@Param("fundCode") String fundCode, @Param("userId") Long userId, @Param("groupType") String groupType);

    /** @deprecated 使用 selectByGroupId 替代 */
    @Deprecated
    List<Fund> selectByGroup(@Param("userId") Long userId, @Param("groupType") String groupType);

    Fund selectByCodeAndGroupId(@Param("fundCode") String fundCode, @Param("userId") Long userId, @Param("groupId") Long groupId);

    List<Fund> selectByGroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    int insert(Fund fund);

    /** @deprecated 使用 deleteByCodeAndGroupId 替代 */
    @Deprecated
    int deleteByCode(@Param("fundCode") String fundCode, @Param("userId") Long userId, @Param("groupType") String groupType);

    int deleteByCodeAndGroupId(@Param("fundCode") String fundCode, @Param("userId") Long userId, @Param("groupId") Long groupId);

    int deleteByGroupId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    int countByCode(@Param("fundCode") String fundCode, @Param("userId") Long userId);
}
