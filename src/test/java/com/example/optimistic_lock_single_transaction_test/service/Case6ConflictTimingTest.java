package com.example.optimistic_lock_single_transaction_test.service;

import com.example.optimistic_lock_single_transaction_test.support.PurchaseTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("6) 충돌 감지 시점")
class Case6ConflictTimingTest extends PurchaseTestSupport {

	@Test
	@DisplayName("UPDATE 결과 건수는 COMMIT 전에 반환된다")
	void updateCountReturnedBeforeCommit() {
		inTransaction(() -> {
			// 트랜잭션 안의 jdbcTemplate 은 현재 트랜잭션과 같은 커넥션을 쓴다
			int matched = jdbcTemplate.update("update stock set quantity = 95 where id = ? and version = 0", stockId);
			int unmatched = jdbcTemplate.update("update user_item set quantity = 5 where id = ? and version = 99", userItemId);

			// 결과 건수를 이미 받았다
			assertThat(matched).isEqualTo(1);
			assertThat(unmatched).isZero(); // version 불일치 = 0건 (낙관적 락 충돌의 판단 근거)

			// 그런데 다른 커넥션에서는 아직 100 → 커밋 전이다
			assertThat(quantitySeenByOtherConnection("stock", stockId)).isEqualTo(INITIAL_STOCK);
		});

		// 커밋 후에야 다른 커넥션에서도 95 로 보인다
		assertThat(quantitySeenByOtherConnection("stock", stockId)).isEqualTo(95);
	}
}
