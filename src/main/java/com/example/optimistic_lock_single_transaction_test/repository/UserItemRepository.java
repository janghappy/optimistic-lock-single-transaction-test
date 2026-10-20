package com.example.optimistic_lock_single_transaction_test.repository;

import com.example.optimistic_lock_single_transaction_test.domain.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {
}
