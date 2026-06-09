package com.fund.vo;

import lombok.Data;
import java.util.Date;

@Data
public class FundGroupVO {
    private Long id;

    private String name;

    private String groupType;

    private Integer sortOrder;

    private Integer fundCount;

    private Date createTime;
}
