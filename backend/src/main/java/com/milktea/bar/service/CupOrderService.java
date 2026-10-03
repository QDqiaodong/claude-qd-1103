package com.milktea.bar.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.milktea.bar.common.BizException;
import com.milktea.bar.common.Dict;
import com.milktea.bar.dto.CupOrderCreateRequest;
import com.milktea.bar.dto.CupOrderCreateRequest.CupOrderItemInput;
import com.milktea.bar.entity.CupOrder;
import com.milktea.bar.entity.CupOrderItem;
import com.milktea.bar.entity.Member;
import com.milktea.bar.entity.Recipe;
import com.milktea.bar.repository.CupOrderRepository;
import com.milktea.bar.repository.MemberRepository;
import com.milktea.bar.repository.RecipeRepository;

@Service
public class CupOrderService {

    private final CupOrderRepository cupOrderRepository;
    private final RecipeRepository recipeRepository;
    private final MemberRepository memberRepository;

    public CupOrderService(CupOrderRepository cupOrderRepository,
                           RecipeRepository recipeRepository,
                           MemberRepository memberRepository) {
        this.cupOrderRepository = cupOrderRepository;
        this.recipeRepository = recipeRepository;
        this.memberRepository = memberRepository;
    }

    public List<CupOrder> list(String status) {
        if (status != null) {
            return cupOrderRepository.findByStatus(status);
        }
        return cupOrderRepository.findAll();
    }

    /**
     * 出杯下单：校验支付方式与明细，按 配方价格 × 杯数 汇总金额，
     * 若关联会员则累加积分并升级。生成「已出杯」订单。
     */
    public CupOrder create(CupOrderCreateRequest req) {
        if (req == null || req.items == null || req.items.isEmpty()) {
            throw new BizException("出杯明细不能为空");
        }
        if (!Dict.isValidPayType(req.payType)) {
            throw new BizException("支付方式必须是：微信 / 支付宝 / 现金");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<CupOrderItem> items = new ArrayList<>();
        for (CupOrderItemInput it : req.items) {
            if (it.recipeId == null || it.qty == null || it.qty <= 0) {
                throw new BizException("出杯明细的配方或杯数不合法");
            }
            Recipe recipe = recipeRepository.findById(it.recipeId)
                .orElseThrow(() -> new BizException("配方不存在：" + it.recipeId));
            total = total.add(recipe.price.multiply(BigDecimal.valueOf(it.qty)));
            CupOrderItem item = new CupOrderItem();
            item.recipeId = recipe.id;
            item.qty = it.qty;
            items.add(item);
        }

        Member member = null;
        Integer pointsEarned = 0;
        if (req.memberId != null) {
            member = memberRepository.findById(req.memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + req.memberId));
            pointsEarned = total.multiply(BigDecimal.valueOf(Dict.POINTS_PER_YUAN)).intValue();
            member.points = member.points + pointsEarned;
            member.level = Dict.decideMemberLevel(member.points);
            memberRepository.save(member);
        }

        CupOrder order = new CupOrder();
        order.memberId = req.memberId;
        order.payType = req.payType;
        order.total = total;
        order.status = "已出杯";
        order.pointsEarned = pointsEarned;
        order.createdAt = LocalDateTime.now();
        order.items = items;
        return cupOrderRepository.save(order);
    }

    /**
     * 退款：仅「已出杯」可退；若关联会员则按原得积分回退。
     */
    public CupOrder refund(Long id) {
        CupOrder order = cupOrderRepository.findById(id)
            .orElseThrow(() -> new BizException("出杯记录不存在：" + id));
        if (!"已出杯".equals(order.status)) {
            throw new BizException("只有「已出杯」的订单可以退款");
        }
        if (order.memberId != null) {
            Member member = memberRepository.findById(order.memberId)
                .orElseThrow(() -> new BizException("关联会员不存在：" + order.memberId));
            int rollback = (order.pointsEarned == null) ? 0 : order.pointsEarned;
            member.points = Math.max(0, member.points - rollback);
            member.level = Dict.decideMemberLevel(member.points);
            memberRepository.save(member);
        }
        order.status = "已退款";
        return cupOrderRepository.save(order);
    }
}
