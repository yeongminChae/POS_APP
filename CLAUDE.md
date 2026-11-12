# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개요

편의점/소매 POS 백엔드 토이 프로젝트 (`POS_APP`). Spring Boot 3.5 + MyBatis + SQLite, Java 21, Gradle, Lombok.

## 명령어

```bash
# 실행 — DB 경로는 POS_DB_URL 환경변수로 주입 (없으면 ./data/pos.db 로 접속하는데 스키마가 없어 쿼리가 실패함)
POS_DB_URL="jdbc:sqlite:/절대/경로/PRODUCT_MST" ./gradlew bootRun

./gradlew build                 # 컴파일 + 테스트
./gradlew test                  # 전체 테스트
./gradlew test --tests 'com.toyproject.pos.persistence.mapper.ProductMapperTest'                                   # 단일 클래스
./gradlew test --tests 'com.toyproject.pos.persistence.mapper.ProductMapperTest.findByBarcode_returnsSeededProduct' # 단일 메서드
```

- `./gradlew test`는 `build.gradle`에서 항상 `spring.profiles.active=test`로 실행되며 운영 DB에 접속하지 않는다.
- IDE에서 테스트를 직접 실행하면 Gradle 설정이 적용되지 않으므로 테스트 클래스에 반드시 `@ActiveProfiles("test")`를 붙인다.
- 별도 린터/포매터 설정은 없음.

## DB

- 운영 SQLite 파일은 레포 밖에 있다. `application.yml`의 `datasource.url`에 로컬 절대경로를 하드코딩하지 말 것 (`${POS_DB_URL:...}` 유지).
- 여러 POS가 같은 DB 파일에 동시에 쓰는 것을 전제로 `transaction_mode=IMMEDIATE`, `busy_timeout=5000`을 `spring.datasource.hikari.data-source-properties`로 설정한다 (URL이 아니라 드라이버 속성이라 `POS_DB_URL`을 바꿔도 적용됨). 빼면 "읽기 → 쓰기" 트랜잭션(반품)이 동시 결제와 부딪혀 즉시 `SQLITE_BUSY`가 난다 — `docs/bugfix-plan.md` 8장 E2, `ConcurrentPosIntegrationTest`.
- 스키마: `src/main/resources/db/schema.sql` (운영 DB에서 `sqlite3 <db> '.schema'`로 추출, `CREATE ... IF NOT EXISTS` 형태). 운영 스키마가 바뀌면 다시 추출해서 갱신한다.
  - 제외 테이블: `sqlite_sequence`(SQLite 내부), `raw_product_data`(이관용 원본, 코드에서 미사용).
- 알려진 스키마 문제점: `docs/schema-issues.md` (enum ↔ CHECK 불일치는 런타임 오류 가능).
- Consul config 의존성은 있지만 `application.yml`에서 비활성화되어 있음.

### 테스트 DB
- `src/test/resources/application-test.yml`: `build/test-db/pos-test.db?foreign_keys=true`. Gradle `test` 태스크가 실행 전에 `build/test-db`를 지우고 다시 만든다.
- 컨텍스트 시작 시 `db/schema.sql` → `db/test-data.sql`(`src/test/resources`) 자동 실행. 시드는 맨 앞에서 전체 DELETE 하므로 재실행해도 안전.
- 테스트 DB는 FK를 강제한다 (운영 연결은 SQLite 기본값대로 FK 미검사) — 운영에서 통과하던 데이터가 테스트에서 FK 오류를 낼 수 있음.
- 시드 규칙:
  - 바코드 `8800000000001` ~ `8800000000010`, 상품 ID `P001` ~ `P010`, 재고 = 일련번호 × 10
  - `8800000000008`: 브랜드 없음 / `8800000000009`: 성인 전용 / `8800000000010`: 판매중지(`discontinued`, 재고 0)
  - 미등록 바코드 테스트용: `8800000009999`

## 아키텍처

계층: `controller` → `service`(interfaces/implement) → `repository`(interfaces/implement) → `persistence.mapper`(MyBatis 인터페이스, `@MapperScan`) + `resources/mapper/*.xml`.

### 모델 계층 구분 (중요)
- `domain.model.*` — 비즈니스 로직을 가진 도메인 모델 (`Cart`, `CartLine`, `CartSummary`, `Sale`, `Payment`/`CashPayment` 등). 정적 팩토리 `of(...)` / Lombok `@Builder` 패턴.
- `domain.dto.*` — API 요청/응답 DTO (`*ReqDto`, `*ResDto`). 서비스는 도메인 모델을 만들고 응답 시 `SaleResDto.of(sale)`처럼 DTO로 변환.
- `persistence.row.*` — DB 테이블 1:1 Row 객체. 저장 시 `XxxRow.of(domain)`, 조회 시 `persistence.mapping.SaleRowMapper`가 Row → 도메인으로 조립.

