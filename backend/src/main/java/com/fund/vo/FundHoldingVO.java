package com.fund.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FundHoldingVO {
    private String fundCode;
    private String fundName;
    private BigDecimal holdShare;
    private BigDecimal holdAmount;
    private BigDecimal costPrice;
    private String buyDate;

    private String unitNetValue;
    private String estimatedNetValue;
    private Double estimatedChange;
    private String valuationTime;

    private BigDecimal todayProfit;
    private BigDecimal profitRate;
    private BigDecimal currentValue;

    private String currentNetValue;
    private String yesterdayNetValue;
    private BigDecimal shareForTodayProfit;
    private String profitSource;
    private Double yesterdayChange;

    private Integer profitStatus;
    private BigDecimal todayProfitConfirmed;
    private Boolean isPostClose;
}
