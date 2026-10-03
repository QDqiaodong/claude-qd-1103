package com.milktea.bar.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 出杯记录：可关联会员（散客为空），按配方明细汇总金额，
 * 状态机 已出杯 → 已退款。
 */
@Entity
@Table(name = "cup_order")
public class CupOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "member_id")
    public Long memberId;

    @Column(name = "pay_type", nullable = false, length = 16)
    public String payType;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal total;

    @Column(nullable = false, length = 16)
    public String status;

    /** 本次出杯为会员带来的积分（退款时按此回退） */
    @Column(name = "points_earned")
    public Integer pointsEarned;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    public List<CupOrderItem> items = new ArrayList<>();
}
