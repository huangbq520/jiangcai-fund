package com.jiangcai.fund.service;

import com.jiangcai.fund.dto.PortfolioOverview;
import com.jiangcai.fund.entity.FundStockHolding;
import com.jiangcai.fund.repository.FundStockHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FundHoldService {
    
    private final FundStockHoldingRepository repository;
    private final FundDataService fundDataService;
    
    public FundHoldService(FundStockHoldingRepository repository, FundDataService fundDataService) {
        this.repository = repository;
        this.fundDataService = fundDataService;
    }
    
    /**
     * 获取所有持仓
     */
    public List<FundStockHolding> getAllHoldings() {
        return repository.findAll();
    }
    
    /**
     * 获取持仓概览（含当日收益计算）
     */
    public PortfolioOverview getPortfolioOverview() {
        PortfolioOverview overview = new PortfolioOverview();
        List<FundStockHolding> holdings = repository.findAll();
        
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalDailyReturn = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        
        for (FundStockHolding holding : holdings) {
            // 获取实时估值
            try {
                Map<String, Object> fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (Boolean.TRUE.equals(fundInfo.get("success"))) {
                    BigDecimal currentPrice = (BigDecimal) fundInfo.get("dwjz");
                    BigDecimal yesterdayNav = (BigDecimal) fundInfo.get("yesterdayNav");
                    
                    if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                        // 更新当前价格
                        holding.setCurrentPrice(currentPrice);
                        
                        // 计算当前市值
                        BigDecimal currentValue = holding.getHoldingAmount().multiply(currentPrice)
                                .setScale(2, RoundingMode.HALF_UP);
                        holding.setCurrentValue(currentValue);
                        
                        // 计算当日收益
                        if (yesterdayNav != null && yesterdayNav.compareTo(BigDecimal.ZERO) > 0) {
                            holding.setCurrentPrice(currentPrice);
                            // 当日收益 = 持有份额 × (当前净值 - 昨日净值)
                            BigDecimal dailyReturn = holding.getHoldingAmount()
                                    .multiply(currentPrice.subtract(yesterdayNav))
                                    .setScale(2, RoundingMode.HALF_UP);
                            holding.setProfitLoss(dailyReturn);
                        }
                        
                        // 计算持有收益 = 当前市值 - 总成本
                        BigDecimal profitLoss = currentValue.subtract(holding.getTotalCost())
                                .setScale(2, RoundingMode.HALF_UP);
                        holding.setProfitLoss(profitLoss);
                        
                        // 计算收益率
                        if (holding.getTotalCost().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal profitRate = profitLoss.divide(holding.getTotalCost(), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"))
                                    .setScale(2, RoundingMode.HALF_UP);
                            holding.setProfitRate(profitRate);
                        }
                        
                        totalAssets = totalAssets.add(currentValue);
                        totalCost = totalCost.add(holding.getTotalCost());
                        
                        // 当日收益累加（如果有昨日净值）
                        Map<String, Object> latestInfo = fundDataService.getFundQuote(holding.getFundCode());
                        if (latestInfo.containsKey("yesterdayNav")) {
                            BigDecimal yesterday = (BigDecimal) latestInfo.get("yesterdayNav");
                            BigDecimal dailyRet = holding.getHoldingAmount()
                                    .multiply(currentPrice.subtract(yesterday))
                                    .setScale(2, RoundingMode.HALF_UP);
                            totalDailyReturn = totalDailyReturn.add(dailyRet);
                        }
                        
                        repository.save(holding);
                    }
                }
            } catch (Exception e) {
                // 静默处理
            }
        }
        
        // 设置概览数据
        overview.setTotalAssets(totalAssets);
        overview.setFundCount((int) holdings.stream().map(FundStockHolding::getFundCode).distinct().count());
        overview.setDailyReturn(totalDailyReturn);
        
        // 计算当日涨跌幅
        if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyChangeRate = totalDailyReturn.divide(
                    totalAssets.subtract(totalDailyReturn), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            overview.setDailyChangeRate(dailyChangeRate);
        }
        
        // 累计收益
        BigDecimal totalProfitLoss = totalAssets.subtract(totalCost);
        overview.setTotalProfitLoss(totalProfitLoss);
        
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalProfitRate = totalProfitLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
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
            detail.setCostPrice(holding.getCostPrice());
            detail.setCurrentPrice(holding.getCurrentPrice());
            detail.setCurrentValue(holding.getCurrentValue());
            detail.setTotalCost(holding.getTotalCost());
            
            // 获取昨日净值
            try {
                Map<String, Object> fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (fundInfo.containsKey("yesterdayNav")) {
                    BigDecimal yesterdayNav = (BigDecimal) fundInfo.get("yesterdayNav");
                    detail.setYesterdayNav(yesterdayNav);
                    
                    // 当日收益
                    BigDecimal dailyRet = holding.getHoldingAmount()
                            .multiply(holding.getCurrentPrice().subtract(yesterdayNav))
                            .setScale(2, RoundingMode.HALF_UP);
                    detail.setDailyReturn(dailyRet);
                    
                    // 当日涨跌幅
                    if (yesterdayNav.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal dailyChg = holding.getCurrentPrice()
                                .subtract(yesterdayNav)
                                .divide(yesterdayNav, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
                        detail.setDailyChangeRate(dailyChg);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
            
            // 持有收益
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
    public Optional<FundStockHolding> getHoldingByFundCode(String fundCode) {
        return repository.findByFundCode(fundCode);
    }
    
    /**
     * 添加或更新持仓（买入）
     */
    @Transactional
    public Map<String, Object> addHolding(String fundCode, BigDecimal amount, BigDecimal price) {
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 获取基金信息
        Map<String, Object> fundInfo = fundDataService.getFundQuote(fundCode);
        if (!(Boolean.TRUE.equals(fundInfo.get("success")))) {
            result.put("success", false);
            result.put("message", "基金代码不存在: " + fundCode);
            return result;
        }
        
        String fundName = (String) fundInfo.getOrDefault("fundName", fundCode);
        BigDecimal currentPrice = (BigDecimal) fundInfo.getOrDefault("dwjz", price);
        
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
            currentPrice = price;
        }
        
        // 计算总成本
        BigDecimal totalCost = amount.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        
        // 检查是否已有持仓
        Optional<FundStockHolding> existingOpt = repository.findByFundCode(fundCode);
        
        FundStockHolding holding;
        if (existingOpt.isPresent()) {
            // 追加持仓
            holding = existingOpt.get();
            BigDecimal oldAmount = holding.getHoldingAmount();
            BigDecimal oldCost = holding.getTotalCost();
            
            BigDecimal newAmount = oldAmount.add(amount);
            BigDecimal newCost = oldCost.add(totalCost);
            
            // 重新计算平均成本
            BigDecimal avgCost = newCost.divide(newAmount, 4, RoundingMode.HALF_UP);
            
            holding.setHoldingAmount(newAmount);
            holding.setCostPrice(avgCost);
            holding.setTotalCost(newCost);
            holding.setCurrentPrice(currentPrice);
            holding.setCurrentValue(newAmount.multiply(currentPrice));
            
            result.put("message", "追加持仓成功");
        } else {
            // 新增持仓
            holding = new FundStockHolding();
            holding.setFundCode(fundCode);
            holding.setFundName(fundName);
            holding.setHoldingAmount(amount);
            holding.setCostPrice(currentPrice);
            holding.setTotalCost(totalCost);
            holding.setCurrentPrice(currentPrice);
            holding.setCurrentValue(totalCost);
            
            result.put("message", "添加持仓成功");
        }
        
        // 计算盈亏
        calculateProfitLoss(holding);
        
        repository.save(holding);
        
        result.put("success", true);
        result.put("holding", holding);
        
        return result;
    }
    
    /**
     * 卖出持仓
     */
    @Transactional
    public Map<String, Object> sellHolding(String fundCode, BigDecimal amount) {
        Map<String, Object> result = new java.util.HashMap<>();
        
        Optional<FundStockHolding> existingOpt = repository.findByFundCode(fundCode);
        
        if (!existingOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "未找到该基金持仓");
            return result;
        }
        
        FundStockHolding holding = existingOpt.get();
        
        if (holding.getHoldingAmount().compareTo(amount) < 0) {
            result.put("success", false);
            result.put("message", "持有份额不足，当前持有: " + holding.getHoldingAmount());
            return result;
        }
        
        // 获取最新净值
        Map<String, Object> fundInfo = fundDataService.getFundQuote(fundCode);
        BigDecimal currentPrice = (BigDecimal) fundInfo.getOrDefault("dwjz", holding.getCurrentPrice());
        
        if (currentPrice == null) {
            currentPrice = holding.getCurrentPrice();
        }
        
        BigDecimal sellValue = amount.multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costValue = amount.multiply(holding.getCostPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitLoss = sellValue.subtract(costValue);
        
        // 更新持仓
        BigDecimal newAmount = holding.getHoldingAmount().subtract(amount);
        
        if (newAmount.compareTo(BigDecimal.ZERO) == 0) {
            // 全部卖出
            repository.delete(holding);
            result.put("message", "全部卖出成功");
        } else {
            // 部分卖出
            BigDecimal newCost = holding.getTotalCost().subtract(costValue);
            holding.setHoldingAmount(newAmount);
            holding.setTotalCost(newCost);
            holding.setCurrentPrice(currentPrice);
            holding.setCurrentValue(newAmount.multiply(currentPrice));
            calculateProfitLoss(holding);
            repository.save(holding);
            result.put("message", "部分卖出成功");
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
    public List<FundStockHolding> refreshAllHoldings() {
        List<FundStockHolding> holdings = repository.findAll();
        
        for (FundStockHolding holding : holdings) {
            try {
                Map<String, Object> fundInfo = fundDataService.getFundQuote(holding.getFundCode());
                if (Boolean.TRUE.equals(fundInfo.get("success"))) {
                    BigDecimal currentPrice = (BigDecimal) fundInfo.get("dwjz");
                    if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                        holding.setCurrentPrice(currentPrice);
                        holding.setCurrentValue(holding.getHoldingAmount().multiply(currentPrice));
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
        BigDecimal cost = holding.getTotalCost();
        BigDecimal currentValue = holding.getCurrentValue();
        
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitLoss = currentValue.subtract(cost);
            BigDecimal profitRate = profitLoss.divide(cost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            holding.setProfitLoss(profitLoss);
            holding.setProfitRate(profitRate.setScale(2, RoundingMode.HALF_UP));
        } else {
            holding.setProfitLoss(BigDecimal.ZERO);
            holding.setProfitRate(BigDecimal.ZERO);
        }
    }
}
