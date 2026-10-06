package com.milktea.bar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.milktea.bar.entity.Member;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** 下单加积分/退款回退积分时对会员行加写锁，保证积分余额与服务端一致 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT m FROM Member m WHERE m.id = :id")
    Member findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
