# API 변경 계획

> **상태: 계획 완료, 구현 전.** 결정(Q1~Q11, 5장)·사전 확인(X1~X3, 8장) 끝. 다음 작업은 배치 A의 A0-0부터. `36c3353` 이후 커밋은 문서뿐이라 코드·라인 번호는 그대로 유효하다 (`f31dc92` 기준 확인).

> 기준 커밋: `36c3353` (버그 수정 단계 완료, 테스트 46개 통과·스킵 0). 이 문서를 쓰는 동안 코드는 수정하지 않았다.
> API 변경 ID(A1~A7)는 `docs/architecture.md` 3.3절 기준. 보류해 둔 B11·B13과 빈 TODO(L2)는 이 단계에서 함께 처리한다.
> 라인 번호는 `src/main/java/com/toyproject/pos/` 기준, 모두 `36c3353` 시점.

## 0. 요약

지금 API의 문제는 세 가지로 모인다.

1. **실패를 HTTP 상태로 알 수 없다** — 예외는 전역 처리기가 없어 모두 500이고, 미등록 상품은 반대로 HTTP 200 + 바디 `status: 404`다. 수량 변경·취소는 실패해도 200 + `false`다
2. **응답 모양이 제각각이다** — 엔드포인트 7개가 `ApiResponseDto` / `boolean` / 도메인 객체(`Cart`, `CartSummary`) / `SaleResDto`를 섞어 돌려준다. `Cart`를 그대로 내보내서 내부 필드(`barcodeToLineMap`, `seq`)가 노출된다
3. **GET에 JSON 바디를 받는다** — 브라우저·프록시·캐시 대부분이 GET 바디를 보장하지 않는다. 요약 API는 경로 변수를 받아 놓고 무시한다

순서: **A0(준비) → A4 → A6 → A5 → A3 → A1·A2·A8(경로 재설계) → A8-1(컨트롤러 이름) → A7 → 문서**. architecture.md 5장 순서를 따르되, 예외 타입을 먼저 정리하는 준비 배치를 앞에 둔다.

| 배치 | 내용 | 바뀌는 계약 |
|---|---|---|
| A0 준비 | 남은 주석·중복 코드 정리(refactor), 전용 예외 클래스 도입(refactor), B11 결제 전 중복 거래번호 확인(fix) | 없음 (여전히 500) |
| A4 | 전역 예외 처리기 + 오류 응답 형식 | 오류 응답 전부 (500 → 400/404/409) |
| A6 | 요청 검증(`@Valid`), 빈 TODO 제거, B13 | 잘못된 요청 → 400 |
| A5 | 카트·요약 응답 전용 DTO | 카트 조회·요약 응답 JSON 키 |
| A3 | 성공 응답 형식 통일 (`boolean`·`ApiResponseDto` 제거) | 담기·수량 변경·취소 응답 |
| A1·A2·A8 | GET 바디 → 쿼리 파라미터, 경로를 자원 단위로 재설계 (Q11) | **모든 엔드포인트의 경로**, GET 3개의 요청 방식 |
| A8-1 | `CheckoutController` → `SaleController` (refactor) | 없음 |
| A7 | 현금 아닌 결제의 `payment.cash` = null | 결제 응답 (지금은 현금뿐이라 실제 변화 없음) |
| 문서 | API 명세(`docs/api.md`), CLAUDE.md 갱신 | — |

---

## 1. 현재 API (`36c3353`)

| # | 메서드·경로 | 요청 | 성공 응답 | 실패 시 |
|---|---|---|---|---|
| 1 | `GET /api/cart/item` | **바디** `CartInfoReqDto` | 도메인 `Cart` (`linesList`, `totalQtyInLines`, `totalAmountInLines`, `barcodeToLineMap`, `seq` …) | — (없는 카트는 빈 카트를 새로 만듦) |
| 2 | `POST /api/cart/item/add` | `ProductReqDto` | 200 `{"message":"상품 추가 성공","status":200}` | **200** `{"message":"미등록 상품","status":404}` (미등록·판매중지 공통) |
| 3 | `POST /api/cart/item/add/many` | `List<ProductReqDto>` | 2와 같음 | 2와 같음. 실패 상품만 빠지고 나머지는 담김 |
| 4 | `POST /api/cart/item/update/qty` | `ProductReqDto`(+`qty`) | 200 `true` | 200 `false` (qty ≤ 0, 없는 라인, 취소된 라인). `qty` 누락 시 **500 NPE** (B13, `controller/CartController.java:45`) |
| 5 | `POST /api/cart/item/cancel` | `ProductReqDto` | 200 `true` | 200 `false` (없는 라인, 이미 취소) |
| 6 | `GET /api/checkout/{posNo}/{tranNo}/summary` | **바디** `PaymentReqDto` (경로 변수 무시, A2) | 도메인 `CartSummary` | — |
| 7 | `GET /api/checkout/transaction/call` | **바디** `CartInfoReqDto` | `SaleResDto` | 500 (`TransactionNotFoundException`) |
| 8 | `POST /api/checkout` | `PaymentReqDto` | `SaleResDto` | 500 (받은 금액 오류·부족, 빈 카트, 카드, 거래번호 재사용 UNIQUE 위반) |
| 9 | `POST /api/checkout/transaction/refund` | `RefundReqDto` | `SaleResDto` (+`origSaleId`) | 500 (없는 원거래, 이미 반품됨) |

지금 던지는 예외 (`throw new` 전수)

| 위치 | 예외 | 메시지 | 의미 |
|---|---|---|---|
| `service/implement/CartServiceImpl.java:102` | `ProductNotSellableException` | 상품을 찾을 수 없습니다 | 미등록 상품 (지금은 잡아서 `false`) |
| `service/implement/CartServiceImpl.java:106` | `ProductNotSellableException` | 판매중지 상품입니다 | 판매중지 (지금은 잡아서 `false`) |
| `domain/model/payment/CashPayment.java:46` | `IllegalArgumentException` | 지불 금액이 이상 합니다 | 받은 금액 ≤ 0 |
| `domain/model/payment/CashPayment.java:50` | `IllegalArgumentException` | 받은 금액이 결제 금액보다 적습니다 | B3 |
| `service/implement/CheckoutServiceImpl.java:135` | `IllegalStateException` | 결제할 상품이 없습니다 | B5 |
| `service/implement/CheckoutServiceImpl.java:83` | `IllegalStateException` | 이미 반품된 거래입니다 | B4 |
| `service/implement/CheckoutServiceImpl.java:116` | `TransactionNotFoundException` | 거래를 찾을 수 없습니다 | B9 |
| `service/implement/CheckoutServiceImpl.java:153`, `:156` | `UnsupportedOperationException` | CARD 결제는 아직 미지원 | B9 |
| `service/implement/ProductServiceImpl.java:26` | `IllegalStateException` | 재고 정보가 없습니다 | **데이터 정합성 오류** (500이어야 함) |
| `domain/model/cart/Cart.java:171` | `IllegalStateException` | 업데이트 실패 | 도달 불가 (앞에서 이미 걸러짐) |
| `repository/implement/CheckoutRepositoryImpl.java:54` | `UnsupportedOperationException` | CARD 결제는 아직 미지원 | 도달 불가 (L5) |
| `repository/implement/CheckoutRepositoryImpl.java:131` | `RuntimeException` | (Jackson 오류) | 500 |
| (DB) 거래번호 재사용 | `SQLiteException` UNIQUE | — | B11 |

