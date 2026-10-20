package com.example.optimistic_lock_single_transaction_test.repository;

import com.example.optimistic_lock_single_transaction_test.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
}
