package com.fund.service;

import com.fund.entity.Fund;
import com.fund.entity.FundDailyProfit;
import com.fund.entity.FundGroup;
import com.fund.entity.UserFund;
import com.fund.mapper.FundDailyProfitMapper;
import com.fund.mapper.FundMapper;
import com.fund.mapper.UserFundMapper;
import com.fund.vo.FundData;
import com.fund.vo.FundHoldingVO;
import com.fund.vo.FundWatchlistVO;
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

    @Resource
    private FundGroupService fundGroupService;

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
        FundGroup holdingGroup = fundGroupService.getHoldingGroup(userId);
        if (holdingGroup == null) {
            logger.warn("用户 {} 没有HOLDING分组，初始化默认分组", userId);
            fundGroupService.initDefaultGroups(userId);
            holdingGroup = fundGroupService.getHoldingGroup(userId);
        }
        if (holdingGroup == null) {
            return new ArrayList<>();
        }
        List<Fund> funds = fundMapper.selectByGroupId(userId, holdingGroup.getId());
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

        FundGroup holdingGroup = fundGroupService.getHoldingGroup(userId);
        if (holdingGroup == null) {
            return summary;
        }
        List<Fund> funds = fundMapper.selectByGroupId(userId, holdingGroup.getId());
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

    public List<FundWatchlistVO> getWatchlistList(Long userId) {
        FundGroup watchlistGroup = fundGroupService.getDefaultWatchlistGroup(userId);
        if (watchlistGroup == null) {
            fundGroupService.initDefaultGroups(userId);
            watchlistGroup = fundGroupService.getDefaultWatchlistGroup(userId);
        }
        if (watchlistGroup == null) {
            return new ArrayList<>();
        }
        List<Fund> funds = fundMapper.selectByGroupId(userId, watchlistGroup.getId());
        List<FundWatchlistVO> list = new ArrayList<>();

        for (Fund fund : funds) {
            FundData fundData = fundDataService.getFundData(fund.getFundCode());
            FundWatchlistVO vo = new FundWatchlistVO();
            vo.setFundCode(fund.getFundCode());
            vo.setFundName(fund.getFundName());
            vo.setUnitNetValue(fundData.getUnitNetValue());
            vo.setEstimatedNetValue(fundData.getEstimatedNetValue());
            vo.setEstimatedChange(fundData.getEstimatedChange());
            vo.setYesterdayChange(fundData.getYesterdayChange());
            vo.setValuationTime(fundData.getValuationTime());
            vo.setLatestNetValueDate(fundData.getLatestNetValueDate());
            vo.setCurrentNetValue(determineCurrentNetValue(fundData, isAfterTradingClose()));
            list.add(vo);
        }

        return list;
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
        vo.setLatestNetValueDate(fundData.getLatestNetValueDate());

        // 计算持仓成本金额和设置持仓成本净值
        vo.setCostPrice(fund.getCostPrice());
        if (fund.getCostPrice() != null && fund.getHoldShare() != null) {
            BigDecimal costAmount = fund.getCostPrice().multiply(fund.getHoldShare()).setScale(2, RoundingMode.HALF_UP);
            vo.setCostAmount(costAmount);
        }

        // 直接从历史数据计算昨日收益，确保是正确的
        BigDecimal yesterdayProfit = calculateYesterdayProfitFromHistory(fund, fundData);
        
        vo.setYesterdayProfit(yesterdayProfit != null ? yesterdayProfit : BigDecimal.ZERO);

        vo.setOneWeekChange(fundData.getOneWeekChange());
        vo.setOneMonthChange(fundData.getOneMonthChange());
        vo.setThreeMonthChange(fundData.getThreeMonthChange());
        vo.setSixMonthChange(fundData.getSixMonthChange());
        vo.setOneYearChange(fundData.getOneYearChange());

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
            // 二次确认：最新净值日期必须是今天，防止数据源不一致导致误显示
            String latestDateCheck = fundData.getLatestNetValueDate();
            String todayCheckStr = new java.text.SimpleDateFormat("MM-dd").format(new java.util.Date());
            if (latestDateCheck != null && latestDateCheck.equals(todayCheckStr)) {
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
            // 最新净值日期不是今天，即使之前确认过也不可用，回退重新计算
            logger.debug("已确认收益但最新净值日期({})不是今天，回退重新计算: fundCode={}",
                    latestDateCheck, fund.getFundCode());
        }

        if (postClose) {
            String unitNetValue = fundData.getUnitNetValue();
            String yesterdayNetValue = fundData.getYesterdayNetValue();
            // 判断今日净值是否已发布：单位净值已更新 且 历史数据日期为今天
            String todayNavDateStr = new java.text.SimpleDateFormat("MM-dd").format(new java.util.Date());
            boolean todayNavConfirmed = unitNetValue != null && !unitNetValue.isEmpty()
                    && !unitNetValue.equals("null")
                    && yesterdayNetValue != null && !yesterdayNetValue.isEmpty()
                    && !yesterdayNetValue.equals(unitNetValue)
                    && fundData.getLatestNetValueDate() != null
                    && fundData.getLatestNetValueDate().equals(todayNavDateStr);

            if (todayNavConfirmed) {
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
            // 今日净值尚未发布（单位净值仍是昨日的），回退到估算净值路径
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

        // 检查最新净值日期是否为今天，不是今天则不计算今日收益
        String latestDate = fundData.getLatestNetValueDate();
        boolean todayNavAvailable = false;
        if (latestDate != null && !latestDate.isEmpty()) {
            String todayStr = new java.text.SimpleDateFormat("MM-dd").format(new java.util.Date());
            todayNavAvailable = latestDate.equals(todayStr);
        }

        BigDecimal todayProfit;
        String profitSource;

        if (todayNavAvailable) {
            // 今日净值已发布，使用估算涨幅计算当日收益
            todayProfit = calculateProfitByChangePercent(currentValue, fundData.getEstimatedChange());
            profitSource = PROFIT_SOURCE_CHANGEPCT;
        } else {
            // 今日净值未发布，不计算今日收益
            todayProfit = BigDecimal.ZERO;
            profitSource = PROFIT_SOURCE_NONE;
            logger.debug("最新净值日期({})不是今天({})，今日收益设为0: fundCode={}",
                    latestDate, new java.text.SimpleDateFormat("MM-dd").format(new java.util.Date()), fund.getFundCode());
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
        // 1. 交易日且估算数据可用时，优先使用估算净值
        if (fundData.isUseEstimatedValue()) {
            Double estCoverage = fundData.getEstPricedCoverage();
            if (estCoverage != null && estCoverage > EST_PRICED_COVERAGE_THRESHOLD) {
                return fundData.getEstimatedNetValue();
            }
            String gsz = fundData.getEstimatedNetValue();
            if (gsz != null && !gsz.isEmpty() && !gsz.equals("null")) {
                return gsz;
            }
        }

        // 2. 盘后：判断今日净值是否已更新（不同于昨日净值）
        if (postClose) {
            String unitNetValue = fundData.getUnitNetValue();
            String yesterdayNetValue = fundData.getYesterdayNetValue();
            if (unitNetValue != null && !unitNetValue.isEmpty() && !unitNetValue.equals("null")) {
                // 如果单位净值与昨日净值不同，说明今日净值已更新
                if (yesterdayNetValue != null && !yesterdayNetValue.isEmpty()
                        && !yesterdayNetValue.equals(unitNetValue)) {
                    return unitNetValue;
                }
                // 今日净值未更新（与昨日相同），但有估算数据就用估算
                String gsz = fundData.getEstimatedNetValue();
                if (gsz != null && !gsz.isEmpty() && !gsz.equals("null")) {
                    return gsz;
                }
                return unitNetValue;
            }
        }

        // 3. 兜底：返回单位净值
        String unitNetValue = fundData.getUnitNetValue();
        if (unitNetValue != null && !unitNetValue.isEmpty() && !unitNetValue.equals("null")) {
            return unitNetValue;
        }
        return null;
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
        FundGroup holdingGroup = fundGroupService.getHoldingGroup(userId);
        if (holdingGroup == null) return null;
        Fund fund = fundMapper.selectByCodeAndGroupId(fundCode, userId, holdingGroup.getId());
        if (fund == null) return null;
        FundData fundData = fundDataService.getFundData(fundCode);
        UserFund userFund = userFundMapper.findByUserIdAndFundCode(userId, fundCode);
        return calculateProfit(fund, fundData, userFund);
    }

    /**
     * 获取任意分组的基金列表（通用方法）
     * HOLDING型分组返回 FundHoldingVO（含财务数据），其他类型返回 FundWatchlistVO
     */
    public List<?> getGroupFundList(Long userId, Long groupId) {
        FundGroup group = fundGroupService.getGroupById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            logger.warn("分组不存在或不属于该用户: groupId={}, userId={}", groupId, userId);
            return new ArrayList<>();
        }

        List<Fund> funds = fundMapper.selectByGroupId(userId, groupId);

        if ("HOLDING".equals(group.getGroupType())) {
            List<FundHoldingVO> holdingList = new ArrayList<>();
            for (Fund fund : funds) {
                FundData fundData = fundDataService.getFundData(fund.getFundCode());
                UserFund userFund = userFundMapper.findByUserIdAndFundCode(fund.getUserId(), fund.getFundCode());
                FundHoldingVO vo = calculateProfit(fund, fundData, userFund);
                if (vo.getCurrentValue() != null && vo.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                    userFundMapper.updateHoldAmount(fund.getFundCode(), vo.getCurrentValue());
                }
                holdingList.add(vo);
            }
            return holdingList;
        } else {
            List<FundWatchlistVO> list = new ArrayList<>();
            for (Fund fund : funds) {
                FundData fundData = fundDataService.getFundData(fund.getFundCode());
                FundWatchlistVO vo = new FundWatchlistVO();
                vo.setFundCode(fund.getFundCode());
                vo.setFundName(fund.getFundName());
                vo.setUnitNetValue(fundData.getUnitNetValue());
                vo.setEstimatedNetValue(fundData.getEstimatedNetValue());
                vo.setEstimatedChange(fundData.getEstimatedChange());
                vo.setYesterdayChange(fundData.getYesterdayChange());
                vo.setValuationTime(fundData.getValuationTime());
                vo.setLatestNetValueDate(fundData.getLatestNetValueDate());
                vo.setCurrentNetValue(determineCurrentNetValue(fundData, isAfterTradingClose()));
                list.add(vo);
            }
            return list;
        }
    }

    public void applyModeTwo(UserFund userFund, Map<String, Object> request) {
        BigDecimal holdAmount = new BigDecimal(request.get("holdAmount").toString());

        // 优先使用前端传递的买入日净值（前端已通过 /nav-at 查询确认）
        BigDecimal effectiveNAV = null;
        if (request.containsKey("costNav") && request.get("costNav") != null
                && !request.get("costNav").toString().isEmpty()) {
            effectiveNAV = new BigDecimal(request.get("costNav").toString());
            logger.info("applyModeTwo: 使用前端传递的买入日净值 {} 计算份额", effectiveNAV);
        }

        // 回退：根据买入日期查询历史净值
        if (effectiveNAV == null && request.containsKey("buyDate") && request.get("buyDate") != null
                && !request.get("buyDate").toString().isEmpty()) {
            String buyDateStr = request.get("buyDate").toString();
            effectiveNAV = fundDataService.getNavByDate(userFund.getFundCode(), buyDateStr);
            if (effectiveNAV != null) {
                logger.info("applyModeTwo: 使用购买日 {} 的历史净值 {} 计算份额", buyDateStr, effectiveNAV);
            } else {
                logger.warn("applyModeTwo: 未找到购买日 {} 的历史净值，回退到当前净值", buyDateStr);
            }
        }

        // 最后回退：使用当前实时净值
        if (effectiveNAV == null) {
            FundData fundData = fundDataService.getFundData(userFund.getFundCode());
            String currentNetValue = determineCurrentNetValue(fundData, isAfterTradingClose());
            if (currentNetValue == null || currentNetValue.isEmpty() || currentNetValue.equals("null")) {
                currentNetValue = fundData.getUnitNetValue();
            }
            effectiveNAV = new BigDecimal(currentNetValue != null && !currentNetValue.equals("null") ? currentNetValue : "1");
            logger.warn("applyModeTwo: 使用当前实时净值 {} 计算份额（非精确）", effectiveNAV);
        }

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
        BigDecimal holdShare = holdAmount.divide(effectiveNAV, 2, RoundingMode.HALF_UP);
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

    /**
     * 从历史数据计算昨日收益
     */
    private BigDecimal calculateYesterdayProfitFromHistory(Fund fund, FundData fundData) {
        List<com.fund.vo.FundHistoryTrend> trends = fundData.getHistoryTrend();
        if (trends == null || trends.size() < 2) {
            return BigDecimal.ZERO;
        }

        // 按时间排序
        trends.sort((t1, t2) -> {
            try {
                long time1 = Long.parseLong(t1.getDate());
                long time2 = Long.parseLong(t2.getDate());
                return Long.compare(time1, time2);
            } catch (Exception e) {
                return 0;
            }
        });

        // 获取最新两条净值数据
        com.fund.vo.FundHistoryTrend latestTrend = trends.get(trends.size() - 1);
        com.fund.vo.FundHistoryTrend previousTrend = trends.get(trends.size() - 2);

        if (latestTrend.getNetValue() == null || previousTrend.getNetValue() == null
            || previousTrend.getNetValue() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal latestNAV = BigDecimal.valueOf(latestTrend.getNetValue());
        BigDecimal previousNAV = BigDecimal.valueOf(previousTrend.getNetValue());
        BigDecimal holdShare = fund.getHoldShare() != null ? fund.getHoldShare() : BigDecimal.ZERO;

        // 昨日收益 = 持有份额 × (最新净值 - 前一天净值)
        return holdShare.multiply(latestNAV.subtract(previousNAV)).setScale(2, RoundingMode.HALF_UP);
    }
}
