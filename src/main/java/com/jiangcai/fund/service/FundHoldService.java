package com.jiangcai.fund.service;

import com.jiangcai.fund.dto.PortfolioOverview;
import com.jiangcai.fund.entity.FundStockHolding;
import com.jiangcai.fund.repository.FundStockHoldingRepository;
import com.jiangcai.fund.util.TradeDayUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FundHoldService {
    
    private final FundStockHoldingRepository repository;
    private final FundDataService fundDataService;
    private final TradeDayUtil tradeDayUtil;
    
    public FundHoldService(FundStockHoldingRepository repository, FundDataService fundDataService, TradeDayUtil tradeDayUtil) {
        this.repository = repository;
        this.fundDataService = fundDataService;
        this.tradeDayUtil = tradeDayUtil;
    }
    
    /**
     * 获取指定用户的所有持仓
     */
    public List<FundStockHolding> getAllHoldings(Long userId) {
        return repository.findByUserId(userId);
    }
    
    /**
     * 获取持仓概览（含当日收益计算）
     * 根据市场状态决定展示实时数据或上一交易日结算数据
     */
    public PortfolioOverview getPortfolioOverview(Long userId) {
        PortfolioOverview overview = new PortfolioOverview();
        List<FundStockHolding> holdings = repository.findByUserId(userId);
        
        // 获取市场状态
        boolean isMarketOpen = tradeDayUtil.isMarketOpen();
        overview.setMarketStatus(isMarketOpen ? "OPEN" : "CLOSED");
        overview.setLastTradeDay(tradeDayUtil.getLastTradeDay());
        
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalDailyReturn = BigDecimal.ZERO;
        BigDecimal totalHoldingValue = BigDecimal.ZERO;
        
        for (FundStockHolding holding : holdings) {
            // 获取基金数据
            Map<String, Object> fundInfo = null;
            boolean priceValid = false;
            try {
                fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (Boolean.TRUE.equals(fundInfo.get("success")) && fundInfo.get("dwjz") != null) {
                    priceValid = true;
                }
            } catch (Exception e) {
                // 静默处理
            }
            
            if (priceValid && fundInfo != null) {
                BigDecimal currentPrice = (BigDecimal) fundInfo.get("dwjz");
                BigDecimal yesterdayNav = (BigDecimal) fundInfo.get("yesterdayNav");
                
                if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                    // 更新当前价格
                    holding.setCurrentPrice(currentPrice);
                    
                    // 计算当前市值
                    BigDecimal currentValue = holding.getHoldingAmount().multiply(currentPrice)
                            .setScale(2, RoundingMode.HALF_UP);
                    holding.setCurrentValue(currentValue);
                    
                    // 计算持有收益 = 当前市值 - 持有金额
                    BigDecimal profitLoss = currentValue.subtract(holding.getHoldingValue())
                            .setScale(2, RoundingMode.HALF_UP);
                    holding.setProfitLoss(profitLoss);
                    
                    // 计算收益率
                    if (holding.getHoldingValue().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal profitRate = profitLoss.divide(holding.getHoldingValue(), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
                        holding.setProfitRate(profitRate);
                    }
                    
                    totalAssets = totalAssets.add(currentValue);
                    totalHoldingValue = totalHoldingValue.add(holding.getHoldingValue());
                    
                    // 当日收益累加（仅在开盘状态且有昨日净值时计算）
                    if (isMarketOpen && yesterdayNav != null && yesterdayNav.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal dailyRet = holding.getHoldingAmount()
                                .multiply(currentPrice.subtract(yesterdayNav))
                                .setScale(2, RoundingMode.HALF_UP);
                        totalDailyReturn = totalDailyReturn.add(dailyRet);
                    }
                    
                    repository.save(holding);
                }
            }
        }
        
        // 设置概览数据
        overview.setTotalAssets(totalAssets);
        overview.setFundCount((int) holdings.stream().map(FundStockHolding::getFundCode).distinct().count());
        
        // 当日收益：休市时显示0
        if (isMarketOpen) {
            overview.setDailyReturn(totalDailyReturn);
            
            // 计算当日涨跌幅
            if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal assetsMinusDaily = totalAssets.subtract(totalDailyReturn);
                if (assetsMinusDaily.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal dailyChangeRate = totalDailyReturn.divide(assetsMinusDaily, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    overview.setDailyChangeRate(dailyChangeRate);
                }
            }
        } else {
            overview.setDailyReturn(BigDecimal.ZERO);
            overview.setDailyChangeRate(BigDecimal.ZERO);
        }
        
        // 累计收益
        BigDecimal totalProfitLoss = totalAssets.subtract(totalHoldingValue);
        overview.setTotalProfitLoss(totalProfitLoss);
        
        if (totalHoldingValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalProfitRate = totalProfitLoss.divide(totalHoldingValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            overview.setTotalProfitRate(totalProfitRate);
        }
        
        // 构建持仓明细
        List<PortfolioOverview.HoldingDetail> details = new java.util.ArrayList<>();
        for (FundStockHolding holding : holdings) {
            PortfolioOverview.HoldingDetail detail = new PortfolioOverview.HoldingDetail();
            detail.setId(holding.getId());
            detail.setFundCode(holding.getFundCode());
            detail.setFundName(holding.getFundName());
            detail.setHoldingAmount(holding.getHoldingAmount());
            detail.setHoldingValue(holding.getHoldingValue());
            detail.setCurrentPrice(holding.getCurrentPrice());
            detail.setCurrentValue(holding.getCurrentValue());
            
            // 获取基金信息计算当日数据
            try {
                Map<String, Object> fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (fundInfo.containsKey("yesterdayNav")) {
                    BigDecimal yesterdayNav = (BigDecimal) fundInfo.get("yesterdayNav");
                    BigDecimal currentPrice = (BigDecimal) fundInfo.get("dwjz");
                    detail.setYesterdayNav(yesterdayNav);
                    
                    // 设置价格有效性
                    detail.setPriceValid(currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0);
                    
                    if (isMarketOpen && currentPrice != null && yesterdayNav != null && yesterdayNav.compareTo(BigDecimal.ZERO) > 0) {
                        // 开盘状态：计算当日收益
                        BigDecimal dailyRet = holding.getHoldingAmount()
                                .multiply(currentPrice.subtract(yesterdayNav))
                                .setScale(2, RoundingMode.HALF_UP);
                        detail.setDailyReturn(dailyRet);
                        
                        // 当日涨跌幅
                        BigDecimal dailyChg = currentPrice
                                .subtract(yesterdayNav)
                                .divide(yesterdayNav, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
                        detail.setDailyChangeRate(dailyChg);
                    } else {
                        // 休市状态：当日数据归零
                        detail.setDailyReturn(BigDecimal.ZERO);
                        detail.setDailyChangeRate(BigDecimal.ZERO);
                    }
                } else {
                    detail.setPriceValid(false);
                }
            } catch (Exception e) {
                detail.setPriceValid(false);
            }
            
            // 持有收益（始终显示）
            detail.setProfitLoss(holding.getProfitLoss() != null ? holding.getProfitLoss() : BigDecimal.ZERO);
            detail.setProfitRate(holding.getProfitRate() != null ? holding.getProfitRate() : BigDecimal.ZERO);
            
            // 持仓占比
            if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = holding.getCurrentValue()
                        .divide(totalAssets, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                detail.setPositionRatio(ratio);
            }
            
            details.add(detail);
        }
        
        overview.setHoldings(details);
        
        return overview;
    }
    
    /**
     * 根据基金代码获取持仓
     */
    public Optional<FundStockHolding> getHoldingByFundCode(String fundCode, Long userId) {
        return repository.findByFundCodeAndUserId(fundCode, userId);
    }
    
    /**
     * 添加或更新持仓
     * @param fundCode 基金代码
     * @param amount 持有金额（人民币，单位元）
     * @param manualProfitLoss 手动录入的持有收益（可选）
     * @param userId 用户ID
     */
    @Transactional
    public Map<String, Object> addHolding(String fundCode, BigDecimal amount, BigDecimal manualProfitLoss, Long userId) {
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 获取基金信息
        Map<String, Object> fundInfo = fundDataService.getFundQuote(fundCode);
        if (!(Boolean.TRUE.equals(fundInfo.get("success")))) {
            result.put("success", false);
            result.put("message", "基金代码不存在: " + fundCode);
            return result;
        }
        
        String fundName = (String) fundInfo.getOrDefault("fundName", fundCode);
        BigDecimal currentNav = (BigDecimal) fundInfo.getOrDefault("dwjz", BigDecimal.ZERO);
        
        if (currentNav == null || currentNav.compareTo(BigDecimal.ZERO) == 0) {
            result.put("success", false);
            result.put("message", "无法获取基金净值: " + fundCode);
            return result;
        }
        
        // 检查是否已有持仓
        Optional<FundStockHolding> existingOpt = repository.findByFundCodeAndUserId(fundCode, userId);
        
        FundStockHolding holding;
        if (existingOpt.isPresent()) {
            // 追加持仓：累加持有金额
            holding = existingOpt.get();
            BigDecimal oldHoldingValue = holding.getHoldingValue();
            BigDecimal newHoldingValue = oldHoldingValue.add(amount);
            
            // 计算新的持有份额和成本价
            BigDecimal oldShares = holding.getHoldingAmount();
            BigDecimal newShares = amount.divide(currentNav, 4, RoundingMode.HALF_UP).add(oldShares);
            
            holding.setHoldingValue(newHoldingValue);
            holding.setHoldingAmount(newShares);
            holding.setCurrentPrice(currentNav);
            holding.setCurrentValue(newHoldingValue); // 简化：当前市值=持有金额
            
            // 计算持有收益
            calculateProfitLoss(holding);
            
            result.put("message", "追加持仓成功");
        } else {
            // 新增持仓
            holding = new FundStockHolding();
            holding.setUserId(userId);
            holding.setFundCode(fundCode);
            holding.setFundName(fundName);
            
            // 计算持有份额
            BigDecimal shares = amount.divide(currentNav, 4, RoundingMode.HALF_UP);
            
            holding.setHoldingAmount(shares);
            holding.setHoldingValue(amount);
            holding.setCurrentPrice(currentNav);
            holding.setCurrentValue(amount);
            
            // 如果有手动录入的持有收益，使用手动值；否则自动计算
            if (manualProfitLoss != null) {
                holding.setProfitLoss(manualProfitLoss);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profitRate = manualProfitLoss.divide(amount, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    holding.setProfitRate(profitRate);
                }
            } else {
                holding.setProfitLoss(BigDecimal.ZERO);
                holding.setProfitRate(BigDecimal.ZERO);
            }
            
            result.put("message", "添加持仓成功");
        }
        
        repository.save(holding);
        result.put("success", true);
        result.put("holding", holding);
        
        return result;
    }
    
    /**
     * 卖出/删除持仓
     */
    @Transactional
    public Map<String, Object> sellHolding(String fundCode, BigDecimal amount, Long userId) {
        Map<String, Object> result = new java.util.HashMap<>();

        Optional<FundStockHolding> existingOpt = repository.findByFundCodeAndUserId(fundCode, userId);
        
        if (!existingOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "未找到该基金持仓");
            return result;
        }
        
        FundStockHolding holding = existingOpt.get();
        
        // amount 为要删除的持有金额（元），不是份额
        // 计算对应的份额
        BigDecimal costPrice = holding.getCostPrice();
        if (costPrice.compareTo(BigDecimal.ZERO) == 0) {
            result.put("success", false);
            result.put("message", "成本价异常，无法计算");
            return result;
        }
        
        BigDecimal sharesToSell = amount.divide(costPrice, 4, RoundingMode.HALF_UP);
        
        if (holding.getHoldingAmount().compareTo(sharesToSell) < 0) {
            result.put("success", false);
            result.put("message", "持有金额不足，当前持有: ¥" + holding.getHoldingValue());
            return result;
        }
        
        // 获取最新净值
        Map<String, Object> fundInfo = fundDataService.getFundQuote(fundCode);
        BigDecimal currentNav = (BigDecimal) fundInfo.getOrDefault("dwjz", holding.getCurrentPrice());
        
        if (currentNav == null) {
            currentNav = holding.getCurrentPrice();
        }
        
        BigDecimal sellValue = sharesToSell.multiply(currentNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costValue = sharesToSell.multiply(costPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitLoss = sellValue.subtract(costValue);
        
        // 更新持仓
        BigDecimal newShares = holding.getHoldingAmount().subtract(sharesToSell);
        BigDecimal newHoldingValue = holding.getHoldingValue().subtract(amount);
        
        if (newShares.compareTo(BigDecimal.ZERO) == 0 || newHoldingValue.compareTo(BigDecimal.ZERO) == 0) {
            // 全部卖出
            repository.delete(holding);
            result.put("message", "删除成功");
        } else {
            // 部分卖出
            holding.setHoldingAmount(newShares);
            holding.setHoldingValue(newHoldingValue);
            holding.setCurrentPrice(currentNav);
            holding.setCurrentValue(newHoldingValue);
            calculateProfitLoss(holding);
            repository.save(holding);
            result.put("message", "部分删除成功");
        }
        
        result.put("success", true);
        result.put("sellValue", sellValue);
        result.put("profitLoss", profitLoss);
        
        return result;
    }
    
    /**
     * 更新所有持仓的当前价格和盈亏
     */
    @Transactional
    public List<FundStockHolding> refreshAllHoldings(Long userId) {
        List<FundStockHolding> holdings = repository.findByUserId(userId);
        
        for (FundStockHolding holding : holdings) {
            try {
                Map<String, Object> fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (Boolean.TRUE.equals(fundInfo.get("success"))) {
                    BigDecimal currentPrice = (BigDecimal) fundInfo.get("dwjz");
                    if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                        holding.setCurrentPrice(currentPrice);
                        holding.setCurrentValue(holding.getHoldingAmount().multiply(currentPrice).setScale(2, RoundingMode.HALF_UP));
                        calculateProfitLoss(holding);
                        repository.save(holding);
                    }
                }
            } catch (Exception e) {
                // 静默处理单个基金的更新失败
            }
        }
        
        return holdings;
    }
    
    /**
     * 计算盈亏
     */
    private void calculateProfitLoss(FundStockHolding holding) {
        BigDecimal holdingValue = holding.getHoldingValue();
        BigDecimal currentValue = holding.getCurrentValue();
        
        if (holdingValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitLoss = currentValue.subtract(holdingValue);
            BigDecimal profitRate = profitLoss.divide(holdingValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            holding.setProfitLoss(profitLoss.setScale(2, RoundingMode.HALF_UP));
            holding.setProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
        } else {
            holding.setProfitLoss(BigDecimal.ZERO);
            holding.setProfitRate(BigDecimal.ZERO);
        }
    }
}
