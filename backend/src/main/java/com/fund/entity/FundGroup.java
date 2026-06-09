package com.fund.entity;

import lombok.Data;
import java.util.Date;

@Data
public class FundGroup {
    private Long id;

    private Long userId;

    private String name;

    private String groupType;

    private Integer sortOrder;

    private Date createTime;
}
