package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("3) 보유수량 증가 단계에서 충돌")
class Case3UserItemConflictTest extends PurchaseTestSupport {

	@Test
	@DisplayName("먼저 성공한 재고 UPDATE 까지 전부 롤백된다")
	void rollbackAllWhenUserItemConflicts() {
		assertThatThrownBy(() -> inTransaction(() -> {
			// 재고 100 → 95, 보유수량 0 → 5 (둘 다 version 0 기준). 아직 커밋 전
			purchaseService.purchase(userItemId, stockId, 5);

			// 그 사이 다른 트랜잭션이 보유수량을 먼저 수정·커밋 (user_item version 0 → 1)
			otherTransactionModifies("user_item", userItemId);

			// 커밋: UPDATE stock 1건 성공 → UPDATE user_item ... WHERE version = 0 → 0건 → 예외
		})).isInstanceOf(OptimisticLockingFailureException.class);

		// 이미 성공한 재고 UPDATE 도 롤백: 95 가 아니라 100
		assertThat(findStock().getQuantity()).isEqualTo(INITIAL_STOCK);
		assertThat(findUserItem().getQuantity()).isZero();
	}
}
