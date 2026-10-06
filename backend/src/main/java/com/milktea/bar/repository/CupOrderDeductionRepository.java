package com.milktea.bar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.milktea.bar.entity.CupOrderDeduction;

public interface CupOrderDeductionRepository extends JpaRepository<CupOrderDeduction, Long> {

    List<CupOrderDeduction> findByOrderId(Long orderId);

    List<CupOrderDeduction> findByOrderIdIn(List<Long> orderIds);
}
