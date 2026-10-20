# 단일 트랜잭션에서 JPA 낙관적 락 충돌 시 롤백 동작 확인

단일 트랜잭션 안에서 JPA 낙관적 락(Optimistic Lock, `@Version`)이 어떻게 동작하는지 확인하기 위한 테스트 프로젝트입니다.

## 테스트 목적

### 의문
낙관적 락(`@Version`) 충돌은 `UPDATE ... WHERE version = ?` 의 **결과 건수가 0건**인 것으로 알 수 있다. 결과 건수는 쿼리를 실행한 뒤에야 알 수 있으니, 충돌을 아는 시점은 **COMMIT 이후**가 아닐까?

그렇다면 하나의 트랜잭션에서 두 엔티티(재고, 사용자 보유수량)를 차례로 UPDATE 할 때, 뒤쪽(보유수량)에서 충돌이 나도 **앞쪽(재고) UPDATE 는 이미 커밋되어 되돌릴 수 없는 것** 아닌가?

### 결론
아니다. **UPDATE 결과 건수는 DB COMMIT 전에 반환된다.** (JDBC `executeUpdate()` 는 문장을 실행하자마자 건수를 돌려준다)

Spring 의 "커밋 처리"는 **① flush (UPDATE 실행, 건수 확인) → ② DB COMMIT** 순서이고, ①에서 0건이 나오면 ②로 가지 않고 ROLLBACK 한다. 그래서 앞쪽 UPDATE 도 함께 롤백된다.

### 커밋 처리 타임라인 (Case3)
```
purchase() 종료                                  (재고 100 → 95, 보유수량 0 → 5 는 아직 메모리의 엔티티에만 반영)
  │
  ▼ Spring: 커밋 처리 시작 (JpaTransactionManager.commit)
  │
  ├─ ① Hibernate flush
  │     UPDATE stock     ... WHERE version = 0  → 1건   DB 에 반영됐지만 미커밋
  │     UPDATE user_item ... WHERE version = 0  → 0건   ← 여기서 충돌을 안다
  │
  ├─ 0건이므로 StaleStateException
  │
  └─ ② DB COMMIT 대신 ROLLBACK                    ← DB COMMIT 은 한 번도 실행되지 않는다
```

### 근거
- **Case3**: 결과가 재고 100 (95 가 아님), 보유수량 0 이다.
- **Case6**: 트랜잭션 안에서 UPDATE 를 실행하면 결과 건수(1건 / version 불일치 0건)를 바로 받는다. 그 시점에 다른 커넥션에서는 아직 변경 전 값(100)이 보인다. 즉 **건수는 COMMIT 전에 받는다.**
  그래서 Hibernate 도 flush 에서 0건을 보고, COMMIT 하기 전에 충돌을 판단할 수 있다.

## 단계별 구성

### 1) 기본 환경 세팅
- Spring Boot + Data JPA + H2(인메모리) 프로젝트
- `application.yaml`: SQL 로그(`show-sql`)와 `JpaTransactionManager` DEBUG 로그를 켜서 트랜잭션 흐름을 확인

### 2) 기본 서비스 환경 세팅 (엔티티 / 리포지토리)
| 엔티티 | 필드 | 구매 시 동작 |
|---|---|---|
| `Stock` (재고) | `id`, `quantity`, `@Version version` | `decrease(n)` |
| `UserItem` (사용자 보유수량) | `id`, `userId`, `quantity`, `@Version version` | `increase(n)` |

- `StockRepository`, `UserItemRepository` : `JpaRepository` 상속
- 구매는 재고에서 보유수량으로 수량을 옮기는 것이므로 **재고 + 보유수량 = 초기 재고** 가 항상 성립해야 한다.

### 3) 서비스 코드 — `PurchaseService.purchase()`
```java
@Transactional
public void purchase(Long userItemId, Long stockId, int quantity) {
    Stock stock = stockRepository.findById(stockId).orElseThrow();
    stock.decrease(quantity);       // 1. 재고 차감

    UserItem userItem = userItemRepository.findById(userItemId).orElseThrow();
    userItem.increase(quantity);    // 2. 보유수량 증가
}
```
- 두 UPDATE 모두 커밋 처리 중 flush 시점(DB COMMIT 전)에 `WHERE id = ? AND version = ?` 조건으로 실행된다.
- flush 시 UPDATE 는 조회한 순서대로 실행된다: `stock` → `user_item`

