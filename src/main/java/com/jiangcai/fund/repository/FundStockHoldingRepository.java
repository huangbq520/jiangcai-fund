package com.jiangcai.fund.repository;

import com.jiangcai.fund.entity.FundStockHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundStockHoldingRepository extends JpaRepository<FundStockHolding, Long> {
    
    List<FundStockHolding> findAll();
    
    Optional<FundStockHolding> findByFundCode(String fundCode);
    
    void deleteByFundCode(String fundCode);
}
