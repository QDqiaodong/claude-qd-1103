package com.milktea.bar.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.milktea.bar.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
