package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("2) 재고 차감 단계에서 충돌")
class Case2StockConflictTest extends PurchaseTestSupport {

	@Test
	@DisplayName("보유수량 UPDATE 는 실행되지 않고 전부 롤백된다")
	void rollbackAllWhenStockConflicts() {
		assertThatThrownBy(() -> inTransaction(() -> {
			// 재고 100 → 95, 보유수량 0 → 5 (둘 다 version 0 기준). 아직 커밋 전
			purchaseService.purchase(userItemId, stockId, 5);

			// 그 사이 다른 트랜잭션이 재고를 먼저 수정·커밋 (stock version 0 → 1)
			otherTransactionModifies("stock", stockId);

			// 커밋: UPDATE stock ... WHERE version = 0 → 0건 → 예외 (user_item UPDATE 는 실행되지 않음)
		})).isInstanceOf(OptimisticLockingFailureException.class);

		// 구매 내용이 하나도 반영되지 않았다
		assertThat(findStock().getQuantity()).isEqualTo(INITIAL_STOCK);
		assertThat(findUserItem().getQuantity()).isZero();
	}
}
