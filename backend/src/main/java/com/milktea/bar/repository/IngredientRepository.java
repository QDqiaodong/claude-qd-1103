package com.milktea.bar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.milktea.bar.entity.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Ingredient> findByCategoryAndStatus(String category, String status);
}
