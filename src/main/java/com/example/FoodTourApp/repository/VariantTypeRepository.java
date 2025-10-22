package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.VariantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantTypeRepository extends JpaRepository<VariantType, Integer> {
    List<VariantType> findByIsActiveTrue();
    Optional<VariantType> findByNameAndIsActiveTrue(String name);
    boolean existsByName(String name);
}

