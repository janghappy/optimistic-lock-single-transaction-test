package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("5) 동시성 (재시도)")
class Case5RetryConcurrencyTest extends PurchaseTestSupport {

	private static final int REQUEST_COUNT = 10;

	@Autowired
	private RetryablePurchaseService retryablePurchaseService;

	@Test
	@DisplayName("충돌한 요청을 재시도하면 요청 건수만큼 모두 처리된다")
	void allRequestsProcessedWithRetry() throws InterruptedException {
		ConcurrentResult result = runConcurrently(REQUEST_COUNT, () -> retryablePurchaseService.purchase(userItemId, stockId, 1));

		assertThat(result.successCount()).isEqualTo(REQUEST_COUNT);
		assertThat(result.conflictCount()).isZero();
		assertThat(findStock().getQuantity()).isEqualTo(INITIAL_STOCK - REQUEST_COUNT);
		assertThat(findUserItem().getQuantity()).isEqualTo(REQUEST_COUNT);
	}
}
