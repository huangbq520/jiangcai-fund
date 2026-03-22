package com.jiangcai.fund.controller;

import com.jiangcai.fund.service.FundDataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
public class FundController {
    
    private final FundDataService fundDataService;
    
    public FundController(FundDataService fundDataService) {
        this.fundDataService = fundDataService;
    }
    
    /**
     * 首页 - 基金查询
     */
    @GetMapping
    public String index() {
        return "index";
    }
    
    /**
     * 持仓管理页面
     */
    @GetMapping("/hold")
    public String hold() {
        return "hold";
    }
    
    /**
     * API: 获取基金实时估值
     */
    @GetMapping("/api/fund/quote")
    @ResponseBody
    public Map<String, Object> getFundQuote(@RequestParam String fundCode) {
        return fundDataService.getFundQuote(fundCode);
    }
    
    /**
     * API: 获取基金历史净值
     */
    @GetMapping("/api/fund/history")
    @ResponseBody
    public List<Map<String, Object>> getFundHistory(
            @RequestParam String fundCode,
            @RequestParam(defaultValue = "7") int days) {
        return fundDataService.getFundHistory(fundCode, days);
    }
}
