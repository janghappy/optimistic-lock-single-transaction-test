# 낙관적 락 예외 정리

[README](../README.md) 의 Case2, Case3 처럼 `@Version` 충돌이 났을 때 어떤 예외가 던져지는지 정리한다.
아래 내용은 모두 이 프로젝트(Spring Boot 4.1.1 / Spring 7.0.9 / Hibernate 7)에서 실제로 실행해 확인했다.

## 1. 예외는 어디서 시작되는가

커밋(flush) 시 Hibernate 가 `UPDATE ... WHERE id = ? AND version = ?` 를 실행하고, 결과가 0건이면 예외를 던진다.

| 예외 | 출처 | 의미 |
|---|---|---|
| `org.hibernate.StaleStateException` | Hibernate | UPDATE 결과가 1건이어야 하는데 0건 (가장 안쪽 원인) |
| `org.hibernate.StaleObjectStateException` | Hibernate | 위 예외에 어떤 엔티티의 어떤 id 인지 정보를 더한 것 |

## 2. Spring 에서 받는 예외

`@Transactional` 커밋 중 예외가 나면 `JpaTransactionManager` 가 잡아서 `HibernateExceptionTranslator` 로 Spring 예외로 바꾼다.

```
ObjectOptimisticLockingFailureException      (org.springframework.orm)  ← 호출한 쪽이 받는 예외
 └─ cause: StaleObjectStateException         (Hibernate)
     └─ cause: StaleStateException           (Hibernate)
```

JPA 표준 예외(`jakarta.persistence.OptimisticLockException`)는 이 원인 체인에 없다.

### Spring 예외 계층

```
RuntimeException
 └─ DataAccessException                       (org.springframework.dao)
     └─ TransientDataAccessException          "다시 시도하면 성공할 수 있는 오류"
         └─ ConcurrencyFailureException
             └─ OptimisticLockingFailureException
                 ├─ ObjectOptimisticLockingFailureException   Hibernate 예외를 변환한 경우 (이 프로젝트)
                 └─ JpaOptimisticLockingFailureException      JPA 표준 OptimisticLockException 을 변환한 경우
```

### Spring 이 감싸는 이유

- **기술에 상관없이 같은 예외로 처리하기 위해서다.** Hibernate, JPA 표준, JDBC 중 어디서 났든 호출하는 쪽은 `DataAccessException` 계층만 알면 된다.
- `TransientDataAccessException` 계층에 속한다는 것은 **재시도하면 성공할 수 있다**는 뜻이다. 낙관적 락 충돌의 일반적인 처리가 재시도인 것과 맞는다.

## 3. Spring 없이 실행하면

Case3 과 같은 상황(재고 −5, 보유수량 +5, 커밋 전에 `user_item` 의 version 이 먼저 올라감)을 Spring 트랜잭션 없이 실행한 결과다.

| 방식 | 받는 예외 | 원인 체인 |
|---|---|---|
| JPA `EntityTransaction.commit()` | `jakarta.persistence.RollbackException` | → `OptimisticLockException` → `StaleObjectStateException` → `StaleStateException` |
| JPA `EntityManager.flush()` | `jakarta.persistence.OptimisticLockException` | → `StaleObjectStateException` → `StaleStateException` |
| Hibernate `Session` 트랜잭션 `commit()` | `jakarta.persistence.RollbackException` | commit 과 같음 |
| **Spring `@Transactional`** | `ObjectOptimisticLockingFailureException` | → `StaleObjectStateException` → `StaleStateException` |

- 모든 방식에서 결과는 **재고 100, 보유수량 0** 이다. 전체 롤백은 Spring 이 아니라 DB 트랜잭션이 보장하는 동작이고, 달라지는 것은 예외의 겉모양뿐이다.
- 순수 JPA 에서는 commit 중 충돌이면 `RollbackException` 으로 한 번 더 감싸지고, flush 중 충돌이면 `OptimisticLockException` 이 바로 나온다. 그래서 원인 체인을 직접 확인해야 한다.
  ```java
  try {
      tx.commit();
  } catch (RollbackException e) {
      if (e.getCause() instanceof OptimisticLockException) {
          // 낙관적 락 충돌 → 재시도 등
      }
  }
  ```
- Hibernate `Session` 으로 commit 해도 `RollbackException` 이 나온 것은, 이 프로젝트가 Spring Boot 에서 JPA 방식으로 시작됐기 때문이다. Hibernate 를 JPA 없이 네이티브 방식으로 시작한 경우는 확인하지 않았다.

## 4. 어떤 타입으로 잡아야 하나

Spring 환경에서는 **`org.springframework.dao.OptimisticLockingFailureException`** 으로 잡는다.

```java
try {
    purchaseService.purchase(userItemId, stockId, 5);
} catch (OptimisticLockingFailureException e) {
    // 재시도 또는 "다른 요청과 충돌했습니다. 다시 시도해 주세요" 응답
}
```

| 타입 | 평가 |
|---|---|
| `jakarta.persistence.OptimisticLockException` | Spring 환경에서는 던져지지 않는다 (원인 체인에도 없음) |
| `ObjectOptimisticLockingFailureException` | 동작하지만 Hibernate 경로에서 변환된 구체 타입에 묶인다 |
| **`OptimisticLockingFailureException`** | 변환 경로(Hibernate / JPA 표준)가 달라져도 함께 잡힌다 |

이 프로젝트의 테스트(Case2, Case3)도 같은 이유로 `OptimisticLockingFailureException` 으로 검증한다.
어느 엔티티에서 충돌했는지 로그로 남기려면 `ObjectOptimisticLockingFailureException` 의 `getPersistentClassName()`, `getIdentifier()` 를 쓸 수 있다.
