package com.milktea.bar.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.milktea.bar.entity.Ingredient;

import jakarta.persistence.LockModeType;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Ingredient> findByCategoryAndStatus(String category, String status);

    Optional<Ingredient> findByName(String name);

    List<Ingredient> findAllByNameIn(Collection<String> names);

    /**
     * 按下单原料 id 集合加写锁并按 id 升序返回。
     * 并发出杯争用同一原料时在此排队，锁内再做一次库存/停用校验，
     * 保证不出现负库存、半扣库存；固定加锁顺序避免交叉等待死锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id in :ids order by i.id asc")
    List<Ingredient> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    /**
     * 出杯下单按原料名直接加写锁并按 id 升序返回。
     * 必须是事务内对这些原料行的第一次读取——若一级缓存里已有旧实体，
     * Hibernate 会直接返回缓存而不真正执行 FOR UPDATE，并发扣减将失效。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.name in :names order by i.id asc")
    List<Ingredient> findAllByNameForUpdate(@Param("names") Collection<String> names);
}
