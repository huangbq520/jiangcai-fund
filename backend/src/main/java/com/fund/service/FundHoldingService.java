package com.fund.service;

import com.fund.entity.Fund;
import com.fund.entity.UserFund;
import com.fund.mapper.FundDailyProfitMapper;
import com.fund.mapper.FundMapper;
import com.fund.mapper.UserFundMapper;
import com.fund.vo.FundData;
import com.fund.vo.FundHoldingVO;
import com.fund.vo.PortfolioSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

@Service
public class FundHoldingService {

    private static final Logger logger = LoggerFactory.getLogger(FundHoldingService.class);

    private static final String PROFIT_SOURCE_YESTERDAY_NET_VALUE = "YESTERDAY_NET_VALUE";
    private static final String PROFIT_SOURCE_CHANGEPCT = "CHANGEPCT";
    private static final String PROFIT_SOURCE_NONE = "NONE";

    private static final double EST_PRICED_COVERAGE_THRESHOLD = 0.05;

    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    @Resource
    private FundMapper fundMapper;

    @Resource
    private UserFundMapper userFundMapper;

    @Resource
    private FundDataService fundDataService;

    @Resource
    private FundDailyProfitMapper fundDailyProfitMapper;

    private boolean isAfterTradingClose() {
        return LocalTime.now(BEIJING_ZONE).isAfter(AFTERNOON_CLOSE);
    }

