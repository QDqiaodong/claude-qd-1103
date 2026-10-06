package com.milktea.bar.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.milktea.bar.common.BizException;
import com.milktea.bar.common.Dict;
import com.milktea.bar.dto.CupOrderCreateRequest;
import com.milktea.bar.dto.CupOrderCreateRequest.CupOrderItemInput;
import com.milktea.bar.dto.CupOrderPreviewResponse;
import com.milktea.bar.dto.CupOrderPreviewResponse.Line;
import com.milktea.bar.entity.CupOrder;
import com.milktea.bar.entity.CupOrderDeduction;
import com.milktea.bar.entity.CupOrderItem;
import com.milktea.bar.entity.Ingredient;
import com.milktea.bar.entity.Member;
import com.milktea.bar.entity.Recipe;
import com.milktea.bar.repository.CupOrderDeductionRepository;
import com.milktea.bar.repository.CupOrderRepository;
import com.milktea.bar.repository.IngredientRepository;
import com.milktea.bar.repository.MemberRepository;
import com.milktea.bar.repository.RecipeRepository;

@Service
public class CupOrderService {

    private final CupOrderRepository cupOrderRepository;
    private final CupOrderDeductionRepository deductionRepository;
    private final RecipeRepository recipeRepository;
    private final MemberRepository memberRepository;
    private final IngredientRepository ingredientRepository;

    public CupOrderService(CupOrderRepository cupOrderRepository,
                           CupOrderDeductionRepository deductionRepository,
                           RecipeRepository recipeRepository,
                           MemberRepository memberRepository,
                           IngredientRepository ingredientRepository) {
        this.cupOrderRepository = cupOrderRepository;
        this.deductionRepository = deductionRepository;
        this.recipeRepository = recipeRepository;
        this.memberRepository = memberRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional(readOnly = true)
    public List<CupOrder> list(String status) {
        List<CupOrder> orders = (status != null)
            ? cupOrderRepository.findByStatus(status)
            : cupOrderRepository.findAll();
        enrichDeductions(orders);
        return orders;
    }

    // ================================================================
    // 出杯预览（不落库）：汇总预计扣减、金额、积分与全部阻断问题
    // ================================================================
    @Transactional(readOnly = true)
    public CupOrderPreviewResponse preview(CupOrderCreateRequest req) {
        CupOrderPreviewResponse resp = new CupOrderPreviewResponse();
        resp.total = BigDecimal.ZERO;
        resp.pointsEarned = 0;
        resp.lines = new ArrayList<>();
        resp.problems = new ArrayList<>();

        // 校验并合并明细（同一配方多条合并杯数），同时汇总金额
        Map<Long, Integer> merged = new LinkedHashMap<>();
        if (req == null || req.items == null || req.items.isEmpty()) {
            resp.problems.add("出杯明细不能为空");
            resp.blocked = true;
            return resp;
        }
        for (CupOrderItemInput it : req.items) {
            if (it.recipeId == null || it.qty == null || it.qty <= 0) {
                resp.problems.add("出杯明细的配方或杯数不合法");
                continue;
            }
            merged.merge(it.recipeId, it.qty, Integer::sum);
        }

        // 关联会员（散客不校验积分）
        Member member = null;
        if (req.memberId != null) {
            member = memberRepository.findById(req.memberId).orElse(null);
            if (member == null) {
                resp.problems.add("会员不存在：" + req.memberId);
            }
        }

        // 按配方汇总「原料名 → 需求量」
        Map<String, Integer> needByName = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            Recipe recipe = recipeRepository.findById(e.getKey()).orElse(null);
            if (recipe == null) {
                resp.problems.add("配方引用失效：配方#" + e.getKey() + " 不存在");
                continue;
            }
            int qty = e.getValue();
            resp.total = resp.total.add(recipe.price.multiply(BigDecimal.valueOf(qty)));
            // 茶底：每杯 BASE_TEA_PER_CUP 个库存单位
            needByName.merge(recipe.baseTea, Dict.BASE_TEA_PER_CUP * qty, Integer::sum);
            // 小料：配方份数 × 杯数
            for (Map.Entry<String, Integer> t : recipe.toppings.entrySet()) {
                needByName.merge(t.getKey(), t.getValue() * qty, Integer::sum);
            }
        }
        if (member != null) {
            resp.pointsEarned = resp.total
                .multiply(BigDecimal.valueOf(Dict.POINTS_PER_YUAN)).intValue();
        }

        // 逐原料对照最新库存 / 停用状态
        Map<String, Ingredient> byName = new LinkedHashMap<>();
        for (Ingredient ing : ingredientRepository.findAllByNameIn(new ArrayList<>(needByName.keySet()))) {
            byName.put(ing.name, ing);
        }
        for (Map.Entry<String, Integer> e : needByName.entrySet()) {
            Line line = new Line();
            line.ingredientName = e.getKey();
            line.need = e.getValue();
            Ingredient ing = byName.get(e.getKey());
            if (ing == null) {
                line.unit = "—";
                line.stock = 0;
                line.available = false;
                line.problem = "原料引用失效：「" + e.getKey() + "」已不存在";
                resp.problems.add(line.problem);
            } else {
                line.category = ing.category;
                line.unit = ing.unit;
                line.stock = ing.stock;
                line.status = ing.status;
                if (!"启用".equals(ing.status)) {
                    line.available = false;
                    line.problem = "原料「" + ing.name + "」已停用";
                    resp.problems.add(line.problem);
                } else if (ing.stock < line.need) {
                    line.available = false;
                    line.problem = "原料「" + ing.name + "」库存不足（需 " + line.need
                        + " " + ing.unit + "，剩 " + ing.stock + " " + ing.unit + "）";
                    resp.problems.add(line.problem);
                } else {
                    line.available = true;
                }
            }
            resp.lines.add(line);
        }

        resp.blocked = !resp.problems.isEmpty();
        return resp;
    }

