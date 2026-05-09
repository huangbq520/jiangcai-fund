package com.fund.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class UserFund {
    private Long id;
    private Long userId;
    private String fundCode;
    private String fundName;
    private BigDecimal holdShare;
    private BigDecimal holdAmount;
    private BigDecimal costPrice;
    private Date buyDate;
    private BigDecimal todayBuyShare;
    private BigDecimal todaySellShare;
    private BigDecimal yesterdayShare;
    private BigDecimal yesterdayNetValue;
    private Integer profitStatus;
    private Date profitConfirmDate;
    private BigDecimal confirmedNetValue;
    private BigDecimal confirmedProfit;
    private Date lastSyncTime;
    private Date createTime;
    private Date updateTime;
}
