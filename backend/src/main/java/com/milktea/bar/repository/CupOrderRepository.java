package com.milktea.bar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.milktea.bar.entity.CupOrder;

import jakarta.persistence.LockModeType;

public interface CupOrderRepository extends JpaRepository<CupOrder, Long> {

    List<CupOrder> findByStatusOrderByCreatedAtDescIdDesc(String status);

    List<CupOrder> findAllByOrderByCreatedAtDescIdDesc();

    List<CupOrder> findByMemberId(Long memberId);

    /** 退款时对订单行加写锁，重复/并发退款只能有一个事务进入“已出杯”分支 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CupOrder o WHERE o.id = :id")
    Optional<CupOrder> findByIdForUpdate(@Param("id") Long id);
}
