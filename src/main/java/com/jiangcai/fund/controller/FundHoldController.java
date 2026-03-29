package com.jiangcai.fund.controller;

import com.jiangcai.fund.dto.PortfolioOverview;
import com.jiangcai.fund.entity.FundStockHolding;
import com.jiangcai.fund.security.UserPrincipal;
import com.jiangcai.fund.service.FundHoldService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
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
    public Map<String, Object> getPortfolioOverview(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            PortfolioOverview overview = fundHoldService.getPortfolioOverview(principal.getUserId());
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
    public Map<String, Object> getHoldings(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FundStockHolding> holdings = fundHoldService.getAllHoldings(principal.getUserId());
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
    public Map<String, Object> refreshHoldings(@AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FundStockHolding> holdings = fundHoldService.refreshAllHoldings(principal.getUserId());
            result.put("success", true);
            result.put("data", holdings);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
    
    /**
     * 添加/调整持仓
     * @param fundCode 基金代码
     * @param amount 持有金额（人民币，单位元）
     * @param profitLoss 手动录入的持有收益（可选）
     */
    @PostMapping("/add")
    @ResponseBody
    public Map<String, Object> addHolding(
            @RequestParam String fundCode,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) BigDecimal profitLoss,
            @AuthenticationPrincipal UserPrincipal principal) {
        return fundHoldService.addHolding(fundCode, amount, profitLoss, principal.getUserId());
    }
    
    /**
     * 删除持仓（全部删除）
     */
    @PostMapping("/delete")
    @ResponseBody
    public Map<String, Object> deleteHolding(
            @RequestParam String fundCode,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = principal.getUserId();
            fundHoldService.getHoldingByFundCode(fundCode, userId).ifPresent(holding -> {
                fundHoldService.sellHolding(fundCode, holding.getHoldingValue(), userId);
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
