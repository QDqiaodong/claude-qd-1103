package com.milktea.bar.common;

import java.math.BigDecimal;
import java.util.List;

/**
 * 奶茶饮品店 · 原料库存与配方出单 业务词典与规则参数表。
 *
 * <p>集中存放状态集合、流程、阈值等规则常量，Service 层引用此处做校验与判定，
 * 便于出题时被要求调整或扩展。</p>
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 原料 ingredient ----------------
    /** 原料类别：茶底 / 奶 / 小料 / 糖浆 */
    public static final List<String> INGREDIENT_CATEGORIES = List.of("茶底", "奶", "小料", "糖浆");

    /** 原料状态：启用 / 停用（停用原料不能进配方） */
    public static final List<String> INGREDIENT_STATUS = List.of("启用", "停用");

    // ---------------- 会员 member ----------------
    public static final List<String> MEMBER_LEVELS = List.of("普通", "银卡", "金卡");

    /** 会员累计积分升级阈值（达到即升级） */
    public static final int SILVER_POINTS = 500;

    public static final int GOLD_POINTS = 2000;

    /** 消费 1 元累计的积分（出杯时按金额累加、退款时反向回退） */
    public static final int POINTS_PER_YUAN = 1;

    // ---------------- 配方 recipe ----------------
    /** 配方售价下限：必须大于该值（0.01 元） */
    public static final BigDecimal RECIPE_MIN_PRICE = new BigDecimal("0.01");

    /** 每出一杯固定消耗的茶底量（茶底用量未在配方中配置，按此统一口径扣减） */
    public static final int BASE_TEA_PER_CUP = 10;

    // ---------------- 出杯记录 cup_order ----------------
    /** 支付方式 */
    public static final List<String> PAY_TYPES = List.of("微信", "支付宝", "现金");

    /** 订单状态：已出杯 / 已退款 */
    public static final List<String> CUP_ORDER_STATUS = List.of("已出杯", "已退款");

    // ---------------- 取值方法 ----------------

    public static boolean isValidIngredientCategory(String category) {
        return category != null && INGREDIENT_CATEGORIES.contains(category);
    }

    public static boolean isValidIngredientStatus(String status) {
        return status != null && INGREDIENT_STATUS.contains(status);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVELS.contains(level);
    }

    /** 按累计积分自动判定会员等级 */
    public static String decideMemberLevel(int points) {
        if (points >= GOLD_POINTS) {
            return "金卡";
        }
        if (points >= SILVER_POINTS) {
            return "银卡";
        }
        return "普通";
    }

    public static boolean isValidPayType(String payType) {
        return payType != null && PAY_TYPES.contains(payType);
    }

    public static boolean isValidCupOrderStatus(String status) {
        return status != null && CUP_ORDER_STATUS.contains(status);
    }

    /** 校验小料份数必须为正整数 */
    public static boolean isValidToppingQty(Integer qty) {
        return qty != null && qty > 0;
    }
}
