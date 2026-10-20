package com.example.optimistic_lock_single_transaction_test.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetryablePurchaseService {

	private static final int MAX_ATTEMPTS = 10;

	private final PurchaseService purchaseService;

	/**
	 * 낙관적 락 충돌 시 구매를 재시도한다.
	 * 재시도마다 새 트랜잭션에서 최신 version 을 다시 읽어야 하므로 이 메서드에는 @Transactional 을 붙이지 않는다.
	 * (같은 트랜잭션 안에서 재시도하면 이미 읽은 version 으로 다시 UPDATE 해서 계속 실패한다)
	 */
	public void purchase(Long userItemId, Long stockId, int quantity) {
		for (int attempt = 1; ; attempt++) {
			try {
				purchaseService.purchase(userItemId, stockId, quantity);
				return;
			} catch (OptimisticLockingFailureException e) {
				if (attempt >= MAX_ATTEMPTS) {
					throw e;
				}
			}
		}
	}
}
