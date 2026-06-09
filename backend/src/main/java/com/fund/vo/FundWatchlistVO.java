package com.fund.vo;

import lombok.Data;

@Data
public class FundWatchlistVO {

    private String fundCode;

    private String fundName;

    private String unitNetValue;

    private String estimatedNetValue;

    private Double estimatedChange;

    private Double yesterdayChange;

    private String valuationTime;

    private String latestNetValueDate;

    private String currentNetValue;
}
