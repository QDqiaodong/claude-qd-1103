package com.milktea.bar.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 会员：手机号唯一，积累积分并按阈值升级等级。
 */
@Entity
@Table(name = "member", uniqueConstraints = {
    @UniqueConstraint(name = "uk_member_phone", columnNames = {"phone"})
})
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 24)
    public String phone;

    @Column(nullable = false, length = 32)
    public String name;

    @Column(nullable = false)
    public Integer points;

    @Column(nullable = false, length = 16)
    public String level;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
