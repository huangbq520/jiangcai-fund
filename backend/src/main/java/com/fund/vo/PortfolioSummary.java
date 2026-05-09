package com.fund.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PortfolioSummary {
    private BigDecimal totalAsset;
    private BigDecimal todayProfit;
    private BigDecimal todayProfitConfirmed;
    private BigDecimal todayProfitRate;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private Integer fundCount;
}
