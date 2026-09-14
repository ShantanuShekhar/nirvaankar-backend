package com.nirvaankar.marketplace.ordering.repository;

import com.nirvaankar.marketplace.ordering.domain.OrderItemTax;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemTaxRepository extends JpaRepository<OrderItemTax, Long> {

    List<OrderItemTax> findAllByOrderItemId(Long orderItemId);
}