⚠️ 같은 `IllegalStateException`이 "이미 반품됨"(클라이언트가 고칠 수 있는 상태 충돌)과 "재고 정보 없음"(서버 데이터 오류)에 함께 쓰인다. 그래서 **`IllegalStateException` → 409처럼 기본 예외 타입으로 매핑하면 안 된다.** A0에서 의미별 전용 예외로 나눈 뒤 A4에서 매핑한다.

---

## 2. 목표 계약 (괄호 안 Q 번호는 5장 결정 사항)

### 2.1 오류 응답 형식 (Q1)

Spring 6 기본 지원인 **RFC 9457 `ProblemDetail`** (`application/problem+json`)에 `code`만 추가한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 반품된 거래입니다.",
  "instance": "/api/checkout/transaction/refund",
  "code": "ALREADY_REFUNDED"
}
```

검증 실패(A6)는 `errors` 배열을 추가한다: `"errors": [{"field": "qty", "message": "1 이상이어야 합니다"}]`.
500은 `detail`에 내부 메시지·스택을 넣지 않고 `"서버 오류가 발생했습니다."`로 고정, 원인은 서버 로그(`ERROR`)에 남긴다.

### 2.2 오류 코드표 (Q3)

| code | HTTP | 발생 | 기존 예외 → 새 예외 (A0) |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | `@Valid` 실패, 필수 값 누락 (A6) | — (Spring 예외) |
| `MALFORMED_REQUEST` | 400 | JSON 문법 오류, 없는 enum 값(`paymentMethod: "XYZ"`) | — (Spring 예외) |
| `INVALID_CASH_AMOUNT` | 400 | 받은 금액 ≤ 0 | `IllegalArgumentException` → `InvalidCashAmountException` |
| `INSUFFICIENT_CASH` | 400 | 받은 금액 < 결제액 | `IllegalArgumentException` → `InsufficientCashException` |
| `EMPTY_CART` | 400 | 결제할 상품 없음 | `IllegalStateException` → `EmptyCartException` |
| `UNSUPPORTED_PAYMENT_METHOD` | 400 | 카드 결제 (Q3: 400 / 501) | `UnsupportedOperationException` → `UnsupportedPaymentMethodException` |
| `PRODUCT_NOT_FOUND` | 404 | 미등록 바코드 | `ProductNotSellableException` 분리 → `ProductNotFoundException` |
| `CART_LINE_NOT_FOUND` | 404 | 수량 변경·취소 대상 라인 없음(취소된 라인 포함) | 지금은 `false` → `CartLineNotFoundException` (A3) |
| `TRANSACTION_NOT_FOUND` | 404 | 없는 거래 조회·반품 | `TransactionNotFoundException` (그대로) |
| `PRODUCT_DISCONTINUED` | 409 | 판매중지 상품 담기 | `ProductNotSellableException` 분리 → `ProductDiscontinuedException` |
| `ALREADY_REFUNDED` | 409 | 이미 반품된 거래 | `IllegalStateException` → `AlreadyRefundedException` |
| `DUPLICATE_TRANSACTION` | 409 | 이미 결제된 거래번호로 결제 (B11) | `SQLiteException` → `DuplicateTransactionException` |
| `INTERNAL_ERROR` | 500 | 그 외 전부 (재고 행 없음, DB 오류, 직렬화 오류) | 그대로 |

- 모든 전용 예외는 `common/exception/PosException`(추상, `ErrorCode` 보유)을 상속한다. `ErrorCode` enum이 `code`·HTTP 상태를 가진다
- 400(요청 값 문제) / 404(대상 없음) / 409(대상의 현재 상태와 충돌) 세 가지만 쓴다

### 2.3 엔드포인트별 목표

| # | 목표 메서드·경로 (Q6, Q11) | 지금 경로 | 요청 | 성공 응답 (Q2, Q5) | 실패 |
|---|---|---|---|---|---|
| 1 | `GET /api/carts?strCd=&posNo=&tranNo=` | `GET /api/cart/item` | 쿼리 파라미터 | 200 `CartResDto` | 400 |
| 2 | `POST /api/carts/lines` | `POST /api/cart/item/add` | `ProductReqDto` | 200 `CartResDto` (담은 뒤 카트) | 400, 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_DISCONTINUED` |
| 3 | `POST /api/carts/lines/batch` | `POST /api/cart/item/add/many` | `List<ProductReqDto>` | 200 `CartResDto` | 2와 같음. **전부 아니면 전무** (Q4) |
| 4 | `PATCH /api/carts/lines` | `POST /api/cart/item/update/qty` | `UpdateQtyReqDto` (`qty` 필수, ≥ 1) | 200 `CartResDto` | 400, 404 `CART_LINE_NOT_FOUND` |
| 5 | `POST /api/carts/lines/cancel` | `POST /api/cart/item/cancel` | `ProductReqDto` | 200 `CartResDto` | 400, 404 `CART_LINE_NOT_FOUND` |
| 6 | `GET /api/carts/summary?strCd=&posNo=&tranNo=` | `GET /api/checkout/{posNo}/{tranNo}/summary` | 쿼리 파라미터 | 200 `CartSummaryResDto` | 400 |
| 7 | `GET /api/sales?strCd=&posNo=&tranNo=` | `GET /api/checkout/transaction/call` | 쿼리 파라미터 | 200 `SaleResDto` | 400, 404 |
| 8 | `POST /api/sales` | `POST /api/checkout` | `PaymentReqDto` | 200 `SaleResDto` (Q8) | 400, 409 `DUPLICATE_TRANSACTION` |
| 9 | `POST /api/sales/refunds` | `POST /api/checkout/transaction/refund` | `RefundReqDto` | 200 `SaleResDto` | 400, 404, 409 `ALREADY_REFUNDED` |
| — | `/api/products` (키오스크 단계에서 메뉴 목록 등 추가) | `/api/product` (빈 컨트롤러) | — | — | — |

