package com.jiangcai.fund.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fund_stock_holding")
public class FundStockHolding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "fund_code", length = 20, nullable = false)
    private String fundCode;
    
    @Column(name = "fund_name", length = 100)
    private String fundName;
    
    @Column(name = "holding_amount", precision = 18, scale = 2)
    private BigDecimal holdingAmount = BigDecimal.ZERO;
    
    @Column(name = "cost_price", precision = 10, scale = 4)
    private BigDecimal costPrice = BigDecimal.ZERO;
    
    @Column(name = "current_price", precision = 10, scale = 4)
    private BigDecimal currentPrice = BigDecimal.ZERO;
    
    @Column(name = "total_cost", precision = 18, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;
    
    @Column(name = "current_value", precision = 18, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;
    
    @Column(name = "profit_loss", precision = 18, scale = 2)
    private BigDecimal profitLoss = BigDecimal.ZERO;
    
    @Column(name = "profit_rate", precision = 10, scale = 4)
    private BigDecimal profitRate = BigDecimal.ZERO;
    
    @Column(name = "create_time")
    private LocalDateTime createTime;
    
    @Column(name = "update_time")
    private LocalDateTime updateTime;
    
    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
