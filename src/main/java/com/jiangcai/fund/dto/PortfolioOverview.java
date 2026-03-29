package com.jiangcai.fund.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PortfolioOverview {
    /** 账户总资产 */
    private BigDecimal totalAssets = BigDecimal.ZERO;
    
    /** 持有基金数 */
    private int fundCount = 0;
    
    /** 当日总收益 */
    private BigDecimal dailyReturn = BigDecimal.ZERO;
    
    /** 当日总涨跌幅 */
    private BigDecimal dailyChangeRate = BigDecimal.ZERO;
    
    /** 累计总收益 */
    private BigDecimal totalProfitLoss = BigDecimal.ZERO;
    
    /** 累计总收益率 */
    private BigDecimal totalProfitRate = BigDecimal.ZERO;
    
    /** 持仓明细列表 */
    private List<HoldingDetail> holdings;
    
    @Data
    public static class HoldingDetail {
        private Long id;
        private String fundCode;
        private String fundName;
        
        /** 持有份额（基金单位） */
        private BigDecimal holdingAmount;
        
        /** 持有金额（投资人民币，单位元） */
        private BigDecimal holdingValue;
        
        /** 当前净值 */
        private BigDecimal currentPrice;
        
        private BigDecimal yesterdayNav; // 昨日净值
        private BigDecimal currentValue;
        
        // 当日收益相关
        private BigDecimal dailyReturn = BigDecimal.ZERO;  // 当日收益金额
        private BigDecimal dailyChangeRate = BigDecimal.ZERO; // 当日涨跌幅%
        
        // 持有收益相关
        private BigDecimal profitLoss = BigDecimal.ZERO;  // 持有收益
        private BigDecimal profitRate = BigDecimal.ZERO;   // 持有收益率%
        
        // 持仓占比
        private BigDecimal positionRatio = BigDecimal.ZERO; // 持仓占比%
    }
}