- 경로 규칙 (Q11): 자원 이름은 복수형 명사(`carts`, `sales`, `products`), 동작은 HTTP 메서드로 표현. 메서드로 표현할 수 없는 동작만 하위 경로(`/batch`, `/cancel`, `/refunds`)
- 거래 키(`strCd`·`posNo`·`tranNo`)는 경로에 넣지 않고 GET은 쿼리 파라미터, 나머지는 바디로 받는다 (Q6). 경로에 넣으면 `/api/stores/{strCd}/pos/{posNo}/carts/{tranNo}/lines`처럼 길어지고 바디의 키와 중복됨
- 취소를 `DELETE`로 하지 않는 이유: 라인을 지우는 게 아니라 `CANCELLED` 상태로 남기고(영수증·저널에 기록), 요청에 바디(키·바코드)가 필요하기 때문
- 결제는 "판매를 만든다"는 의미라 `POST /api/sales`. 반품도 새 판매(반품 거래)를 만드므로 `POST /api/sales/refunds`

`CartResDto` (A5, 신규): 키 이름을 `SaleResDto.cart`(`CartSummaryResDto`)와 맞춘다

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "1001",
  "totalQty": 5, "totalAmount": 10500,
  "lines": [ { "lineId": "0001", "barcode": "...", "name": "...", "unitPrice": 1500, "qty": 2, "amount": 3000, "itemStatus": "NORMAL" } ] }
