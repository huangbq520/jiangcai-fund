package com.fund.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class Fund {

    private Long id;

    private Long userId;

    private String fundCode;

    private String fundName;

    @Deprecated
    private String groupType;

    private Long groupId;

    private BigDecimal holdShare;

    private BigDecimal holdAmount;

    private BigDecimal costPrice;

    private Date buyDate;

    private BigDecimal todayBuyShare;

    private BigDecimal todaySellShare;

    private Date createTime;
}
