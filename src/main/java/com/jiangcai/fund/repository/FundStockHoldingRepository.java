package com.jiangcai.fund.repository;

import com.jiangcai.fund.entity.FundStockHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundStockHoldingRepository extends JpaRepository<FundStockHolding, Long> {

    List<FundStockHolding> findByUserId(Long userId);

    Optional<FundStockHolding> findByFundCodeAndUserId(String fundCode, Long userId);

    void deleteByFundCodeAndUserId(String fundCode, Long userId);
}
