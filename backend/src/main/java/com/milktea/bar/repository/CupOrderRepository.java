package com.milktea.bar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.milktea.bar.entity.CupOrder;

import jakarta.persistence.LockModeType;

public interface CupOrderRepository extends JpaRepository<CupOrder, Long> {

    List<CupOrder> findByStatus(String status);

    List<CupOrder> findByMemberId(Long memberId);

    /**
     * 退款时对订单行加写锁：并发 / 重复点击退款在此串行化，
     * 只有一个事务能看到「已出杯」并完成状态翻转，杜绝重复回补、重复退积分。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CupOrder> findWithLockingById(Long id);
}
