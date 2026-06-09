package com.fund.mapper;

import com.fund.entity.FundGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface FundGroupMapper {

    @Select("SELECT * FROM fund_group WHERE user_id = #{userId} ORDER BY sort_order ASC")
    List<FundGroup> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM fund_group WHERE id = #{id}")
    FundGroup findById(@Param("id") Long id);

    @Select("SELECT * FROM fund_group WHERE user_id = #{userId} AND group_type = #{groupType} LIMIT 1")
    FundGroup findByUserIdAndGroupType(@Param("userId") Long userId, @Param("groupType") String groupType);

    @Select("SELECT IFNULL(MAX(sort_order), 0) FROM fund_group WHERE user_id = #{userId}")
    int getMaxSortOrder(@Param("userId") Long userId);

    @Insert("INSERT INTO fund_group (user_id, name, group_type, sort_order, create_time) " +
            "VALUES (#{userId}, #{name}, #{groupType}, #{sortOrder}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FundGroup group);

    @Update("UPDATE fund_group SET name = #{name} WHERE id = #{id}")
    int update(FundGroup group);

    @Update("UPDATE fund_group SET sort_order = #{sortOrder} WHERE id = #{id}")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    @Delete("DELETE FROM fund_group WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM fund WHERE group_id = #{groupId}")
    int countFundsByGroupId(@Param("groupId") Long groupId);
}
