package com.fund.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FundData {
    private String fundCode;
    private String fundName;
    @JsonProperty("dwjz")
    private String unitNetValue;
    @JsonProperty("gsz")
    private String estimatedNetValue;
    @JsonProperty("gszzl")
    private Double estimatedChange;
    @JsonProperty("gztime")
    private String valuationTime;

    private List<FundHolding> holdings = new ArrayList<>();
    private List<FundHistoryTrend> historyTrend = new ArrayList<>();
    private List<CompareIndex> compareIndices = new ArrayList<>();

    private Double yesterdayChange;

    private boolean basicInfoSuccess = false;
    private boolean holdingsSuccess = false;
    private boolean historySuccess = false;

    private List<String> errorMessages = new ArrayList<>();

    private String yesterdayNetValue;
    private boolean tradingDay;
    private boolean priced;
    private Double estPricedCoverage;

    public String getGsz() {
        return estimatedNetValue;
    }

    public String getGztime() {
        return valuationTime;
    }

    public Double getGszzl() {
        return estimatedChange;
    }

    public String getDwjz() {
        return unitNetValue;
    }

    public String getJzrq() {
        return "";
    }

    public boolean isUseEstimatedValue() {
        return tradingDay && !priced;
    }
}