#### 재시도 — `RetryablePurchaseService.purchase()`
```java
public void purchase(Long userItemId, Long stockId, int quantity) {   // @Transactional 없음
    for (int attempt = 1; ; attempt++) {
        try {
            purchaseService.purchase(userItemId, stockId, quantity);  // 매번 새 트랜잭션
            return;
        } catch (OptimisticLockingFailureException e) {
            if (attempt >= MAX_ATTEMPTS) throw e;                     // 최대 10회
        }
    }
}
```
- 재시도는 **트랜잭션 바깥**에서 해야 한다. 재시도마다 새 트랜잭션에서 최신 version 을 다시 읽어야 하기 때문이다. 같은 트랜잭션 안에서 재시도하면 이미 읽은 version 으로 다시 UPDATE 해서 계속 실패한다.
- 그래서 `PurchaseService` 를 고치지 않고, `@Transactional` 이 없는 별도 빈이 감싸서 호출한다. 같은 클래스 안에서 호출하면 프록시를 거치지 않아 `@Transactional` 이 적용되지 않는다.

### 4) 테스트 코드
초기 상태: 재고 100, 보유수량 0, version 은 둘 다 0 (공통 설정: `support.PurchaseTestSupport`)

| 순서 | 테스트 클래스 | 테스트 | 상황 |
|---|---|---|---|
| 1 | `Case1SuccessTest` | 정상 | 5개 구매 |
| 2 | `Case2StockConflictTest` | 재고 차감 단계에서 충돌 | 5개 구매, 커밋 전에 다른 트랜잭션이 **재고** 행을 먼저 수정·커밋 |
| 3 | `Case3UserItemConflictTest` | 보유수량 증가 단계에서 충돌 | 5개 구매, 커밋 전에 다른 트랜잭션이 **보유수량** 행을 먼저 수정·커밋 |
| 4 | `Case4ConcurrencyTest` | 동시성 (재시도 없음) | 10개 스레드가 동시에 1개씩 구매. ① 최종 정합성 ② 처리 건수 확인 |
| 5 | `Case5RetryConcurrencyTest` | 동시성 (재시도) | Case4 와 같은 상황을 `RetryablePurchaseService` 로 실행 |
| 6 | `Case6ConflictTimingTest` | 충돌 감지 시점 | 트랜잭션 안에서 받은 UPDATE 결과 건수와, 그 시점에 다른 커넥션에서 보이는 값 비교 |

- 테스트는 **Case1 → Case2 → Case3 → Case4 → Case5 → Case6** 순서로 실행된다. 서비스 로직 순서(재고 차감 → 보유수량 증가)대로 재고 충돌(Case2)을 먼저 확인한다.
  - 클래스명 앞의 `CaseN` 과 `@DisplayName("N) ...")` 으로 순서를 표시
  - `junit-platform.properties` 의 `ClassOrderer$DisplayName` 설정으로 실제 실행 순서도 번호 순서를 따른다

#### version 불일치 재현 방법 (Case2, Case3)
```java
assertThatThrownBy(() -> inTransaction(() -> {
    purchaseService.purchase(userItemId, stockId, 5);    // 재고 100 → 95, 보유수량 0 → 5. 아직 커밋 전 (version 0 기준)
    otherTransactionModifies("user_item", userItemId);   // 다른 트랜잭션이 먼저 수정·커밋 → DB version 0 → 1
})).isInstanceOf(OptimisticLockingFailureException.class);  // 커밋 → UPDATE ... WHERE version = 0 → 0건
```
| 헬퍼 (`PurchaseTestSupport`) | 역할 |
|---|---|
| `inTransaction(action)` | action 을 하나의 트랜잭션으로 묶고 action 이 끝난 뒤 커밋한다. 서비스의 `@Transactional` 이 여기에 참여하므로 `purchase()` 가 끝나도 바로 커밋되지 않아, 커밋 전에 끼어들 틈이 생긴다. |
| `otherTransactionModifies(table, id)` | 별도 스레드에서 `update {table} set version = version + 1 where id = ?` 를 실행한다. 다른 커넥션이라 즉시 커밋된다. |

- 끼어드는 트랜잭션은 테스트 대상이 아니라 충돌을 만드는 장치이므로 **version 만 올린다.** 데이터는 바꾸지 않기 때문에, 구매 트랜잭션이 롤백됐다면 결과가 초기 상태(재고 100, 보유수량 0)와 같아야 한다.
- 구매 트랜잭션의 UPDATE 는 커밋 처리 중 flush 시점에야 실행되므로, 끼어드는 UPDATE 가 행 잠금에 막히지 않는다.
- 테스트 클래스에 `@Transactional` 을 붙이면 커밋이 일어나지 않아 version 충돌이 재현되지 않으므로 붙이지 않았다.

