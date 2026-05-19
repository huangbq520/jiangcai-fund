package com.fund.mapper;

import com.fund.entity.UserFund;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UserFundMapper {

    @Select("SELECT * FROM user_fund WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<UserFund> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_fund WHERE user_id = #{userId} AND fund_code = #{fundCode}")
    UserFund findByUserIdAndFundCode(@Param("userId") Long userId, @Param("fundCode") String fundCode);

    @Select("SELECT * FROM user_fund ORDER BY update_time DESC")
    List<UserFund> findAll();

    @Insert("INSERT INTO user_fund (user_id, fund_code, fund_name, hold_share, hold_amount, cost_price, buy_date, " +
            "profit_confirm_date, confirmed_net_value, confirmed_profit, create_time, update_time) " +
            "VALUES (#{userId}, #{fundCode}, #{fundName}, #{holdShare}, #{holdAmount}, #{costPrice}, #{buyDate}, " +
            "#{profitConfirmDate}, #{confirmedNetValue}, #{confirmedProfit}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserFund userFund);

    @Update("UPDATE user_fund SET fund_name = #{fundName}, hold_share = #{holdShare}, " +
            "hold_amount = #{holdAmount}, cost_price = #{costPrice}, buy_date = #{buyDate}, " +
            "profit_confirm_date = #{profitConfirmDate}, confirmed_net_value = #{confirmedNetValue}, " +
            "confirmed_profit = #{confirmedProfit}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND fund_code = #{fundCode}")
    int update(UserFund userFund);

    @Update("UPDATE user_fund SET yesterday_share = #{yesterdayShare}, yesterday_net_value = #{yesterdayNetValue}, " +
            "profit_status = #{profitStatus}, profit_confirm_date = #{profitConfirmDate}, " +
            "confirmed_net_value = #{confirmedNetValue}, confirmed_profit = #{confirmedProfit}, " +
            "last_sync_time = #{lastSyncTime}, update_time = NOW() " +
            "WHERE fund_code = #{fundCode}")
    int updatePostClose(UserFund userFund);

    @Update("UPDATE user_fund SET today_buy_share = #{todayBuyShare}, today_sell_share = #{todaySellShare}, " +
            "profit_status = #{profitStatus}, profit_confirm_date = #{profitConfirmDate}, " +
            "confirmed_net_value = #{confirmedNetValue}, confirmed_profit = #{confirmedProfit}, " +
            "update_time = NOW() " +
            "WHERE fund_code = #{fundCode}")
    int updateDailyFields(UserFund userFund);

    @Update("UPDATE user_fund SET profit_status = #{profitStatus}, profit_confirm_date = #{profitConfirmDate}, " +
            "confirmed_net_value = #{confirmedNetValue}, confirmed_profit = #{confirmedProfit}, update_time = NOW() " +
            "WHERE fund_code = #{fundCode}")
    int confirmProfit(UserFund userFund);

    @Update("UPDATE user_fund SET hold_amount = #{holdAmount}, update_time = NOW() WHERE fund_code = #{fundCode}")
    int updateHoldAmount(@Param("fundCode") String fundCode, @Param("holdAmount") BigDecimal holdAmount);

    @Delete("DELETE FROM user_fund WHERE user_id = #{userId} AND fund_code = #{fundCode}")
    int deleteByUserIdAndFundCode(@Param("userId") Long userId, @Param("fundCode") String fundCode);

    @Select("SELECT COUNT(*) FROM user_fund WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
