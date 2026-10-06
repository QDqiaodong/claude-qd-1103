package com.milktea.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.milktea.bar.common.BizException;
import com.milktea.bar.dto.CupOrderCreateRequest;
import com.milktea.bar.dto.CupOrderCreateRequest.CupOrderItemInput;
import com.milktea.bar.dto.CupOrderPreviewResponse;
import com.milktea.bar.entity.CupOrder;
import com.milktea.bar.entity.CupOrderDeduction;
import com.milktea.bar.entity.Ingredient;
import com.milktea.bar.entity.Member;
import com.milktea.bar.entity.Recipe;
import com.milktea.bar.repository.CupOrderDeductionRepository;
import com.milktea.bar.repository.CupOrderRepository;
import com.milktea.bar.repository.IngredientRepository;
import com.milktea.bar.repository.MemberRepository;
import com.milktea.bar.repository.RecipeRepository;
import com.milktea.bar.service.CupOrderService;

@SpringBootTest
class CupOrderFulfillmentTests {

    @Autowired CupOrderService orderService;
    @Autowired IngredientRepository ingredientRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired CupOrderRepository cupOrderRepository;
    @Autowired CupOrderDeductionRepository deductionRepository;

    private CupOrderCreateRequest req(Long memberId, Long recipeId, int qty) {
        CupOrderCreateRequest r = new CupOrderCreateRequest();
        r.memberId = memberId;
        r.payType = "微信";
        CupOrderItemInput it = new CupOrderItemInput();
        it.recipeId = recipeId;
        it.qty = qty;
        r.items = List.of(it);
        return r;
    }

    private CupOrderCreateRequest req(Long memberId, List<CupOrderItemInput> items) {
        CupOrderCreateRequest r = new CupOrderCreateRequest();
        r.memberId = memberId;
        r.payType = "现金";
        r.items = items;
        return r;
    }

    private CupOrderItemInput item(long recipeId, int qty) {
        CupOrderItemInput it = new CupOrderItemInput();
        it.recipeId = recipeId;
        it.qty = qty;
        return it;
    }

    private Ingredient ing(String name) {
        return ingredientRepository.findByName(name).orElseThrow();
    }

    // 1. 正常出杯：合并重复明细、合并原料需求、扣库存、加积分、落快照
    @Test
    void create_success_mergesItemsAndDeductsStockWithSnapshot() {
        int teaBefore = ing("红茶").stock;
        int pearlBefore = ing("珍珠").stock;
        int milkBefore = ing("鲜牛奶").stock;
        int pointsBefore = memberRepository.findById(1L).orElseThrow().points;

        // 配方#1 出现两条明细（2 杯 + 1 杯），必须合并成 3 杯
        CupOrderCreateRequest r = req(1L, List.of(item(1L, 2), item(1L, 1)));
        CupOrder order = orderService.create(r);

        assertThat(order.status).isEqualTo("已出杯");
        assertThat(order.stockResult).isEqualTo("已扣减");
        assertThat(order.total.intValue()).isEqualTo(36);          // 12 × 3
        assertThat(order.pointsEarned).isEqualTo(36);
        assertThat(order.items).hasSize(1);                        // 重复明细已合并
        assertThat(order.items.get(0).qty).isEqualTo(3);

        // 茶底每杯 1g；珍珠每份配方 2 份 × 3 杯 = 6；鲜牛奶 1 × 3
        assertThat(ing("红茶").stock).isEqualTo(teaBefore - 3);
        assertThat(ing("珍珠").stock).isEqualTo(pearlBefore - 6);
        assertThat(ing("鲜牛奶").stock).isEqualTo(milkBefore - 3);
        assertThat(memberRepository.findById(1L).orElseThrow().points)
            .isEqualTo(pointsBefore + 36);

        List<CupOrderDeduction> ds = deductionRepository.findByOrderId(order.id);
        assertThat(ds).hasSize(3);
        assertThat(ds).allSatisfy(d -> assertThat(d.restored).isZero());
        assertThat(ds).anySatisfy(d -> {
            assertThat(d.ingredientName).isEqualTo("珍珠");
            assertThat(d.qty).isEqualTo(6);
            assertThat(d.unit).isEqualTo("份");
        });
    }

