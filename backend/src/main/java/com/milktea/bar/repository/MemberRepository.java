package com.milktea.bar.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.milktea.bar.entity.Member;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** 出杯加积分 / 退款回退积分时锁定会员行，保证并发下积分与等级一致。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findWithLockingById(Long id);
}
