package com.jiangcai.fund.service;

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
