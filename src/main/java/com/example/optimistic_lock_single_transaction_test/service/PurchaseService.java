package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.domain.Stock;
import com.example.optimistic_lock_single_transaction_test.domain.UserItem;
import com.example.optimistic_lock_single_transaction_test.repository.StockRepository;
import com.example.optimistic_lock_single_transaction_test.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseService {

	private final StockRepository stockRepository;
	private final UserItemRepository userItemRepository;

	/**
	 * 하나의 트랜잭션에서 재고 차감과 사용자 보유수량 증가를 함께 처리한다.
	 * 두 엔티티 모두 @Version 을 가지며, version 충돌은 커밋(flush) 시점에 감지된다.
	 * flush 시 UPDATE 는 조회한 순서대로 실행된다: stock → user_item
	 */
	@Transactional
	public void purchase(Long userItemId, Long stockId, int quantity) {
		// 1. 재고 차감
		Stock stock = stockRepository.findById(stockId).orElseThrow();
		stock.decrease(quantity);

		// 2. 사용자 보유수량 증가
		UserItem userItem = userItemRepository.findById(userItemId).orElseThrow();
		userItem.increase(quantity);
	}
}
