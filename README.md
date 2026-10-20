# optimistic-lock-single-transaction-test

단일 트랜잭션 안에서 JPA 낙관적 락(Optimistic Lock, `@Version`)이 어떻게 동작하는지 확인하기 위한 테스트 프로젝트입니다.

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

# 애플리케이션 실행
./gradlew bootRun
```

## 참고 문서

- [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
- [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
- [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
