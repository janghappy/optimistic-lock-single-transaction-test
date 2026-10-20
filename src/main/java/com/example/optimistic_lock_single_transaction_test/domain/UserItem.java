package com.example.optimistic_lock_single_transaction_test.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자 보유수량
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private int quantity;

	// 낙관적 락: UPDATE ... WHERE id = ? AND version = ? 로 충돌을 감지한다
	@Version
	private Long version;

	public UserItem(Long userId, int quantity) {
		this.userId = userId;
		this.quantity = quantity;
	}

	public void increase(int amount) {
		quantity += amount;
	}
}
