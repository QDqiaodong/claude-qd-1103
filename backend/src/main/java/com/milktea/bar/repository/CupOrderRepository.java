package com.milktea.bar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.milktea.bar.entity.CupOrder;

public interface CupOrderRepository extends JpaRepository<CupOrder, Long> {

    List<CupOrder> findByStatus(String status);

    List<CupOrder> findByMemberId(Long memberId);
}