    // 2. 库存不足：整单不落库、库存不变、积分不加
    @Test
    void create_insufficientStock_rollsBackEverything() {
        long orderCountBefore = cupOrderRepository.count();
        int teaBefore = ing("红茶").stock;
        int pearlBefore = ing("珍珠").stock;
        int milkBefore = ing("鲜牛奶").stock;
        int pointsBefore = memberRepository.findById(1L).orElseThrow().points;

        // 配方#1 每杯 2 珍珠；要 100 杯，珍珠需 200 > 100
        assertThatThrownBy(() -> orderService.create(req(1L, 1L, 100)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("库存不足");

        assertThat(cupOrderRepository.count()).isEqualTo(orderCountBefore);
        assertThat(ing("红茶").stock).isEqualTo(teaBefore);
        assertThat(ing("珍珠").stock).isEqualTo(pearlBefore);
        assertThat(ing("鲜牛奶").stock).isEqualTo(milkBefore);
        assertThat(memberRepository.findById(1L).orElseThrow().points).isEqualTo(pointsBefore);
    }

    // 3. 停用原料：配方引用停用小料时整单拒绝
    @Test
    void create_disabledIngredient_rejected() {
        // 新建一个引用停用原料「布丁」的配方
        Recipe recipe = new Recipe();
        recipe.name = "布丁奶茶_测试";
        recipe.baseTea = "红茶";
        recipe.price = new java.math.BigDecimal("13.00");
        recipe.toppings = new java.util.HashMap<>();
        recipe.toppings.put("布丁", 1);
        recipe.createdAt = java.time.LocalDateTime.now();
        final Long disabledRecipeId = recipeRepository.save(recipe).id;

        CupOrderPreviewResponse preview = orderService.preview(req(null, disabledRecipeId, 1));
        assertThat(preview.blocked).isTrue();
        assertThat(preview.problems.toString()).contains("停用");

        int puddingBefore = ing("布丁").stock;
        int teaBefore = ing("红茶").stock;
        assertThatThrownBy(() -> orderService.create(req(null, disabledRecipeId, 1)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("停用");
        assertThat(ing("布丁").stock).isEqualTo(puddingBefore);
        assertThat(ing("红茶").stock).isEqualTo(teaBefore);
    }

    // 4. 配方引用失效 / 原料引用失效
    @Test
    void create_staleReference_rejected() {
        assertThatThrownBy(() -> orderService.create(req(null, 9999L, 1)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("配方引用失效");

        // 配方茶底指向一个不存在的原料
        Recipe recipe = new Recipe();
        recipe.name = "幽灵茶底_测试";
        recipe.baseTea = "不存在的茶";
        recipe.price = new java.math.BigDecimal("10.00");
        recipe.toppings = new java.util.HashMap<>();
        recipe.toppings.put("珍珠", 1);
        recipe.createdAt = java.time.LocalDateTime.now();
        final Long ghostRecipeId = recipeRepository.save(recipe).id;

        assertThatThrownBy(() -> orderService.create(req(null, ghostRecipeId, 1)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("引用失效");
    }

    // 5. 并发出杯争用同一原料：只能一张成功，无负库存
    @Test
    void concurrentOrders_contendedIngredient_onlyOneSucceeds() throws Exception {
        int pearlBefore = ing("珍珠").stock;   // 100
        int teaBefore = ing("红茶").stock;     // 50
        // 配方#4（双倍珍珠）：红茶1/杯，珍珠3/杯，鲜牛奶1/杯；每单 40 杯 → 珍珠需 120 > 库存
        // 两单各 20 杯 → 珍珠各需 60，库存 100 只够一单
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    orderService.create(req(null, 4L, 20));
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(fail.get()).isEqualTo(1);
        // 成功的一单：珍珠 -60，红茶 -20，绝不出现负库存
        final int pearlExpect = pearlBefore - 60;
        final int teaExpect = teaBefore - 20;
        assertThat(ing("珍珠").stock).isEqualTo(pearlExpect);
        assertThat(ing("红茶").stock).isEqualTo(teaExpect);
        assertThat(ing("珍珠").stock).isGreaterThanOrEqualTo(0);

        // 只有一张新订单落库
        List<CupOrder> newOrders = cupOrderRepository.findAll().stream()
            .filter(o -> "已出杯".equals(o.status) && o.items.stream()
                .anyMatch(it -> it.recipeId == 4L && it.qty == 20))
            .toList();
        assertThat(newOrders).hasSize(1);
    }

    // 6. 退款：按快照精确回补、回退原积分、只允许一次；之后改配方不影响
    @Test
    void refund_restoresBySnapshotAndRollsBackPointsOnce() {
        CupOrder order = orderService.create(req(2L, List.of(item(3L, 2))));
        // 配方#3：乌龙茶1/杯，红豆2/杯，黑糖糖浆1/杯 × 2 → 乌龙2、红豆4、黑糖2
        int teaAfter = ing("乌龙茶").stock;
        int beanAfter = ing("红豆").stock;
        int syrupAfter = ing("黑糖糖浆").stock;
        int pointsAfterSale = memberRepository.findById(2L).orElseThrow().points;
        assertThat(order.pointsEarned).isEqualTo(26); // 13×2

        // 下单后把配方改掉（茶底换掉、小料改掉），历史快照不得受影响
        Recipe changed = recipeRepository.findById(3L).orElseThrow();
        changed.baseTea = "绿茶";
        changed.toppings.clear();
        changed.toppings.put("椰果", 5);
        recipeRepository.save(changed);

        CupOrder refunded = orderService.refund(order.id);
        assertThat(refunded.status).isEqualTo("已退款");
        assertThat(refunded.restoreResult).isEqualTo("已回补");
        // 仍按快照回补的是乌龙茶/红豆/黑糖糖浆，而不是绿茶/椰果
        assertThat(ing("乌龙茶").stock).isEqualTo(teaAfter + 2);
        assertThat(ing("红豆").stock).isEqualTo(beanAfter + 4);
        assertThat(ing("黑糖糖浆").stock).isEqualTo(syrupAfter + 2);
        assertThat(memberRepository.findById(2L).orElseThrow().points)
            .isEqualTo(pointsAfterSale - 26);
        assertThat(deductionRepository.findByOrderId(order.id))
            .allSatisfy(d -> assertThat(d.restored).isEqualTo(1));

        // 重复退款：稳定失败，不再回补、不再退积分
        assertThatThrownBy(() -> orderService.refund(order.id))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("已退款");
        assertThat(ing("乌龙茶").stock).isEqualTo(teaAfter + 2);
        assertThat(memberRepository.findById(2L).orElseThrow().points)
            .isEqualTo(pointsAfterSale - 26);
    }

    // 7. 并发重复退款：只有一个事务真正回补
    @Test
    void concurrentRefund_restoresExactlyOnce() throws Exception {
        CupOrder order = orderService.create(req(null, 2L, 1));
        int teaAfter = ing("绿茶").stock;
        int jellyAfter = ing("椰果").stock;

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    orderService.refund(order.id);
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(fail.get()).isEqualTo(3);
        // 仅回补一次：绿茶1、椰果2
        assertThat(ing("绿茶").stock).isEqualTo(teaAfter + 1);
        assertThat(ing("椰果").stock).isEqualTo(jellyAfter + 2);
    }

    // 8. 历史旧单（无快照）：可退款、回退积分，但不凭空回补库存
    @Test
    void refundLegacyOrder_noSnapshot_pointsRollbackWithoutStock() {
        int teaBefore = ing("红茶").stock;
        int pearlBefore = ing("珍珠").stock;
        int pointsBefore = memberRepository.findById(1L).orElseThrow().points;

        CupOrder refunded = orderService.refund(1L);
        assertThat(refunded.status).isEqualTo("已退款");
        assertThat(refunded.stockResult).isEqualTo("未扣减");
        assertThat(refunded.restoreResult).isEqualTo("未回补");
        assertThat(refunded.remark).contains("无原料扣减快照");
        // 库存不动
        assertThat(ing("红茶").stock).isEqualTo(teaBefore);
        assertThat(ing("珍珠").stock).isEqualTo(pearlBefore);
        // 积分仍按原值回退
        Member m = memberRepository.findById(1L).orElseThrow();
        assertThat(m.points).isEqualTo(pointsBefore - 24);

        // 旧单同样不能重复退款
        assertThatThrownBy(() -> orderService.refund(1L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("已退款");
    }

    // 9. 预览：多配方合并需求、金额与积分、不足提示
    @Test
    void preview_mergesNeedsAndReportsShortage() {
        // 配方#1（红茶/珍珠2/奶1）×2 + 配方#4（红茶/珍珠3/奶1）×1
        CupOrderPreviewResponse p = orderService.preview(req(1L, List.of(item(1L, 2), item(4L, 1))));
        assertThat(p.blocked).isFalse();
        assertThat(p.total.intValue()).isEqualTo(24 + 15);
        assertThat(p.pointsEarned).isEqualTo(39);
        assertThat(p.lines).filteredOn(l -> "红茶".equals(l.ingredientName))
            .singleElement().satisfies(l -> {
                assertThat(l.need).isEqualTo(3);
                assertThat(l.available).isTrue();
            });
        assertThat(p.lines).filteredOn(l -> "珍珠".equals(l.ingredientName))
            .singleElement().satisfies(l -> assertThat(l.need).isEqualTo(7)); // 2×2+3
        assertThat(p.lines).filteredOn(l -> "鲜牛奶".equals(l.ingredientName))
            .singleElement().satisfies(l -> assertThat(l.need).isEqualTo(3));

        CupOrderPreviewResponse bad = orderService.preview(req(null, 1L, 1000));
        assertThat(bad.blocked).isTrue();
        assertThat(bad.problems.toString()).contains("库存不足");
    }
}