```

- 지금 `Cart` 응답과의 차이: `linesList` → `lines`, `totalQtyInLines` → `totalQty`, `totalAmountInLines` → `totalAmount`, `barcodeToLineMap`·`seq` 제거. 라인의 `productId`도 내보내지 않음 (`CartLineDto`가 이미 제외)
- 요약 API(6)는 지금 도메인 `CartSummary`(라인에 `productId` 포함)를 그대로 내보낸다 → 기존 `CartSummaryResDto`로 바꾸고, 키 필드(`strCd`·`posNo`·`tranNo`)를 `CartSummaryResDto`에 추가한다

---

## 3. 배치별 상세

표기: **바뀌는 테스트** = 기대값을 새 계약으로 바꾸는 기존 테스트. 여기에 없는 테스트가 깨지면 멈추고 보고한다 (버그 수정 단계와 같은 규칙).

### A0 — 준비 (계약 변경 없음)

**A0-0 `refactor: 주석 처리된 코드와 중복 원거래 ID 설정 제거`** (계획 정리 중 새로 확인한 잔여 정리)
- 리팩토링 R1(죽은 코드 제거) 때 영향 파일 목록에 없어 남은 주석 코드
  - `persistence/mapper/CheckoutMapper.java:30`, `:32` — 주석 처리된 `selectSaleLinesBySaleId`·`selectPaymentBySaleId`
  - `resources/mapper/CheckoutMapper.xml:190~216` — 같은 두 쿼리의 주석 처리된 `<select>`
  - `service/interfaces/CheckoutService.java:12` — 주석 처리된 `getSaleData`
- 중복 호출: `CheckoutServiceImpl.refundTransaction`의 `saleResDto.withOriginSaleId(sale.getSaleId())` — B8 이후 `SaleResDto.of`가 `refundDetail`에서 같은 값을 채움 (bugfix-plan.md 배치 2 메모). 호출과 `SaleResDto.withOriginSaleId` 메서드 삭제
- 그대로 두는 것
  - `controller/ProductController.java`: 엔드포인트 없는 빈 컨트롤러지만, 상품 API 자리로 남긴다 (삭제해도 동작은 같음 — 필요하면 이 커밋에 포함)
  - `UpdateQtyRequest`: A6에서 `UpdateQtyReqDto`로 대체하면서 삭제
  - `CheckoutRepositoryImpl.save`의 도달 불가 `case CARD`: L5(카드 결제 구현) 때
- 보호 테스트: 전체 (기대값 변경 0건). 반품 응답의 `origSaleId`는 `fullScenario`·`callRefundTransaction_returnsRefund`가 검사
- 위험도: 하

**A0-1 `refactor: 업무 예외를 의미별 전용 클래스로 분리`**
- `common/exception/ErrorCode`(enum: code, HTTP 상태), `PosException`(추상, `RuntimeException` 상속, `ErrorCode` 보유) 추가
- 2.2절 표대로 전용 예외를 만들되, **기존 테스트가 루트 원인 타입을 검사하므로 이번 커밋에서는 기존 타입을 상속**해 동작을 유지한다
  - 예: `InsufficientCashException extends IllegalArgumentException`, `EmptyCartException`·`AlreadyRefundedException extends IllegalStateException`, `UnsupportedPaymentMethodException extends UnsupportedOperationException`
  - Java는 다중 상속이 안 되므로 `PosException` 대신 **인터페이스 `HasErrorCode`**(`ErrorCode errorCode()`)로 코드를 붙이고, A4에서 처리기가 이 인터페이스로 매핑한다. A4 이후 상속을 `PosException`으로 바꿀지는 그때 결정 (지금은 안 바꿔도 동작 같음)
  - `ProductNotSellableException`은 `ProductNotFoundException`·`ProductDiscontinuedException`의 부모로 남겨 `CartServiceImpl.addItem`의 `catch`가 그대로 동작하게 한다
- 메시지는 그대로 (테스트가 `hasRootCauseMessage`로 검사)
- 보호 테스트: 전체 (기대값 변경 0건이어야 함)
- 위험도: 하

**A0-2 `fix(checkout): 이미 결제된 거래번호로 결제 시 명시적 예외` (B11)**
- 현재: 같은 키로 다시 결제하면 `SALES` INSERT에서 SQLite UNIQUE 위반(500, 원인이 드러나지 않음)
- 수정: `checkOut`에서 저장 전에 `checkoutRepository.existsTransaction(key)`(B9에서 추가한 메서드)로 확인 → `DuplicateTransactionException` (`IllegalStateException` 상속, A0-1 방식)
  - 동시성: 결제 트랜잭션은 `transaction_mode=IMMEDIATE`라 시작할 때 쓰기 잠금을 잡는다 → "확인 후 INSERT" 사이에 다른 POS가 끼어들 수 없다. UNIQUE 제약은 최후 방어선으로 남는다
- 실패 테스트: `CheckoutEdgeIntegrationTest.reuseTranNoAfterCheckout_violatesUnique`(:114) → `reuseTranNoAfterCheckout_isRejected` (루트 원인 `DuplicateTransactionException`, 메시지 "이미 결제된 거래번호입니다.", SALES 1건, 카트 유지)
- 같이 바뀌는 테스트: `failedCheckout_doesNotDecreaseStock`(:132)는 `isInstanceOf(Exception.class)`라 그대로 통과해야 함
- 위험도: 하

### A4 — 실패를 HTTP 상태로

**`feat(api): 전역 예외 처리기와 오류 응답 형식 도입`** (Q7: 커밋 타입)
- `common/web/GlobalExceptionHandler` (`@RestControllerAdvice`, `ResponseEntityExceptionHandler` 상속)
  - `HasErrorCode` → `ErrorCode`의 상태 + `ProblemDetail`(`code` 속성)
  - Spring 기본 예외(`HttpMessageNotReadableException` 등) → 400 `MALFORMED_REQUEST`
  - 그 외 `Exception` → 500 `INTERNAL_ERROR`, `log.error`로 원인 기록
- 미등록·판매중지 상품: `CartServiceImpl.addItem`의 `catch`를 없애고 예외를 그대로 던진다 → 404 / 409. `addItem`의 반환 타입은 A3까지 `boolean`(항상 true) 유지, A3에서 `Cart`로 바꿈
  - 일괄 등록(Q4 "전부 아니면 전무"): 바코드를 **먼저 전부 조회·검사한 뒤** 하나라도 실패하면 아무것도 담지 않고 예외 → 지금 동작(일부만 담김)이 바뀜
- `toAddItemResponse`의 "HTTP 200 + 바디 404" 분기 제거 (실패는 예외가 처리)
- **바뀌는 테스트** (MockMvc가 예외를 던지는 대신 오류 응답을 돌려주므로, `assertThatThrownBy(...).hasRootCause...` → `andExpect(status().is4xx()) + jsonPath("$.code")`로 바꿈)

| 테스트 | 지금 | A4 이후 |
|---|---|---|
| `CheckoutEdge.insufficientCash_isRejected` | 루트 원인 IAE | 400 `INSUFFICIENT_CASH` |
| `CheckoutEdge.zeroCash_throws` | 루트 원인 IAE | 400 `INVALID_CASH_AMOUNT` (이름 → `zeroCash_isRejected`) |
| `CheckoutEdge.emptyCart_isRejected`, `allCancelledCart_isRejected` | 루트 원인 ISE | 400 `EMPTY_CART` |
| `CheckoutEdge.cardPayment_isUnsupported` | 루트 원인 UOE | 400 `UNSUPPORTED_PAYMENT_METHOD` |
| `CheckoutEdge.callUnknownTransaction_throwsNotFound`, `refundUnknownTransaction_throwsNotFound` | 루트 원인 `TransactionNotFoundException` | 404 `TRANSACTION_NOT_FOUND` (이름 → `..._returns404`) |
| `CheckoutEdge.reuseTranNoAfterCheckout_isRejected` (A0-2) | 루트 원인 `DuplicateTransactionException` | 409 `DUPLICATE_TRANSACTION` |
| `CheckoutEdge.failedCheckout_doesNotDecreaseStock` | 예외 | 409 (재고 단언은 그대로) |
| `CheckoutScenario.refundTwice_isRejected`, `refundOfRefund_isRejected` | 루트 원인 ISE | 409 `ALREADY_REFUNDED` |
| `CheckoutScenario.refund_isAtomic` | 루트 원인 메시지 `injected failure` | **500 `INTERNAL_ERROR`**, `detail`에 내부 메시지 없음 (DB 단언은 그대로) |
| `Concurrent.sameOriginal_refundedConcurrently_onlyOnce` | 예외 1건 | 응답 상태 200 1건 + 409 1건 (`failureOf` 헬퍼 → 상태 코드 수집으로 변경) |
| `Cart.unknownBarcode_returns200WithBody404` | HTTP 200 + 바디 404 | **HTTP 404** `PRODUCT_NOT_FOUND` (이름 → `unknownBarcode_returns404`) |
| `Cart.discontinuedProduct_isRejected` | 바디 404 | HTTP 409 `PRODUCT_DISCONTINUED` |
| `Cart.addMany_withUnknown_isPartiallyApplied` | 바디 404, CHIPS·CHOCO는 담김 | HTTP 404, **카트 비어 있음** (이름 → `addMany_withUnknown_addsNothing`) |
| `Concurrent.addItems_concurrently_onManyCarts` | 바디 `status` 200 수집 | HTTP 상태 200 수집 (A3 전까지 바디도 그대로라 단언 유지 가능 — 진행 시 확인) |
| `CartServiceImplTest` 미등록·판매중지 2개 | `false` | 각각 `ProductNotFoundException`·`ProductDiscontinuedException` |

- 신규 테스트: `GlobalExceptionHandlerTest`(MockMvc) — 잘못된 JSON → 400 `MALFORMED_REQUEST`, 없는 `paymentMethod` 값 → 400, 500 응답에 스택·내부 메시지 없음
- 영향 파일: 신규 `common/web/GlobalExceptionHandler.java`, `common/exception/*`, `controller/CartController.java`, `service/implement/CartServiceImpl.java`, `service/interfaces/CartService.java`, 통합 테스트 4개, `CartServiceImplTest`
- 위험도: **상** (오류 계약 전부가 바뀜, 테스트 변경이 가장 많음)

### A6 — 요청 검증 (+B13, L2)

**`feat(api): 요청 값 검증 추가`**
- `spring-boot-starter-validation`은 이미 의존성에 있음. 컨트롤러에 `@Validated`도 이미 있음
- 제약
  - `CartInfoReqDto`: `strCd`·`posNo`·`tranNo` `@NotBlank`. `@JsonUnwrapped`로 포함하는 DTO 쪽 필드에 `@Valid`
  - `ProductReqDto`: `barcode` `@NotBlank`
  - 수량 변경 전용 `UpdateQtyReqDto`(신규, `CartInfoReqDto` + `barcode` + `qty`): `qty` `@NotNull @Min(1)`. 미사용 `UpdateQtyRequest`(record)는 삭제 — `ProductReqDto`는 담기·취소와 공유하므로 `qty` 필수 조건을 넣을 수 없음
  - `PaymentReqDto`: `paymentMethod` `@NotNull`. `amountReceived`는 현금일 때만 필수 (Q9) → 클래스 수준 제약 또는 서비스 검사로 누락 시 400 `VALIDATION_FAILED` (지금은 NPE 500). 값 범위(≤ 0)는 도메인 검사 `INVALID_CASH_AMOUNT` 유지
  - `RefundReqDto`: `origStrCd`·`origPosNo`·`origTranNo` `@NotBlank`, `reason` `@Size(max = 100)` (DB 컬럼은 없고 TR_JSON에만 저장)
  - 일괄 등록: `@RequestBody List<@Valid ProductReqDto>`, 빈 목록은 `@NotEmpty`로 400 (지금은 빈 목록 = 성공, Q4)
- **L2**: `CartController.updatedProductQty`의 빈 TODO(:45-48) 삭제 → B13 NPE의 원인이 사라짐
- **B13**: `qty` 누락 → 400 `VALIDATION_FAILED` (`errors[0].field = "qty"`)
- **바뀌는 테스트**
  - `Cart.updateQty_withoutQtyField_throwsNpe` → `updateQty_withoutQty_returns400`
  - `Cart.updateQty_rejectedCases`: qty 0·−1은 `false` → 400. 없는 라인·취소된 라인은 A3 전까지 `false` 유지
  - 신규: 키 누락(`tranNo` 없음) → 400, 바코드 공백 → 400, 반품 원거래 키 누락 → 400, 일괄 등록에서 한 건만 바코드 누락 → 400 + 아무것도 안 담김
- 사전 확인 X1 결과(8장): 단건은 `MethodArgumentNotValidException`, 목록은 `ConstraintViolationException`. `@JsonUnwrapped` 안쪽 제약은 실행되지만 필드 이름이 `cartInfoReqDto.tranNo`로 나오므로 처리기에서 `tranNo`로 정규화. 메시지는 한국어로 지정
- 영향 파일: `domain/dto/**/request/*.java`, 신규 `UpdateQtyReqDto`, 삭제 `UpdateQtyRequest`, `controller/*.java`, `GlobalExceptionHandler`, `service`(수량 변경 시그니처), `CartIntegrationTest`, `PosApiTestSupport.updateQty`
- 위험도: 중

### A5 — 카트·요약 응답 DTO

**`feat(api): 장바구니·결제 요약 응답을 전용 DTO로 변경`**
- 신규 `domain/dto/cart/CartResDto` (2.3절 모양), `CartController.getCurrentCart`가 반환
- 요약 API: 도메인 `CartSummary` → `CartSummaryResDto`. `CartSummaryResDto`에 `strCd`·`posNo`·`tranNo` 추가 → **`SaleResDto.cart`에도 세 필드가 새로 생김** (필드 추가라 기존 클라이언트는 영향 없음)
- **바뀌는 테스트** (대부분 키 이름 치환)
  - `linesList` 16곳 → `lines`, `totalQtyInLines`·`totalAmountInLines` 3곳 → `totalQty`·`totalAmount` (`fullScenario`, `CartIntegrationTest`, `CheckoutEdge`, `Concurrent`)
  - `Cart.getCart_exposesInternals` → `getCart_hidesInternals` (`barcodeToLineMap`·`seq`·라인 `productId` 없음)
  - `CheckoutEdge.summary_ignoresPathVariables`: 응답 키는 그대로(`tranNo`, `totalAmount`) — 통과해야 함. 경로 변수 무시는 A2에서 바뀜
- 영향 파일: 신규 `CartResDto`, `CartSummaryResDto`, `controller/*.java`, 테스트 5개 파일
- 위험도: 중 (치환이 많지만 기계적)

### A3 — 성공 응답 형식 통일

**`feat(api): 장바구니 변경 API 응답을 변경 후 장바구니로 통일`**
- 담기·일괄 담기·수량 변경·취소 모두 200 + `CartResDto` (Q5). `ApiResponseDto` 삭제
- 수량 변경·취소의 실패(`false`) → 404 `CART_LINE_NOT_FOUND`
  - `Cart.updatedItemQty`·`cancelCartLine`은 `boolean`을 돌려주므로 서비스에서 `false`면 `CartLineNotFoundException`. 도메인은 그대로
  - "이미 취소된 라인 취소"도 404 (도메인이 "없는 라인"과 구분하지 않음. 구분하려면 도메인 변경이 필요해 범위 밖)
- 서비스 시그니처: `addItem`·`addItems`·`updatedQty`·`cancelLine` → `Cart` 반환
- **바뀌는 테스트**
  - `.asBoolean()` 9곳: `true` → 200 + 카트 내용 단언, `false` → 404 `CART_LINE_NOT_FOUND` (`fullScenario`, `updateQty_rejectedCases`, `cancel_andRescan`)
  - `get("status")` 8곳: 바디 `status` 필드가 사라짐 → HTTP 상태로 (`fullScenario`, `Cart`, `Concurrent`)
  - `CartServiceImplTest`: 반환값 `boolean` → `Cart`
- 영향 파일: `controller/CartController.java`, `service/*/CartService*.java`, 삭제 `domain/dto/response/ApiResponseDto.java`, 테스트 4~5개 파일
- 위험도: 중

### A1·A2·A8 — 경로 재설계와 GET 바디 제거 (Q11)

**`feat(api): API 경로를 자원 단위로 재설계하고 조회를 쿼리 파라미터로 변경`**
- 경로와 요청 방식은 "엔드포인트 주소"라는 한 가지 계약이라 커밋 하나로 묶는다 (A1·A2: GET 바디 제거, A8: 경로 재설계)
- 경로: 2.3절 표대로
  - `CartController`: `@RequestMapping("/api/carts")`. 담기 `POST /lines`, 일괄 `POST /lines/batch`, 수량 `PATCH /lines`, 취소 `POST /lines/cancel`, 조회 `GET`, 요약 `GET /summary`
  - 요약 API는 장바구니 조회라 `CheckoutController`에서 **`CartController`로 옮긴다**. 서비스는 `getSaleSummary(PaymentReqDto)` → `CartService`의 요약 메서드(`CartKey`를 받음)로 이동
  - `CheckoutController`: `@RequestMapping("/api/sales")`. 결제 `POST`, 조회 `GET`, 반품 `POST /refunds`
  - `ProductController`: `@RequestMapping("/api/products")` (엔드포인트는 아직 없음)
  - 컨트롤러 클래스 이름은 이번 커밋에서 바꾸지 않는다 — 이름 변경은 동작이 같은 refactor라 바로 다음 A8-1에서 별도 커밋
- GET 바디 제거: `@RequestBody CartInfoReqDto` → `@ModelAttribute @Valid CartInfoReqDto` (쿼리 `strCd`·`posNo`·`tranNo`). X2에서 생성자 바인딩 확인됨 (누락 시 `MethodArgumentNotValidException` → 400)
- 옛 경로는 남기지 않는다 (클라이언트가 아직 없음). 옛 경로로 요청하면 Spring 기본 404
- **바뀌는 테스트**
  - `PosApiTestSupport` 헬퍼 8개의 경로·메서드 (`getCart`, `addItem`, `addItems`, `updateQty`(→ `patch`), `cancelItem`, `checkoutCash`, `callTransaction`, `refund`). 헬퍼를 쓰는 호출부는 그대로
  - 경로를 직접 쓰는 5곳: `CheckoutEdge`(카드 결제 :88, 요약 :150), `CartIntegrationTest`(수량 누락 :134, 다른 점포 담기 :200), `CheckoutScenario`(사유 없는 반품 :264)
  - `CheckoutEdge.summary_ignoresPathVariables` → `summary_usesQueryParameters`
  - 신규: 쿼리 파라미터 누락 → 400, 옛 경로(`/api/cart/item/add`) → 404
- 영향 파일: `controller/*.java`, `service/*/CartService*.java`, `service/*/CheckoutService*.java`, `PosApiTestSupport`, 통합 테스트 3개
- 위험도: 중 (경로를 쓰는 곳이 헬퍼와 5곳에 모여 있어 범위는 명확함)

### A8-1 — 컨트롤러 이름을 경로 자원에 맞춤 (refactor)

**`refactor: 판매 컨트롤러 이름을 경로에 맞게 변경`**
- `controller/CheckoutController.java` → `SaleController.java` (A1·A2·A8 이후 `/api/sales`만 담당)
- 그대로 두는 것
  - `CartController`(`/api/carts`), `ProductController`(`/api/products`): 이미 경로와 이름이 맞음
  - 경로 이름 `lines`: 도메인 `CartLine`, DB `SALE_LINES`, 응답 JSON `lines`와 같은 단어라 유지 (2026-11-15 결정 — `items` 등으로 바꾸면 경로만 다른 이름이 됨)
  - 서비스 이름 `CheckoutService`/`CheckoutServiceImpl`: "결제·반품"이라는 유스케이스 이름이라 유지. 바꾸면 영향 범위가 커지고 동작상 이득이 없음
  - 테스트 클래스 이름(`CheckoutEdgeIntegrationTest`, `CheckoutScenarioIntegrationTest`): 컨트롤러가 아니라 결제 시나리오를 검사하므로 유지
- 동작 변경 없음: 경로·응답 그대로, 테스트 변경 0건이어야 함 (클래스를 직접 참조하는 테스트 없음)
- 위험도: 하

### A7 — 현금이 아닐 때 `payment.cash` = null

**`feat(api): 현금 외 결제 응답의 현금 정보 생략`**
- `PaymentResDto.of`: `payment instanceof CashPayment`일 때만 `CashPayloadDto` 생성 (`domain/dto/payment/PaymentResDto.java:29`)
- 지금은 카드 결제가 400으로 거절되므로 **실제 응답에는 변화 없음**. 단위 테스트로만 확인 (`PaymentResDtoTest`: 현금 → cash 있음, 가짜 `Payment` 구현 → cash null)
- 위험도: 하. Q10대로 이번 단계에서 진행

### 문서

**`docs: API 명세 및 CLAUDE.md 갱신`**
- 신규 `docs/api.md`: 2.3절 표 + 요청·응답 예시 + 2.2절 오류 코드표
- CLAUDE.md: "미완성 부분"의 GET 바디·응답 형식 항목 삭제, "API 규칙" 절 추가 (성공은 DTO, 실패는 `ProblemDetail` + `code`, GET은 쿼리 파라미터, 새 업무 예외는 `HasErrorCode`/`ErrorCode`에 등록)
- architecture.md 3.3절 표에 완료 표시

---

## 4. 사전 확인 (코드 작성 전, 임시 테스트로)

| ID | 확인할 것 | 결과에 따른 변화 |
|---|---|---|
| X1 | `@Validated` 컨트롤러에서 ① `@Valid @RequestBody` DTO 실패 → `MethodArgumentNotValidException`인지 ② `List<@Valid ProductReqDto>` 실패 → `HandlerMethodValidationException`인지 ③ `@JsonUnwrapped` + `@Valid` 필드의 제약이 실행되는지 | ②가 다른 예외면 처리기에 매핑 추가. ③이 안 되면 `CartInfoReqDto` 필드를 각 DTO에 직접 선언하거나 클래스 수준 제약으로 대체 |
| X2 | final 필드 + `@AllArgsConstructor`인 `CartInfoReqDto`를 `@ModelAttribute`로 받을 때 생성자 바인딩이 되는지 (`-parameters` 컴파일 옵션 필요, Spring Boot Gradle 플러그인이 기본으로 켬) | 안 되면 `@RequestParam` 3개로 받아 `CartKey.of(...)` |
| X3 | `ResponseEntityExceptionHandler`를 상속했을 때 `ProblemDetail`의 `code` 같은 추가 속성이 JSON에 평평하게(최상위 키로) 나오는지 (`setProperty`) | 안 되면 `properties` 아래로 나옴 → 테스트 경로(`$.code`) 조정 |

## 5. 결정 사항

> Q1~Q10은 **권장안으로 확정**, Q11은 확장 방향(`docs/expansion-notes.md`)을 검토한 뒤 추가로 결정 (사용자 승인). 아래 "결정" 열이 확정된 내용이다.

| # | 질문 | 결정 | 영향 |
|---|---|---|---|
| Q1 | 오류 응답 형식 | **`ProblemDetail`(RFC 9457) + `code`** — Spring 기본 지원, 별도 DTO 불필요 | A4 |
| Q2 | 성공 응답을 공통 봉투(`{success, data}`)로 감쌀지 | **감싸지 않음** — 성공은 DTO 그대로, 실패만 HTTP 상태 + `ProblemDetail`. 봉투를 쓰면 9개 응답과 테스트 대부분의 JSON 경로가 바뀜 | A3 전체 |
| Q3 | 업무 규칙 위반의 HTTP 상태 | **400(요청 값) / 404(대상 없음) / 409(상태 충돌)** 세 가지만. 카드 결제는 **400** | A4 |
| Q4 | 일괄 담기 중 한 건이 실패하면 | **전부 아니면 전무** — 먼저 전부 검사, 하나라도 실패하면 아무것도 안 담고 404/409. 빈 목록은 400 | A4, A6 |
| Q5 | 장바구니 변경(담기·수량·취소) 성공 응답 | **변경 후 카트(`CartResDto`)** — POS 화면이 바로 다시 그릴 수 있음 | A3 |
| Q6 | GET 조회의 키 전달 방식 | **쿼리 파라미터** (`?strCd=&posNo=&tranNo=`). 요약 API 경로는 Q11에 따라 `/api/carts/summary` | A1·A2·A8 |
| Q7 | API 변경 커밋 타입 | **`feat(api): ...`**, 본문 첫 줄에 `- API 변경: <무엇이 어떻게>` (CLAUDE.md 타입 목록 안에서 고름) | 전체 |
| Q8 | 결제·반품 성공 상태 | **200 유지** (201 Created는 `Location` 헤더 설계까지 필요) | A3 |
| Q9 | 현금 결제에서 `amountReceived` 누락 | **400 `VALIDATION_FAILED`** — 지금은 NPE(500). 현금일 때만 필수라 클래스 수준 제약 또는 서비스 검사로 구현 | A6 |
| Q10 | A7을 지금 할지 | **지금 함** (작고 독립적, 카드 구현 때 잊지 않게) | A7 |
| Q11 | URL 구조 | **자원 단위로 재설계 (B안)** — `/api/carts`, `/api/sales`, `/api/products`. 키오스크(선불, 같은 API를 UI만 다르게)와 ERP 조회 API가 붙을 것을 고려해, 이번 API 변경에서 경로를 한 번에 정리 (2026-11-15 추가 결정). 대안이었던 "기존 경로 유지"는 동사가 섞인 경로(`/item/update/qty`, `/transaction/call`)가 남아 채택하지 않음 | A1·A2·A8 |

## 6. 진행 규칙

버그 수정 단계(bugfix-plan.md 4.3절)와 같다.

1. 바뀌는 테스트를 새 계약으로 먼저 고치고 **빨간 상태를 확인**
2. 수정 → `./gradlew test` 전체 통과
3. `git diff src/test`의 기대값 변경이 3장 "바뀌는 테스트"에 있는 것뿐인지 확인. 없는 변경이 생기면 **멈추고 보고**
4. 커밋: API 변경은 `feat(api)`, refactor·fix와 섞지 않음. 커밋 전에 메시지·파일 확인 (배치 단위로 한 번에 승인)
5. 배치마다 7장(진행 현황)에 기록하는 `docs` 커밋

배치 묶음 제안: **배치 A = A0-0, A0-1, A0-2, A4** (정리 + 오류 계약) → **배치 B = A6, A5** → **배치 C = A3, A1·A2·A8, A8-1(컨트롤러 이름, refactor), A7, 문서**. 배치 A가 테스트 변경이 가장 많고 위험도가 높다.

## 7. 진행 현황

| 순서 | 배치 | 항목 | 커밋 | 비고 |
|---|---|---|---|---|
| 1 | A | A0-0 주석·중복 코드 정리 | `43cf907` | refactor, 테스트 변경 없음 |
| 2 | A | A0-1 전용 예외 클래스 | `b1313ef` | refactor, 테스트 변경 없음 |
| 3 | A | A0-2 B11 거래번호 재사용 거절 | `59194fe` | `reuseTranNoAfterCheckout_violatesUnique` → `reuseTranNoAfterCheckout_isRejected` |
| 4 | A | A4 전역 예외 처리기 | `62e9777` | 기존 테스트 17개 기대값 변경, 신규 `ErrorResponseIntegrationTest` 2개 |

### 배치 A 메모

- 테스트: 46개 → **48개**(스킵 0). 늘어난 2개는 `ErrorResponseIntegrationTest`(깨진 JSON, 없는 결제 수단 값)
- **A0-0**: 주석 처리된 매퍼 메서드 2개·XML `<select>` 2개·`CheckoutService.getSaleData` 주석, `withOriginSaleId` 호출과 메서드 삭제. 테스트 변경 없이 통과 (반품 응답 `origSaleId`는 `SaleResDto.of`가 채움 — `fullScenario`·`callRefundTransaction_returnsRefund`로 확인)
- **A0-1**
  - `common/exception/ErrorCode`(13개, HTTP 상태 보유)와 인터페이스 `HasErrorCode`를 추가. 계획 2.2절의 추상 클래스 `PosException`은 **만들지 않았다** — 전용 예외가 `IllegalArgumentException` 등 기존 타입을 상속해야 해서 인터페이스만으로 충분 (3장 A0-1에 적은 방식)
  - 전용 예외 7개: `InvalidCashAmountException`·`InsufficientCashException`(← IAE), `EmptyCartException`·`AlreadyRefundedException`(← ISE), `UnsupportedPaymentMethodException`(← UOE), `ProductNotFoundException`·`ProductDiscontinuedException`(← 추상 `ProductNotSellableException`). `TransactionNotFoundException`에 `HasErrorCode` 추가. 메시지는 그대로라 테스트 변경 없음
- **A0-2 (B11)**: 빨간 이유 — 루트 원인이 `SQLiteException`(UNIQUE 위반). `checkOut` 맨 앞에서 `existsTransaction`으로 확인 후 `DuplicateTransactionException`(← ISE). `failedCheckout_doesNotDecreaseStock`은 계획대로 그대로 통과
- **A4**
  - `common/web/GlobalExceptionHandler`(`ResponseEntityExceptionHandler` 상속): `HasErrorCode` → 해당 상태·코드, 예외 메시지를 `detail`로 / `MethodArgumentNotValidException`·`ConstraintViolationException` → 400 `VALIDATION_FAILED` / `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST` / 그 외 → 500 `INTERNAL_ERROR`, `detail`은 "서버 오류가 발생했습니다.", 원인은 `log.error`
  - 검증 예외 두 가지는 A6에서 쓰지만, X1 결과대로 처리기에 미리 넣었다 (지금은 검증 제약이 없어 발생하지 않음). `errors[]` 필드 목록과 필드 이름 정규화는 계획대로 A6
  - 상품 담기: `addItem`의 `catch` 제거 → 미등록 404 `PRODUCT_NOT_FOUND`, 판매중지 409 `PRODUCT_DISCONTINUED`. `addItem`은 A3까지 항상 `true`. 일괄 담기는 **먼저 전부 조회·검사한 뒤** 담음 (Q4 전부 아니면 전무). `toAddItemResponse`의 "HTTP 200 + 바디 404" 분기 삭제
  - 테스트 헬퍼 `PosApiTestSupport.error(result, status, code)` 추가: HTTP 상태, `application/problem+json`, `code`를 검사하고 바디를 돌려줌
  - 바뀐 기존 테스트 17개: `CheckoutEdge` 9개(`insufficientCash_isRejected`, `zeroCash_throws`→`zeroCash_isRejected`, `emptyCart_isRejected`, `allCancelledCart_isRejected`, `cardPayment_isUnsupported`, `callUnknownTransaction_*`→`_returns404`, `refundUnknownTransaction_*`→`_returns404`, `reuseTranNoAfterCheckout_isRejected`, `failedCheckout_doesNotDecreaseStock`), `CheckoutScenario` 3개(`refundTwice_isRejected`, `refundOfRefund_isRejected`, `refund_isAtomic`), `Concurrent` 1개(`sameOriginal_refundedConcurrently_onlyOnce` — 예외 수집 → 응답 상태 수집, 200·409 각 1건), `Cart` 4개(`unknownBarcode_*`→`_returns404`, `discontinuedProduct_isRejected`, `addMany_withUnknown_*`→`_addsNothing`, 아래 `updateQty_withoutQtyField_*`), `CartServiceImplTest` 2개(`false` → 예외)
  - 빨간 상태 확인: 수정 전 21개 실패 (예외가 그대로 전파되거나, 미등록·판매중지가 HTTP 200) — 모두 의도한 이유
  - **⚠️ 계획에 없던 기대값 변경 1건**: `CartIntegrationTest.updateQty_withoutQtyField_throwsNpe` → `updateQty_withoutQtyField_returns500`. 처리기가 모든 예외를 응답으로 바꾸므로 NPE도 던져지지 않고 500 `INTERNAL_ERROR`가 된다. 3장 A4 표에서 빠뜨린 항목이고 동작상 자연스러운 결과라 그대로 진행했다. B13은 A6에서 400으로 바뀜
  - 동시성 테스트는 수정 후 3회 반복 통과. `refund_isAtomic`은 의도대로 테스트 로그에 `ERROR 처리되지 않은 예외`를 남긴다
- 500으로 남는 예외 (의도함): `ProductServiceImpl`의 "재고 정보가 없습니다"(데이터 정합성), `Cart.updatedItemQty`의 "업데이트 실패"(도달 불가), 저널 JSON 직렬화 실패, DB 오류
- 계획에 없는 기대값 변경: 위 1건 외 **없음**

## 8. 사전 확인 기록

`2e59437` 기준, 임시 테스트(`src/test/java/.../experiment/`, 실제 DTO와 같은 모양 — final 필드 + 전체 인자 생성자 + `@JsonUnwrapped` — 의 테스트 전용 컨트롤러·`ResponseEntityExceptionHandler` 상속 처리기)로 확인한 뒤 삭제했다 (커밋하지 않음). `@SpringBootTest` + MockMvc, 실제 앱과 같은 `@Validated` 컨트롤러.

| 확인 | 요청 | 결과 |
|---|---|---|
| X1 단건 바디 | 정상 | 200, `@JsonUnwrapped` 키도 정상 바인딩 |
| | `@JsonUnwrapped` 안쪽 `tranNo` 누락 | **400 `MethodArgumentNotValidException`** — 안쪽 제약도 실행됨. 단 필드 이름이 **`key.tranNo`**(자바 필드 경로)로 나옴 |
| | `barcode` 누락 | 400 `MethodArgumentNotValidException`, 필드 `barcode` |
| | 깨진 JSON | 400 `HttpMessageNotReadableException` |
| X1 목록 바디 (`List<@Valid ...>`, `@NotEmpty`) | 한 건 `barcode` 누락 | **400 `ConstraintViolationException`** (`HandlerMethodValidationException` 아님 — 클래스의 `@Validated`가 AOP 메서드 검증을 먼저 함). 경로 `many.reqs[1].barcode` |
| | 한 건 `tranNo` 누락 | 같은 예외, 경로 `many.reqs[1].key.tranNo` |
| | 빈 목록 | 같은 예외, 경로 `many.reqs` |
| X2 `@ModelAttribute` (쿼리 파라미터) | 정상 | 200, final 필드 + 생성자 DTO에 **생성자 바인딩 동작** |
| | `tranNo` 누락 | 400 `MethodArgumentNotValidException`, 필드 `tranNo` |
| X3 `ProblemDetail.setProperty("code", …)` | 업무 예외 | 409, `application/problem+json`, **`code`가 최상위 키**로 나옴 |

추가로 확인된 점
- 기본 검증 메시지가 영어(`must not be blank`)다 → 제약마다 `message = "..."`로 한국어 메시지를 지정한다
- 기본 `detail`도 영어(`Invalid request content.`, `Failed to read request`)다 → 처리기에서 한국어로 덮어쓴다

### 결과로 바뀐 계획 (A4·A6)

| 항목 | 변경 |
|---|---|
| A4 처리기 | `MethodArgumentNotValidException`(단건 바디·쿼리)과 **`jakarta.validation.ConstraintViolationException`(목록 바디)** 둘 다 400 `VALIDATION_FAILED`로 매핑. `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST` |
| A6 `errors[].field` | **JSON 필드 이름으로 정규화**: `@JsonUnwrapped` 필드 접두사(`cartInfoReqDto.`) 제거, 목록은 메서드·파라미터 접두사(`addToCart.productReqDtoList`) 제거 후 `[1].barcode` 형태 |
| A6 메시지 | 모든 제약에 한국어 `message` 지정 (예: `@NotBlank(message = "거래번호는 필수입니다")`) |
| A1·A2·A8 | 계획대로 `@ModelAttribute @Valid CartInfoReqDto` 사용 (대안 `@RequestParam` 불필요) |
| X3 | 테스트는 `jsonPath("$.code")`로 검사 (계획대로) |
