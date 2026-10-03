package com.milktea.bar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 出杯明细：每条记录对应一个配方及其杯数。
 * order_id 由 CupOrder 的 @OneToMany @JoinColumn 维护。
 */
@Entity
@Table(name = "cup_order_item")
public class CupOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "recipe_id", nullable = false)
    public Long recipeId;

    @Column(nullable = false)
    public Integer qty;
}
