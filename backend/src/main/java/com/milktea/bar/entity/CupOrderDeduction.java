package com.milktea.bar.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 出杯原料扣减快照（履约台账）：
 * 订单提交时按「茶底用量 + 小料份数 × 杯数」合并汇总后逐原料落一行，
 * 记录下单当时的原料名称 / 类别 / 单位与实际扣减数量。
 *
 * <p>之后配方、原料如何编辑都不影响历史消耗；退款时严格按本快照精确回补。</p>
 */
@Entity
@Table(name = "cup_order_deduction")
public class CupOrderDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "order_id", nullable = false)
    public Long orderId;

    /** 原料 id（退款回补锚点；原料若被物理删除则为空，回补时按失效处理） */
    @Column(name = "ingredient_id")
    public Long ingredientId;

    @Column(name = "ingredient_name", nullable = false, length = 64)
    public String ingredientName;

    @Column(nullable = false, length = 16)
    public String category;

    @Column(nullable = false, length = 16)
    public String unit;

    /** 本次实际扣减数量（多个配方 / 重复明细已合并） */
    @Column(nullable = false)
    public Integer qty;

    /** 是否已随退款回补：1 已回补 / 0 未回补 */
    @Column(nullable = false)
    public Integer restored = 0;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
