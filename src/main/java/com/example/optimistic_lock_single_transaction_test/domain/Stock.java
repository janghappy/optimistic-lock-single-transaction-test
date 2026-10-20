package com.example.optimistic_lock_single_transaction_test.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private int quantity;

	// 낙관적 락: UPDATE ... WHERE id = ? AND version = ? 로 충돌을 감지한다
	@Version
	private Long version;

	public Stock(int quantity) {
		this.quantity = quantity;
	}

	public void decrease(int amount) {
		if (quantity < amount) {
			throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + quantity);
		}
		quantity -= amount;
	}
}
