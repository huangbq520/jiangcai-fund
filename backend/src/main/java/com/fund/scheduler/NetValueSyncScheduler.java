package com.fund.scheduler;

import com.fund.entity.UserFund;
import com.fund.mapper.UserFundMapper;
import com.fund.service.FundDataService;
import com.fund.vo.FundData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Component
public class NetValueSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NetValueSyncScheduler.class);

    @Resource
    private UserFundMapper userFundMapper;

    @Resource
    private FundDataService fundDataService;

    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void syncConfirmedNetValue() {
        logger.info("=== 盘后净值同步任务启动 ===");
        long startTime = System.currentTimeMillis();

        List<UserFund> allFunds = userFundMapper.findAll();
        int successCount = 0;
        int failCount = 0;

        for (UserFund fund : allFunds) {
            try {
                FundData fundData = fundDataService.getFundData(fund.getFundCode());

                String unitNetValue = fundData.getUnitNetValue();
                String yesterdayNetValue = fundData.getYesterdayNetValue();

                if (unitNetValue != null && !unitNetValue.isEmpty() && !unitNetValue.equals("null")) {
                    fund.setConfirmedNetValue(new BigDecimal(unitNetValue));
                    fund.setYesterdayNetValue(new BigDecimal(unitNetValue));
                }

                if (yesterdayNetValue != null && !yesterdayNetValue.isEmpty() && !yesterdayNetValue.equals("null")) {
                    fund.setYesterdayNetValue(new BigDecimal(yesterdayNetValue));
                }

                BigDecimal holdShare = fund.getHoldShare() != null ? fund.getHoldShare() : BigDecimal.ZERO;
                BigDecimal todayBuyShare = fund.getTodayBuyShare() != null ? fund.getTodayBuyShare() : BigDecimal.ZERO;
                BigDecimal todaySellShare = fund.getTodaySellShare() != null ? fund.getTodaySellShare() : BigDecimal.ZERO;
                BigDecimal yesterdayShare = holdShare.subtract(todayBuyShare).add(todaySellShare);
                if (yesterdayShare.compareTo(BigDecimal.ZERO) < 0) {
                    yesterdayShare = BigDecimal.ZERO;
                }
                fund.setYesterdayShare(yesterdayShare);

                fund.setProfitStatus(1);
                fund.setProfitConfirmDate(new java.util.Date());
                fund.setLastSyncTime(new Date());

                // 更新持仓金额为当前市值 = 昨日份额 × 单位净值
                BigDecimal holdAmount = yesterdayShare.multiply(
                    fund.getConfirmedNetValue() != null ? fund.getConfirmedNetValue() : BigDecimal.ZERO
                ).setScale(2, java.math.RoundingMode.HALF_UP);
                fund.setHoldAmount(holdAmount);

                // 计算并确认当日收益 = 昨日份额 × (今日单位净值 - 昨日净值)
                BigDecimal todayUnitNV = fund.getConfirmedNetValue();
                BigDecimal yesterdayNV = fund.getYesterdayNetValue();
                if (todayUnitNV != null && yesterdayNV != null
                    && todayUnitNV.compareTo(BigDecimal.ZERO) > 0 && yesterdayNV.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal todayProfit = yesterdayShare.multiply(todayUnitNV.subtract(yesterdayNV))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    fund.setConfirmedProfit(todayProfit);
                }

                userFundMapper.updatePostClose(fund);
                successCount++;
                logger.info("净值同步成功: fundCode={}, yesterdayShare={}, yesterdayNetValue={}",
                    fund.getFundCode(), yesterdayShare, fund.getYesterdayNetValue());
            } catch (Exception e) {
                failCount++;
                logger.error("净值同步失败: fundCode={}, error={}", fund.getFundCode(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("=== 盘后净值同步任务完成 === 成功: {}, 失败: {}, 耗时: {}ms", successCount, failCount, duration);
    }

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void resetDailyFields() {
        logger.info("=== 重置每日字段任务启动 ===");

        List<UserFund> allFunds = userFundMapper.findAll();
        int count = 0;

        for (UserFund fund : allFunds) {
            try {
                fund.setTodayBuyShare(BigDecimal.ZERO);
                fund.setTodaySellShare(BigDecimal.ZERO);
                fund.setProfitStatus(0);
                fund.setProfitConfirmDate(null);
                fund.setConfirmedNetValue(null);
                fund.setConfirmedProfit(null);
                userFundMapper.updateDailyFields(fund);
                count++;
            } catch (Exception e) {
                logger.error("重置每日字段失败: fundCode={}, error={}", fund.getFundCode(), e.getMessage());
            }
        }

        logger.info("=== 重置每日字段任务完成 === 重置: {} 条记录", count);
    }
}