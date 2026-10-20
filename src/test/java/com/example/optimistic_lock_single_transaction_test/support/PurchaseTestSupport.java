package com.example.optimistic_lock_single_transaction_test.support;

import com.example.optimistic_lock_single_transaction_test.domain.Stock;
import com.example.optimistic_lock_single_transaction_test.domain.UserItem;
import com.example.optimistic_lock_single_transaction_test.repository.StockRepository;
import com.example.optimistic_lock_single_transaction_test.repository.UserItemRepository;
import com.example.optimistic_lock_single_transaction_test.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * 구매 테스트 공통 설정. 초기 상태: 재고 100, 보유수량 0, version 은 둘 다 0
 * 테스트 클래스에 @Transactional 을 붙이면 커밋이 일어나지 않아 version 충돌을 볼 수 없으므로 붙이지 않는다
 */
@SpringBootTest
public abstract class PurchaseTestSupport {

	protected static final int INITIAL_STOCK = 100;
	protected static final Long USER_ID = 1L;

	@Autowired
	protected PurchaseService purchaseService;

	@Autowired
	protected StockRepository stockRepository;

	@Autowired
	protected UserItemRepository userItemRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	protected Long stockId;
	protected Long userItemId;

	@BeforeEach
	protected void setUp() {
		userItemRepository.deleteAll();
		stockRepository.deleteAll();
		stockId = stockRepository.save(new Stock(INITIAL_STOCK)).getId();
		userItemId = userItemRepository.save(new UserItem(USER_ID, 0)).getId();
	}

	/**
	 * action 을 하나의 트랜잭션으로 묶어 실행하고, action 이 끝난 뒤에 커밋한다.
	 * action 안에서 호출한 서비스의 @Transactional 은 이 트랜잭션에 참여하므로 서비스가 끝나도 바로 커밋되지 않는다.
	 */
	protected void inTransaction(Runnable action) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
	}

	/**
	 * 다른 트랜잭션이 해당 행을 먼저 수정·커밋한 상황을 만든다. 수정 내용은 무관하고 version 증가만 중요하다.
	 * 별도 스레드에서 실행하므로 현재 트랜잭션과 다른 커넥션을 쓰고 즉시 커밋된다.
	 */
	protected void otherTransactionModifies(String table, Long id) {
		CompletableFuture.runAsync(() ->
				jdbcTemplate.update("update " + table + " set version = version + 1 where id = ?", id)
		).join();
	}

	protected Stock findStock() {
		return stockRepository.findById(stockId).orElseThrow();
	}

	protected UserItem findUserItem() {
		return userItemRepository.findById(userItemId).orElseThrow();
	}
}
