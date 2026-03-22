package com.jiangcai.fund.controller;

import com.jiangcai.fund.dto.PortfolioOverview;
import com.jiangcai.fund.entity.FundStockHolding;
import com.jiangcai.fund.service.FundHoldService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/hold")
public class FundHoldController {
    
    private final FundHoldService fundHoldService;
    
    public FundHoldController(FundHoldService fundHoldService) {
        this.fundHoldService = fundHoldService;
    }
    
    /**
     * 获取持仓概览（含当日收益）
     */
    @GetMapping("/overview")
    @ResponseBody
    public Map<String, Object> getPortfolioOverview() {
        Map<String, Object> result = new HashMap<>();
        try {
            PortfolioOverview overview = fundHoldService.getPortfolioOverview();
            result.put("success", true);
            result.put("data", overview);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
    
    /**
     * 获取所有持仓
     */
    @GetMapping("/list")
    @ResponseBody
    public Map<String, Object> getHoldings() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FundStockHolding> holdings = fundHoldService.getAllHoldings();
            result.put("success", true);
            result.put("data", holdings);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
    
    /**
     * 刷新所有持仓的当前价格和盈亏
     */
    @PostMapping("/refresh")
    @ResponseBody
    public Map<String, Object> refreshHoldings() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FundStockHolding> holdings = fundHoldService.refreshAllHoldings();
            result.put("success", true);
            result.put("data", holdings);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
    
    /**
     * 添加持仓（买入）
     */
    @PostMapping("/add")
    @ResponseBody
    public Map<String, Object> addHolding(
            @RequestParam String fundCode,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) BigDecimal price) {
        return fundHoldService.addHolding(fundCode, amount, price);
    }
    
    /**
     * 卖出持仓
     */
    @PostMapping("/sell")
    @ResponseBody
    public Map<String, Object> sellHolding(
            @RequestParam String fundCode,
            @RequestParam BigDecimal amount) {
        return fundHoldService.sellHolding(fundCode, amount);
    }
    
    /**
     * 删除持仓
     */
    @PostMapping("/delete")
    @ResponseBody
    public Map<String, Object> deleteHolding(@RequestParam String fundCode) {
        Map<String, Object> result = new HashMap<>();
        try {
            fundHoldService.getHoldingByFundCode(fundCode).ifPresent(holding -> {
                fundHoldService.sellHolding(fundCode, holding.getHoldingAmount());
            });
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