    private boolean isTodayProfitConfirmed(UserFund userFund) {
        if (userFund == null || userFund.getProfitConfirmDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now(BEIJING_ZONE);
        LocalDate confirmDate = userFund.getProfitConfirmDate().toInstant()
                .atZone(BEIJING_ZONE)
                .toLocalDate();
        return today.equals(confirmDate) && userFund.getProfitStatus() != null && userFund.getProfitStatus() == 1;
    }

    private void confirmTodayProfit(UserFund userFund, BigDecimal confirmedNetValue, BigDecimal confirmedProfit) {
        if (userFund == null) {
            return;
        }
        userFund.setProfitStatus(1);
        userFund.setProfitConfirmDate(java.sql.Date.valueOf(LocalDate.now(BEIJING_ZONE)));
        userFund.setConfirmedNetValue(confirmedNetValue);
        userFund.setConfirmedProfit(confirmedProfit);
        userFundMapper.confirmProfit(userFund);
        logger.info("收益已确认: fundCode={}, netValue={}, profit={}, confirmDate={}",
                userFund.getFundCode(), confirmedNetValue, confirmedProfit, userFund.getProfitConfirmDate());
    }

    public List<FundHoldingVO> getHoldingList(Long userId) {
        List<Fund> funds = fundMapper.selectAll(userId);
        List<FundHoldingVO> holdingList = new ArrayList<>();

        for (Fund fund : funds) {
            FundData fundData = fundDataService.getFundData(fund.getFundCode());
            UserFund userFund = userFundMapper.findByUserIdAndFundCode(fund.getUserId(), fund.getFundCode());
            FundHoldingVO vo = calculateProfit(fund, fundData, userFund);

            // 同步持仓金额为当前市值，确保每次查看都是最新市值
            if (vo.getCurrentValue() != null && vo.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                fund.setHoldAmount(vo.getCurrentValue());
                userFundMapper.updateHoldAmount(fund.getFundCode(), vo.getCurrentValue());
            }

            holdingList.add(vo);
        }

        return holdingList;
    }

    public PortfolioSummary getPortfolioSummary(Long userId) {
        PortfolioSummary summary = new PortfolioSummary();
        summary.setTotalAsset(BigDecimal.ZERO);
        summary.setTodayProfit(BigDecimal.ZERO);
        summary.setTotalProfit(BigDecimal.ZERO);
        summary.setTotalProfitRate(BigDecimal.ZERO);
        summary.setFundCount(0);

        List<Fund> funds = fundMapper.selectAll(userId);
        if (funds.isEmpty()) {
            return summary;
        }

        summary.setFundCount(funds.size());

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalTodayProfit = BigDecimal.ZERO;
        BigDecimal totalTodayProfitConfirmed = BigDecimal.ZERO;

        for (Fund fund : funds) {
            FundData fundData = fundDataService.getFundData(fund.getFundCode());
            UserFund userFund = userFundMapper.findByUserIdAndFundCode(fund.getUserId(), fund.getFundCode());
            FundHoldingVO holding = calculateProfit(fund, fundData, userFund);

            if (holding.getShareForTodayProfit() != null &&
                holding.getCurrentNetValue() != null &&
                holding.getShareForTodayProfit().compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal holdShare = holding.getShareForTodayProfit();
                BigDecimal costPrice = fund.getCostPrice() != null ? fund.getCostPrice() : BigDecimal.ZERO;

                if (holdShare.compareTo(BigDecimal.ZERO) > 0 && costPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal cost = holdShare.multiply(costPrice);
                    totalCost = totalCost.add(cost);
                }

                if (holding.getCurrentValue() != null) {
                    totalCurrentValue = totalCurrentValue.add(holding.getCurrentValue());
                }

                if (holding.getTodayProfit() != null) {
                    totalTodayProfit = totalTodayProfit.add(holding.getTodayProfit());
                }

                if (holding.getTodayProfitConfirmed() != null) {
                    totalTodayProfitConfirmed = totalTodayProfitConfirmed.add(holding.getTodayProfitConfirmed());
                }
            }
        }

        summary.setTotalAsset(totalCurrentValue.setScale(2, RoundingMode.HALF_UP));
        summary.setTodayProfit(totalTodayProfit.setScale(2, RoundingMode.HALF_UP));
        summary.setTodayProfitConfirmed(totalTodayProfitConfirmed.setScale(2, RoundingMode.HALF_UP));

        if (totalTodayProfit.compareTo(BigDecimal.ZERO) != 0 && totalCurrentValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal todayRate = totalTodayProfit.divide(totalCurrentValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            summary.setTodayProfitRate(todayRate.setScale(2, RoundingMode.HALF_UP));
        }

        if (totalCurrentValue.compareTo(BigDecimal.ZERO) > 0 && totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profit = totalCurrentValue.subtract(totalCost);
            summary.setTotalProfit(profit);
            BigDecimal profitRate = profit.divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            summary.setTotalProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
        }

        return summary;
    }

    private FundHoldingVO calculateProfit(Fund fund, FundData fundData, UserFund userFund) {
        FundHoldingVO vo = new FundHoldingVO();
        vo.setFundCode(fund.getFundCode());
        vo.setFundName(fund.getFundName());
        vo.setHoldShare(fund.getHoldShare());
        vo.setHoldAmount(fund.getHoldAmount());
        vo.setCostPrice(fund.getCostPrice());

        if (fund.getBuyDate() != null) {
            vo.setBuyDate(new java.text.SimpleDateFormat("yyyy-MM-dd").format(fund.getBuyDate()));
        }

        vo.setUnitNetValue(fundData.getUnitNetValue());
        vo.setEstimatedNetValue(fundData.getEstimatedNetValue());
        vo.setEstimatedChange(fundData.getEstimatedChange());
        vo.setValuationTime(fundData.getValuationTime());
        vo.setYesterdayNetValue(fundData.getYesterdayNetValue());
        vo.setYesterdayChange(fundData.getYesterdayChange());

        String yesterdayDate = LocalDate.now(BEIJING_ZONE).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        BigDecimal yesterdayProfit = fundDailyProfitMapper.getProfitByDate(fund.getUserId(), fund.getFundCode(), yesterdayDate);
        vo.setYesterdayProfit(yesterdayProfit != null ? yesterdayProfit : BigDecimal.ZERO);

        BigDecimal holdShare = fund.getHoldShare() != null ? fund.getHoldShare() : BigDecimal.ZERO;
        BigDecimal todayBuyShare = fund.getTodayBuyShare() != null ? fund.getTodayBuyShare() : BigDecimal.ZERO;
        BigDecimal todaySellShare = fund.getTodaySellShare() != null ? fund.getTodaySellShare() : BigDecimal.ZERO;

        BigDecimal shareForToday = holdShare.subtract(todayBuyShare).add(todaySellShare);
        if (shareForToday.compareTo(BigDecimal.ZERO) < 0) {
            shareForToday = BigDecimal.ZERO;
        }
        vo.setShareForTodayProfit(shareForToday);

        BigDecimal costPrice = fund.getCostPrice();
        if (holdShare == null || costPrice == null ||
            holdShare.compareTo(BigDecimal.ZERO) <= 0 ||
            costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            vo.setTodayProfit(BigDecimal.ZERO);
            vo.setProfitRate(BigDecimal.ZERO);
            vo.setCurrentValue(BigDecimal.ZERO);
            vo.setProfitSource(PROFIT_SOURCE_NONE);
            return vo;
        }

        boolean postClose = isAfterTradingClose();
        vo.setIsPostClose(postClose);

        boolean alreadyConfirmed = isTodayProfitConfirmed(userFund);

        if (alreadyConfirmed) {
            vo.setTodayProfitConfirmed(userFund.getConfirmedProfit() != null ?
                    userFund.getConfirmedProfit().setScale(2, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO);
            vo.setTodayProfit(vo.getTodayProfitConfirmed());
            vo.setProfitStatus(1);
            vo.setProfitSource(PROFIT_SOURCE_YESTERDAY_NET_VALUE);

            String currentNetValue = determineCurrentNetValue(fundData, postClose);
            vo.setCurrentNetValue(currentNetValue);
            if (currentNetValue != null && !currentNetValue.isEmpty()) {
                BigDecimal confirmedNetValueBD = userFund.getConfirmedNetValue() != null ?
                        userFund.getConfirmedNetValue() : new BigDecimal(currentNetValue);
                BigDecimal currentValue = shareForToday.multiply(confirmedNetValueBD).setScale(2, RoundingMode.HALF_UP);
                vo.setCurrentValue(currentValue);

                BigDecimal cost = shareForToday.multiply(costPrice);
                if (cost.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profit = currentValue.subtract(cost);
                    BigDecimal profitRate = profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    vo.setProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
                }
            }
            return vo;
        }

        if (postClose) {
            String unitNetValue = fundData.getUnitNetValue();
            if (unitNetValue != null && !unitNetValue.isEmpty() && !unitNetValue.equals("null")) {
                BigDecimal unitNetValueBD = new BigDecimal(unitNetValue);
                BigDecimal currentValue = shareForToday.multiply(unitNetValueBD).setScale(2, RoundingMode.HALF_UP);

                BigDecimal yesterdayNetValueForCalc = fundData.getYesterdayNetValue() != null ?
                        new BigDecimal(fundData.getYesterdayNetValue()) : BigDecimal.ZERO;
                BigDecimal todayProfit = BigDecimal.ZERO;
                if (yesterdayNetValueForCalc.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal yesterdayValue = shareForToday.multiply(yesterdayNetValueForCalc).setScale(2, RoundingMode.HALF_UP);
                    todayProfit = currentValue.subtract(yesterdayValue);
                }

                vo.setCurrentNetValue(unitNetValue);
                vo.setTodayProfitConfirmed(todayProfit.setScale(2, RoundingMode.HALF_UP));
                vo.setTodayProfit(todayProfit.setScale(2, RoundingMode.HALF_UP));
                vo.setCurrentValue(currentValue);
                vo.setProfitSource(PROFIT_SOURCE_YESTERDAY_NET_VALUE);

                if (!alreadyConfirmed) {
                    confirmTodayProfit(userFund, unitNetValueBD, todayProfit);
                }
                vo.setProfitStatus(1);

                BigDecimal cost = shareForToday.multiply(costPrice);
                if (cost.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profit = currentValue.subtract(cost);
                    BigDecimal profitRate = profit.divide(cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    vo.setProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
                }
                return vo;
            }
        }

        String currentNetValue = determineCurrentNetValue(fundData, postClose);
        vo.setCurrentNetValue(currentNetValue);

        if (currentNetValue == null || currentNetValue.isEmpty()) {
            vo.setTodayProfit(BigDecimal.ZERO);
            vo.setProfitRate(BigDecimal.ZERO);
            vo.setCurrentValue(BigDecimal.ZERO);
            vo.setProfitSource(PROFIT_SOURCE_NONE);
            return vo;
        }

        BigDecimal currentNetValueBD;
        try {
            currentNetValueBD = new BigDecimal(currentNetValue);
        } catch (NumberFormatException e) {
            logger.warn("解析净值失败: fundCode={}, value={}", fund.getFundCode(), currentNetValue);
            vo.setTodayProfit(BigDecimal.ZERO);
            vo.setProfitRate(BigDecimal.ZERO);
            vo.setCurrentValue(BigDecimal.ZERO);
            vo.setProfitSource(PROFIT_SOURCE_NONE);
            return vo;
        }

        BigDecimal currentValue = shareForToday.multiply(currentNetValueBD);
        vo.setCurrentValue(currentValue.setScale(2, RoundingMode.HALF_UP));

        String yesterdayNetValue = fundData.getYesterdayNetValue();
        BigDecimal todayProfit;
        String profitSource;

        if (yesterdayNetValue != null && !yesterdayNetValue.isEmpty() && !yesterdayNetValue.equals("null")) {
            try {
                BigDecimal yesterdayNetValueBD = new BigDecimal(yesterdayNetValue);
                todayProfit = shareForToday.multiply(currentNetValueBD.subtract(yesterdayNetValueBD));
                profitSource = PROFIT_SOURCE_YESTERDAY_NET_VALUE;
            } catch (NumberFormatException e) {
                logger.warn("解析昨日净值失败: fundCode={}, value={}", fund.getFundCode(), yesterdayNetValue);
                todayProfit = calculateProfitByChangePercent(currentValue, fundData.getEstimatedChange());
                profitSource = PROFIT_SOURCE_CHANGEPCT;
            }
        } else {
            todayProfit = calculateProfitByChangePercent(currentValue, fundData.getEstimatedChange());
            profitSource = PROFIT_SOURCE_CHANGEPCT;
        }

        vo.setTodayProfit(todayProfit.setScale(2, RoundingMode.HALF_UP));
        vo.setProfitSource(profitSource);

        if (postClose && userFund != null && userFund.getProfitStatus() != null && userFund.getProfitStatus() == 1) {
            vo.setTodayProfitConfirmed(todayProfit.setScale(2, RoundingMode.HALF_UP));
            vo.setProfitStatus(1);
        } else {
            vo.setProfitStatus(userFund != null && userFund.getProfitStatus() != null ? userFund.getProfitStatus() : 0);
        }

        BigDecimal cost = shareForToday.multiply(costPrice);
        BigDecimal profit = currentValue.subtract(cost);
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = profit.divide(cost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            vo.setProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
        } else {
            vo.setProfitRate(BigDecimal.ZERO);
        }

        return vo;
    }

    private String determineCurrentNetValue(FundData fundData, boolean postClose) {
        if (postClose) {
            String unitNetValue = fundData.getUnitNetValue();
            if (unitNetValue != null && !unitNetValue.isEmpty() && !unitNetValue.equals("null")) {
                return unitNetValue;
            }
        }

        boolean useEstimated = fundData.isUseEstimatedValue();

        if (!useEstimated) {
            return fundData.getUnitNetValue();
        }

        Double estCoverage = fundData.getEstPricedCoverage();
        if (estCoverage != null && estCoverage > EST_PRICED_COVERAGE_THRESHOLD) {
            return fundData.getEstimatedNetValue();
        }

        String gsz = fundData.getEstimatedNetValue();
        if (gsz != null && !gsz.isEmpty() && !gsz.equals("null")) {
            return gsz;
        }

        return fundData.getUnitNetValue();
    }

    private BigDecimal calculateProfitByChangePercent(BigDecimal currentValue, Double estimatedChange) {
        if (currentValue == null || estimatedChange == null || estimatedChange == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal changeRate = BigDecimal.valueOf(estimatedChange)
            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        return currentValue.multiply(changeRate).setScale(2, RoundingMode.HALF_UP);
    }

    public FundHoldingVO getSingleHolding(Long userId, String fundCode) {
        Fund fund = fundMapper.selectByCode(fundCode, userId);
        if (fund == null) return null;
        FundData fundData = fundDataService.getFundData(fundCode);
        UserFund userFund = userFundMapper.findByUserIdAndFundCode(userId, fundCode);
        return calculateProfit(fund, fundData, userFund);
    }

    public void applyModeTwo(UserFund userFund, Map<String, Object> request) {
        FundData fundData = fundDataService.getFundData(userFund.getFundCode());
        String currentNetValue = determineCurrentNetValue(fundData, isAfterTradingClose());
        if (currentNetValue == null || currentNetValue.isEmpty() || currentNetValue.equals("null")) {
            currentNetValue = fundData.getUnitNetValue();
        }
        BigDecimal currentNAV = new BigDecimal(currentNetValue != null && !currentNetValue.equals("null") ? currentNetValue : "1");

        BigDecimal holdAmount = new BigDecimal(request.get("holdAmount").toString());
        BigDecimal profit;
        if (request.containsKey("profitRate") && request.get("profitRate") != null
                && !request.get("profitRate").toString().isEmpty()) {
            BigDecimal profitRate = new BigDecimal(request.get("profitRate").toString());
            BigDecimal cost = holdAmount.divide(BigDecimal.ONE.add(
                    profitRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)), 10, RoundingMode.HALF_UP);
            profit = holdAmount.subtract(cost);
        } else if (request.containsKey("profit") && request.get("profit") != null
                && !request.get("profit").toString().isEmpty()) {
            profit = new BigDecimal(request.get("profit").toString());
        } else {
            profit = BigDecimal.ZERO;
        }

        BigDecimal costAmount = holdAmount.subtract(profit);
        BigDecimal holdShare = holdAmount.divide(currentNAV, 2, RoundingMode.HALF_UP);
        BigDecimal costPrice = holdShare.compareTo(BigDecimal.ZERO) > 0
                ? costAmount.divide(holdShare, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        userFund.setHoldShare(holdShare);
        userFund.setCostPrice(costPrice);
        userFund.setHoldAmount(costAmount.setScale(2, RoundingMode.HALF_UP));
    }

    public void applyBuy(UserFund userFund, BigDecimal adjustShare, BigDecimal adjustCost) {
        BigDecimal oldShare = userFund.getHoldShare() != null ? userFund.getHoldShare() : BigDecimal.ZERO;
        BigDecimal oldCost = userFund.getCostPrice() != null ? userFund.getCostPrice() : BigDecimal.ZERO;
        BigDecimal oldAmount = userFund.getHoldAmount() != null ? userFund.getHoldAmount() : BigDecimal.ZERO;

        BigDecimal newShare = oldShare.add(adjustShare);
        BigDecimal newAmount = oldAmount.add(adjustShare.multiply(adjustCost));
        BigDecimal newCost = newShare.compareTo(BigDecimal.ZERO) > 0
                ? newAmount.divide(newShare, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        userFund.setHoldShare(newShare);
        userFund.setCostPrice(newCost);
        userFund.setHoldAmount(newAmount.setScale(2, RoundingMode.HALF_UP));
    }

    public void applySell(UserFund userFund, BigDecimal adjustShare) {
        BigDecimal oldShare = userFund.getHoldShare() != null ? userFund.getHoldShare() : BigDecimal.ZERO;
        if (adjustShare.compareTo(oldShare) >= 0) {
            adjustShare = oldShare;
        }
        BigDecimal newShare = oldShare.subtract(adjustShare);
        BigDecimal costPrice = userFund.getCostPrice() != null ? userFund.getCostPrice() : BigDecimal.ZERO;
        BigDecimal newAmount = newShare.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);

        userFund.setHoldShare(newShare);
        userFund.setHoldAmount(newAmount);
    }
}