#### UPDATE 결과 건수 확인 방법 (Case6)
```java
inTransaction(() -> {
    int matched   = jdbcTemplate.update("update stock set quantity = 95 where id = ? and version = 0", stockId);    // 1
    int unmatched = jdbcTemplate.update("update user_item set quantity = 5 where id = ? and version = 99", userItemId); // 0
    quantitySeenByOtherConnection("stock", stockId);   // 100 ← 아직 커밋 전
});
quantitySeenByOtherConnection("stock", stockId);       // 95  ← 커밋 후
```
- 트랜잭션 안의 `jdbcTemplate` 은 현재 트랜잭션과 같은 커넥션을 쓴다.
- `quantitySeenByOtherConnection()` 은 별도 스레드(다른 커넥션)에서 조회하므로 커밋된 값만 보인다.

### 5) 예상 결과 및 동작 확인

**결론: 어느 쪽에서 충돌이 나든 트랜잭션 전체가 롤백된다. 일부만 반영되는 경우는 없다.**

| 순서 | 커밋 처리 중(flush) 실행된 SQL | 결과 재고 | 결과 보유수량 | 예외 |
|---|---|---|---|---|
| 1 | `update stock` 1건 → `update user_item` 1건 | 95 | 5 | 없음 |
| 2 | `update stock` **0건** → `user_item` 은 실행 안 됨 | 100 (롤백) | 0 (롤백) | `OptimisticLockingFailureException` |
| 3 | `update stock` **1건 성공** → `update user_item` **0건** | **100 (95 가 아니라 롤백)** | 0 (롤백) | `OptimisticLockingFailureException` |
| 4 | 실행 예시: 성공 1~3, 충돌 7~9 (실행할 때마다 다름) | 97~99 | 1~3 | 충돌한 요청만 |
| 5 | 충돌한 요청을 재시도 → 10건 모두 성공 | 90 | 10 | 없음 (재시도 안에서 처리) |
| 6 | UPDATE 결과 1건 / 0건을 COMMIT 전에 받음 | 커밋 전 100, 커밋 후 95 | - | 없음 |

> 실제로 던져지는 타입은 하위 타입인 `ObjectOptimisticLockingFailureException` 이다. 테스트는 상위 타입인 `OptimisticLockingFailureException` 으로 검증한다.
>
> 예외가 어떻게 감싸지는지, Spring 없이 순수 JPA 로 실행하면 어떤 예외가 나는지는 [낙관적 락 예외 정리](docs/optimistic-lock-exception.md) 참고.

Case3 의 실제 로그 (구매 트랜잭션 커밋 부분):
```
Committing JPA transaction ...
update stock     set quantity=?, version=? where id=? and version=?            ← 1건 성공 (100 → 95)
update user_item set quantity=?, user_id=?, version=? where id=? and version=?  ← 0건
Initiating transaction rollback after commit exception
ObjectOptimisticLockingFailureException: Unexpected row count (expected row count 1 but was 0)
  [update user_item ...] for entity [...UserItem]
```

**정리**
- Case2 처럼 첫 UPDATE(`stock`) 에서 실패하면 그 뒤 UPDATE 는 아예 실행되지 않는다.
- **Case3 이 핵심이다.** `stock` UPDATE 는 DB 에서 이미 1건 성공(100 → 95, 미커밋)했지만, 같은 트랜잭션의 `user_item` UPDATE 가 실패하자 `stock` 도 100 으로 롤백됐다.
- **Case6 이 그 이유다.** UPDATE 결과 건수는 DB COMMIT 전에 반환되므로, Hibernate 는 flush 에서 0건을 보고 COMMIT 대신 ROLLBACK 한다.
- Case4 ① **최종 정합성**: 성공/충돌 건수는 매번 달라도 `재고 + 보유수량 = 100` 은 항상 지켜진다.
- Case4 ② **처리 건수**: 요청 10건 중 1~3건만 처리된다. 10개 요청이 대부분 같은 version 을 읽어서 먼저 커밋한 요청만 성공하기 때문이다. 낙관적 락은 정합성은 지키지만, 충돌한 요청을 대신 처리해 주지는 않는다.
- Case5 **재시도**: 충돌한 요청을 새 트랜잭션으로 다시 실행하면 10건 모두 처리된다 (재고 90, 보유수량 10).
- 충돌은 `decrease()`/`increase()` 호출 시점이 아니라 커밋 처리 중 flush 시점(DB COMMIT 전)에 감지되므로 서비스 메서드 안의 `try-catch` 로는 잡을 수 없다. 호출하는 쪽에서 `OptimisticLockingFailureException` 을 받아 재시도 등을 처리해야 한다.

## 기술 스택

- Java 17
- Spring Boot 4.1.1 (Web MVC, Data JPA)
- H2 Database (인메모리, H2 Console 포함)
- Lombok
- Gradle (Kotlin DSL)

## 실행 방법

```bash
# 테스트 실행
./gradlew test
```
