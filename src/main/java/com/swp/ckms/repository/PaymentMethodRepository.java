package com.swp.ckms.repository;

import com.swp.ckms.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    java.util.Optional<com.swp.ckms.entity.PaymentMethod> findByName(String name);
}
