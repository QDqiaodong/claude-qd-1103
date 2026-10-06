package com.milktea.bar.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
import jakarta.persistence.Transient;

/**
 * 出杯记录：可关联会员（散客为空），按配方明细汇总金额，
 * 状态机 已出杯 → 已退款（仅一次）。
 *
 * <p>原料扣减走 {@link CupOrderDeduction} 快照台账：stockResult/restoreResult
 * 记录扣减与退款回补结果；旧单无快照时只退款回积分、不凭空回补库存。</p>
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

    /** 原料扣减结果：已扣减 / 未扣减（历史旧单） */
    @Column(name = "stock_result", nullable = false, length = 16)
    public String stockResult;

    /** 退款原料回补结果：已回补 / 未回补（旧单无快照）；未退款为空 */
    @Column(name = "restore_result", length = 16)
    public String restoreResult;

    /** 履约备注：如旧单无快照、回补时原料已失效等 */
    @Column(length = 255)
    public String remark;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    public List<CupOrderItem> items = new ArrayList<>();

    /** 原料扣减快照台账（非表字段，由 Service 按 order_id 填充） */
    @Transient
    @JsonIgnoreProperties("orderId")
    public List<CupOrderDeduction> deductions = new ArrayList<>();
}