    // ================================================================
    // 出杯下单：一个事务内完成「锁库存 → 校验 → 扣减 → 积分 → 订单/快照」
    // 任一原料不足 / 停用 / 引用失效 → 整单回滚，积分绝不先行增加
    // ================================================================
    @Transactional
    public CupOrder create(CupOrderCreateRequest req) {
        if (req == null || req.items == null || req.items.isEmpty()) {
            throw new BizException("出杯明细不能为空");
        }
        if (!Dict.isValidPayType(req.payType)) {
            throw new BizException("支付方式必须是：微信 / 支付宝 / 现金");
        }

        // 1. 合并重复明细、校验配方引用并汇总金额
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CupOrderItemInput it : req.items) {
            if (it.recipeId == null || it.qty == null || it.qty <= 0) {
                throw new BizException("出杯明细的配方或杯数不合法");
            }
            merged.merge(it.recipeId, it.qty, Integer::sum);
        }
        Map<Long, Recipe> recipeMap = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            Recipe recipe = recipeRepository.findById(e.getKey())
                .orElseThrow(() -> new BizException("配方引用失效：配方#" + e.getKey() + " 不存在"));
            recipeMap.put(e.getKey(), recipe);
            total = total.add(recipe.price.multiply(BigDecimal.valueOf(e.getValue())));
        }

        // 2. 汇总本单原料需求（茶底 + 小料，跨配方合并）
        Map<String, Integer> needByName = aggregateNeeds(merged, recipeMap);

        // 3. 直接按名称对原料行加写锁（事务内首次读取，确保拿到的是锁内最新行；
        //    固定 id 升序加锁防死锁）。并发出杯争用同一原料时在此排队。
        List<Ingredient> lockedIngredients =
            ingredientRepository.findAllByNameForUpdate(new ArrayList<>(needByName.keySet()));

        // 4. 锁内以最新库存做最终校验，收集全部问题后一次性抛出（整单不落库）
        List<String> problems = new ArrayList<>();
        Map<String, Ingredient> lockedByName = new LinkedHashMap<>();
        for (Ingredient ing : lockedIngredients) {
            lockedByName.put(ing.name, ing);
        }
        for (Map.Entry<String, Integer> e : needByName.entrySet()) {
            Ingredient fresh = lockedByName.get(e.getKey());
            if (fresh == null) {
                problems.add("原料引用失效：「" + e.getKey() + "」已不存在");
            } else if (!"启用".equals(fresh.status)) {
                problems.add("原料「" + fresh.name + "」已停用，无法出杯");
            } else if (fresh.stock < e.getValue()) {
                problems.add("原料「" + fresh.name + "」库存不足（需 " + e.getValue()
                    + " " + fresh.unit + "，剩 " + fresh.stock + " " + fresh.unit + "）");
            }
        }
        if (!problems.isEmpty()) {
            throw new BizException("出杯失败，整单未扣减：" + String.join("；", problems));
        }

        // 5. 校验通过后才在同一事务内扣减库存，并逐原料留存扣减快照
        LocalDateTime now = LocalDateTime.now();
        List<CupOrderDeduction> deductions = new ArrayList<>();
        for (Map.Entry<String, Integer> e : needByName.entrySet()) {
            Ingredient fresh = lockedByName.get(e.getKey());
            fresh.stock = fresh.stock - e.getValue();
            ingredientRepository.save(fresh);

            CupOrderDeduction d = new CupOrderDeduction();
            d.ingredientId = fresh.id;
            d.ingredientName = fresh.name;
            d.category = fresh.category;
            d.unit = fresh.unit;
            d.qty = e.getValue();
            d.restored = 0;
            d.createdAt = now;
            deductions.add(d);
        }

        // 6. 会员加积分（与扣库存在同一事务，失败一起回滚）
        Member member = null;
        int pointsEarned = 0;
        if (req.memberId != null) {
            member = memberRepository.findWithLockingById(req.memberId)
                .orElseThrow(() -> new BizException("会员不存在：" + req.memberId));
            pointsEarned = total.multiply(BigDecimal.valueOf(Dict.POINTS_PER_YUAN)).intValue();
            member.points = member.points + pointsEarned;
            member.level = Dict.decideMemberLevel(member.points);
            memberRepository.save(member);
        }

        // 7. 落订单、合并后的明细、原料扣减快照——同一事务结果
        CupOrder order = new CupOrder();
        order.memberId = req.memberId;
        order.payType = req.payType;
        order.total = total;
        order.status = "已出杯";
        order.pointsEarned = pointsEarned;
        order.stockResult = "已扣减";
        order.restoreResult = null;
        order.remark = null;
        order.createdAt = now;
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            CupOrderItem item = new CupOrderItem();
            item.recipeId = e.getKey();
            item.qty = e.getValue();
            order.items.add(item);
        }
        CupOrder saved = cupOrderRepository.save(order);
        for (CupOrderDeduction d : deductions) {
            d.orderId = saved.id;
        }
        deductionRepository.saveAll(deductions);
        saved.deductions = deductions;
        return saved;
    }

    // ================================================================
    // 退款：仅「已出杯」执行一次；锁订单行判定状态，按快照精确回补并回退积分
    // 重复点击 / 并发退款只有一个成功，旧单无快照不凭空回补库存
    // ================================================================
    @Transactional
    public CupOrder refund(Long id) {
        CupOrder order = cupOrderRepository.findWithLockingById(id)
            .orElseThrow(() -> new BizException("出杯记录不存在：" + id));
        // 锁内判定：重复点击或并发退款得到稳定结果，状态翻转只发生一次
        if ("已退款".equals(order.status)) {
            enrichDeductions(List.of(order));
            throw new BizException("该订单已退款，不能重复退款");
        }
        if (!"已出杯".equals(order.status)) {
            throw new BizException("只有「已出杯」的订单可以退款");
        }

        List<CupOrderDeduction> deductions = deductionRepository.findByOrderId(id);

        // 1. 回退原积分（严格按订单记录的 pointsEarned）
        if (order.memberId != null && order.pointsEarned != null && order.pointsEarned > 0) {
            Member member = memberRepository.findWithLockingById(order.memberId)
                .orElseThrow(() -> new BizException("关联会员不存在：" + order.memberId));
            member.points = Math.max(0, member.points - order.pointsEarned);
            member.level = Dict.decideMemberLevel(member.points);
            memberRepository.save(member);
        }

        // 2. 按快照精确回补原料；无快照的历史旧单只退款，绝不凭空回补
        List<String> notes = new ArrayList<>();
        if (order.remark != null && !order.remark.isBlank()) {
            notes.add(order.remark);
        }
        if (deductions.isEmpty()) {
            order.stockResult = "未扣减";
            order.restoreResult = "未回补";
            notes.add("历史旧单无原料扣减快照，未回补库存");
        } else {
            List<Long> ingIds = deductions.stream()
                .map(d -> d.ingredientId).filter(java.util.Objects::nonNull).distinct().sorted().toList();
            Map<Long, Ingredient> ingById = new LinkedHashMap<>();
            if (!ingIds.isEmpty()) {
                for (Ingredient ing : ingredientRepository.findAllByIdForUpdate(ingIds)) {
                    ingById.put(ing.id, ing);
                }
            }
            for (CupOrderDeduction d : deductions) {
                Ingredient ing = (d.ingredientId == null) ? null : ingById.get(d.ingredientId);
                if (ing == null) {
                    notes.add("快照原料「" + d.ingredientName + "」已失效，回补 "
                        + d.qty + " " + d.unit + " 未入账");
                    continue;
                }
                ing.stock = ing.stock + d.qty;
                ingredientRepository.save(ing);
                d.restored = 1;
                deductionRepository.save(d);
            }
            order.stockResult = "已扣减";
            order.restoreResult = notes.isEmpty() ? "已回补" : "未回补";
        }

        // 3. 状态翻转（只发生一次）
        order.status = "已退款";
        order.remark = notes.isEmpty() ? null : String.join("；", notes);
        CupOrder saved = cupOrderRepository.save(order);
        saved.deductions = deductions;
        return saved;
    }

    // ----------------------------------------------------------------
    // 辅助方法
    // ----------------------------------------------------------------

    /** 按合并后的明细汇总原料需求：茶底每杯 BASE_TEA_PER_CUP，小料份数 × 杯数。 */
    private Map<String, Integer> aggregateNeeds(Map<Long, Integer> merged,
                                                Map<Long, Recipe> recipeMap) {
        Map<String, Integer> needByName = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            Recipe recipe = recipeMap.get(e.getKey());
            int qty = e.getValue();
            needByName.merge(recipe.baseTea, Dict.BASE_TEA_PER_CUP * qty, Integer::sum);
            for (Map.Entry<String, Integer> t : recipe.toppings.entrySet()) {
                needByName.merge(t.getKey(), t.getValue() * qty, Integer::sum);
            }
        }
        return needByName;
    }

    /** 为订单批量填充扣减快照台账。 */
    private void enrichDeductions(List<CupOrder> orders) {
        if (orders.isEmpty()) {
            return;
        }
        List<Long> ids = orders.stream().map(o -> o.id).toList();
        Map<Long, List<CupOrderDeduction>> map = new LinkedHashMap<>();
        for (CupOrderDeduction d : deductionRepository.findByOrderIdIn(ids)) {
            map.computeIfAbsent(d.orderId, k -> new ArrayList<>()).add(d);
        }
        for (CupOrder o : orders) {
            o.deductions = map.getOrDefault(o.id, new ArrayList<>());
        }
    }
}
