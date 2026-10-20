package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("4) 동시성 (재시도 없음)")
class Case4ConcurrencyTest extends PurchaseTestSupport {

	private static final int REQUEST_COUNT = 10;

	@Test
	@DisplayName("동시에 구매해도 최종 결과 정합성(재고 + 보유수량 = 100)은 지켜진다")
	void totalIsConsistent() throws InterruptedException {
		ConcurrentResult result = runConcurrently(REQUEST_COUNT, () -> purchaseService.purchase(userItemId, stockId, 1));

		assertThat(result.successCount() + result.conflictCount()).isEqualTo(REQUEST_COUNT);
		assertThat(findStock().getQuantity() + findUserItem().getQuantity()).isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("요청 건수만큼 처리되지는 않는다 (충돌한 요청은 롤백되고 끝남)")
	void notAllRequestsProcessed() throws InterruptedException {
		ConcurrentResult result = runConcurrently(REQUEST_COUNT, () -> purchaseService.purchase(userItemId, stockId, 1));

		// 대부분 같은 version 을 읽기 때문에 먼저 커밋한 요청만 성공한다
		assertThat(result.conflictCount()).isPositive();
		assertThat(findUserItem().getQuantity()).isEqualTo(result.successCount()).isLessThan(REQUEST_COUNT);
		assertThat(findStock().getQuantity()).isGreaterThan(INITIAL_STOCK - REQUEST_COUNT);
	}
}
