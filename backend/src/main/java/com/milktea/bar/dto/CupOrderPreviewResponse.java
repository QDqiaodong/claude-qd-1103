package com.milktea.bar.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出杯预览：提交前按最新配方与库存汇总的「预计扣减」结果。
 * 仅用于页面展示，下单是否成功永远以提交时服务端事务内的最新库存为准。
 */
public class CupOrderPreviewResponse {

    /** 订单金额合计（配方售价 × 合并后杯数） */
    public BigDecimal total;

    /** 若关联会员，预计增加的积分（1 元 1 积分，取整） */
    public Integer pointsEarned;

    /** 合并后的原料需求行 */
    public List<Line> lines;

    /** 是否存在阻断下单的问题（库存不足 / 已停用 / 引用失效 / 明细非法） */
    public boolean blocked;

    /** 全部问题描述（页面红条逐条展示，提交失败时服务端仍会再校验一次） */
    public List<String> problems;

    /**
     * 一种原料一行：同一原料被多个配方、重复明细引用时已合并。
     */
    public static class Line {

        public String ingredientName;

        public String category;

        public String unit;

        /** 本单合计需求量（茶底按每杯用量，小料按份数 × 杯数） */
        public Integer need;

        /** 服务端当前库存 */
        public Integer stock;

        /** 原料当前状态：启用 / 停用；引用失效查无原料时为 null */
        public String status;

        /** 该行是否可用（存在且启用且库存足够） */
        public boolean available;

        /** 该行的问题说明（不足 / 已停用 / 引用失效），无问题为空 */
        public String problem;
    }
}