### 거래 키
거래는 `CartKey(strCd, posNo, tranNo)`로 식별. 대부분의 요청 DTO가 `CartInfoReqDto`를 포함하고 `getKey()`로 `CartKey`를 꺼냄.

### 장바구니 (DB에 없음)
상품 담기(`CartServiceImpl.addItem`)는 미등록·판매중지(`discontinued`) 상품만 `false`(`ProductNotSellableException`)로 처리하고, 그 밖의 예외(DB 오류 등)는 전파한다. 발주중단(`suspend_order`)·상태 NULL은 판매 허용.

`CartRepositoryImpl`은 **인메모리 `ConcurrentHashMap<CartKey, Cart>`** 임시 저장소다 (추후 DB 이전 예정). 키는 점포·POS·거래번호 조합(`CartKey`, `@EqualsAndHashCode`)이고, POS 여러 대의 동시 요청을 전제로 동시 접근에 안전한 맵을 쓴다. 서버 재시작 시 장바구니가 사라진다. 상품 정보만 `ProductMapper`로 DB(`PRODUCT`)에서 조회.

### 결제 흐름 (`CheckoutServiceImpl.checkOut`, `@Transactional`)
1. 인메모리 Cart → `CartSummary` + `Payment` → `Sale` 도메인 생성
2. `CheckoutRepositoryImpl.save`: `SALES` insert(생성키로 `SALE_ID` 획득) → `SALE_LINES` → `PAYMENTS` → 현금이면 `CASH_PAYMENTS` + `RECEIPT_JOURNAL`(`Sale`을 Jackson으로 JSON 직렬화해 저장)
3. 같은 트랜잭션에서 재고 반영 (`ProductService.decreaseStock`, 바코드 기준): 정상 라인은 차감, 반품 라인은 수량이 음수라 복원, 취소 라인은 제외. 재고 부족이어도 막지 않음(음수 허용), 재고 행이 없으면 예외
4. 커밋 후(`TransactionSynchronization.afterCommit`) 인메모리 장바구니 삭제

거래 조회/반품은 DB에서 `SALE_LINES`·`PAYMENTS`를 거래 키로 읽어 `Cart`/`Payment`/`Sale`을 재구성한다 (복원은 `restoredOf` 팩토리로 저장값을 그대로 사용, 없는 거래는 `TransactionNotFoundException`). 반품(`@Transactional`)은 원거래 `SALE_STATUS`를 조건부 UPDATE로 `REFUNDED`로 바꾸고(이미 반품됐으면 거절), 원거래를 `refundAll()` 처리한 뒤 새 거래 키로 반품 `Sale`(`Sale.refundedOf`)을 새로 저장. 반품 금액은 결제액 기준이고 청구액·받은 금액은 음수로 저장된다.

### 미완성 부분
- 카드 결제(`CARD`)는 미구현 — 서비스에서 `UnsupportedOperationException`.
- `ProductController`의 엔드포인트는 주석 처리됨.
- 일부 `@GetMapping` 엔드포인트가 `@RequestBody`를 받음 (`/api/cart/item`, `/api/checkout/transaction/call` 등) — 호출 시 GET에 JSON 바디를 보내야 함.
- API 응답 형식이 통일되어 있지 않음 (`ApiResponseDto`, `boolean`, 도메인 객체 직접 반환이 섞여 있음).

코드 주석과 응답 메시지는 한국어로 작성되어 있음.

## 커밋 규칙

- 형식: `타입(범위): 한국어 요약` (범위는 생략 가능)
- 타입: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `wip`
- 요약은 한국어 명사형으로 짧게, 끝에 마침표 없음
  - 예) `test: 바코드 조회 매퍼 테스트 추가`
  - 예) `docs(db): 스키마 문제점 목록 정리`
  - 예) `chore: 테스트 프로필 Gradle 설정 정리`
- 본문이 필요하면 빈 줄 뒤에 `- ` 목록으로 한국어 작성
- 하나의 커밋에는 하나의 목적만. 리팩토링과 버그 수정, API 변경은 절대 같은 커밋에 섞지 않음
- 커밋 전에 메시지와 포함 파일을 보여주고 사용자 확인을 받음
