package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.domain.Stock;
import com.example.optimistic_lock_single_transaction_test.domain.UserItem;
import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("1) 정상")
class Case1SuccessTest extends PurchaseTestSupport {

	@Test
	@DisplayName("재고 차감과 보유수량 증가가 함께 커밋된다")
	void purchaseSuccess() {
		purchaseService.purchase(userItemId, stockId, 5);

		Stock stock = findStock();
		UserItem userItem = findUserItem();
		assertThat(stock.getQuantity()).isEqualTo(95);
		assertThat(stock.getVersion()).isEqualTo(1L);
		assertThat(userItem.getQuantity()).isEqualTo(5);
		assertThat(userItem.getVersion()).isEqualTo(1L);
	}
}
