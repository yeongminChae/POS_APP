# API 명세

> POS·키오스크가 함께 쓰는 REST API. 변경 이력과 결정 근거는 [api-change-plan.md](api-change-plan.md).

## 공통 규칙

- 경로는 자원 단위 복수형 명사(`/api/carts`, `/api/sales`, `/api/products`), 동작은 HTTP 메서드. 메서드로 표현할 수 없는 동작만 하위 경로(`/batch`, `/cancel`, `/refunds`)
- 거래 키 `strCd`(점포)·`posNo`(POS 번호)·`tranNo`(거래번호)는 경로에 넣지 않는다. **GET은 쿼리 파라미터, 나머지는 JSON 바디**
- 성공: HTTP 200 + 응답 DTO 그대로 (공통 봉투 없음)
- 실패: HTTP 상태 + RFC 9457 `ProblemDetail`(`application/problem+json`)에 `code` 추가 (아래 [오류 응답](#오류-응답))
- 장바구니는 서버 메모리에 있다. 서버를 재시작하면 결제 전 장바구니는 사라진다

## 엔드포인트

| # | 메서드·경로 | 요청 | 성공 응답 | 실패 |
|---|---|---|---|---|
| 1 | `GET /api/carts?strCd=&posNo=&tranNo=` | 쿼리 파라미터 | `CartResDto` (없는 카트는 빈 카트) | 400 |
| 2 | `POST /api/carts/lines` | 거래 키 + `barcode` | `CartResDto` (담은 뒤) | 400, 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_DISCONTINUED` |
| 3 | `POST /api/carts/lines/batch` | 2의 배열 (1건 이상) | `CartResDto` | 2와 같음. **전부 아니면 전무** — 한 건이라도 실패하면 아무것도 담지 않음 |
| 4 | `PATCH /api/carts/lines` | 거래 키 + `barcode` + `qty`(≥ 1, 절대값) | `CartResDto` | 400, 404 `CART_LINE_NOT_FOUND` |
| 5 | `POST /api/carts/lines/cancel` | 거래 키 + `barcode` | `CartResDto` | 400, 404 `CART_LINE_NOT_FOUND` (이미 취소된 라인 포함) |
| 6 | `GET /api/carts/summary?strCd=&posNo=&tranNo=` | 쿼리 파라미터 | `CartSummaryResDto` | 400 |
| 7 | `GET /api/sales?strCd=&posNo=&tranNo=` | 쿼리 파라미터 | `SaleResDto` | 400, 404 `TRANSACTION_NOT_FOUND` |
| 8 | `POST /api/sales` | 거래 키 + `paymentMethod` + `amountReceived`(현금 필수) | `SaleResDto` | 400, 409 `DUPLICATE_TRANSACTION` |
| 9 | `POST /api/sales/refunds` | 새 거래 키 + 원거래 키 + `reason`(선택) | `SaleResDto` (반품 거래) | 400, 404, 409 `ALREADY_REFUNDED` |

`/api/products`는 자리만 있다 (키오스크 단계에서 메뉴 목록 등 추가).

## 요청

### 상품 담기·취소 (2, 3, 5)

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "1001", "barcode": "8800000000001" }
```

- 같은 바코드를 다시 담으면 기존 라인의 수량이 1 늘어난다. 취소된 라인과 같은 바코드는 새 라인으로 담긴다
- 판매중지(`discontinued`) 상품은 거절, 발주중단·상태 없음은 허용
- 취소는 라인을 지우지 않고 `itemStatus: "CANCELLED"`, 금액 0으로 남긴다

### 수량 변경 (4)

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "1001", "barcode": "8800000000001", "qty": 3 }
```

### 결제 (8)

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "1001", "paymentMethod": "CASH", "amountReceived": 20000 }
```

- `paymentMethod`: `CASH`만 지원. `CARD`는 400 `UNSUPPORTED_PAYMENT_METHOD`, 없는 값은 400 `MALFORMED_REQUEST`
- 받은 금액 ≤ 0 → 400 `INVALID_CASH_AMOUNT`, 결제액보다 적으면 400 `INSUFFICIENT_CASH`, 정상 라인이 없으면 400 `EMPTY_CART`
- 결제와 같은 트랜잭션에서 재고를 차감한다 (부족해도 막지 않음). 커밋 후 장바구니 삭제

### 반품 (9)

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "2001",
  "origStrCd": "S01", "origPosNo": "01", "origTranNo": "1001",
  "reason": "고객 변심" }
```

- `strCd`·`posNo`·`tranNo`는 **반품 거래의 새 키**, `orig*`는 원거래 키. `reason`은 100자 이하 (선택)
- 원거래 전체를 반품한다 (부분 반품 없음). 금액은 결제액 기준으로 음수 저장, 재고 복원

## 응답

### `CartResDto` (1~5), `CartSummaryResDto` (6, `SaleResDto.cart`)

```json
{ "strCd": "S01", "posNo": "01", "tranNo": "1001",
  "totalQty": 5, "totalAmount": 10500,
  "lines": [
    { "lineId": "0001", "barcode": "8800000000001", "name": "감자칩", "unitPrice": 1500, "qty": 2, "amount": 3000, "itemStatus": "NORMAL" },
    { "lineId": "0002", "barcode": "8800000000002", "name": "초콜릿", "unitPrice": 1200, "qty": 1, "amount": 0, "itemStatus": "CANCELLED" }
  ] }
```

- `lines`는 취소된 라인을 포함한다. `totalQty`·`totalAmount`는 정상 라인 합계

### `SaleResDto` (7~9)

```json
{ "receiptNo": "...", "storeCd": "S01", "posNo": "01", "tranNo": "1001",
  "cashier": "...", "saleStatus": "NORMAL", "approvedAt": "2026-11-16 10:00:00",
  "cart": { "...": "CartSummaryResDto" },
  "payment": { "method": "CASH", "status": "APPROVED", "amountDueToPay": 10500,
               "cash": { "amountReceived": 20000, "change": 9500 },
               "approvedAt": "2026-11-16 10:00:00" },
  "origSaleId": null }
```

- `saleStatus`: `NORMAL`, 반품된 원거래는 `REFUNDED`
- `payment.cash`: 현금 결제일 때만, 그 외 `null`
- 반품 거래: `origSaleId`에 원거래 `SALE_ID`, 금액(`amountDueToPay`, `cash.amountReceived`)은 음수
- 시각은 `Asia/Seoul` 기준 `yyyy-MM-dd HH:mm:ss`

## 오류 응답

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "이미 반품된 거래입니다.", "instance": "/api/sales/refunds",
  "code": "ALREADY_REFUNDED" }
```

검증 실패는 `errors`가 붙는다. `field`는 요청 JSON 기준 이름 (일괄 담기는 `[1].barcode`, 빈 목록 자체는 `""`).

```json
{ "status": 400, "code": "VALIDATION_FAILED", "detail": "요청 값이 올바르지 않습니다.",
  "errors": [ { "field": "qty", "message": "수량은 1 이상이어야 합니다" } ] }
```

| code | HTTP | 발생 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 필수 값 누락·공백, `qty` < 1, `reason` 100자 초과, 빈 일괄 목록, 현금 결제의 `amountReceived` 누락 |
| `MALFORMED_REQUEST` | 400 | JSON 문법 오류, 없는 enum 값 |
| `INVALID_CASH_AMOUNT` | 400 | 받은 금액 ≤ 0 |
| `INSUFFICIENT_CASH` | 400 | 받은 금액 < 결제액 |
| `EMPTY_CART` | 400 | 결제할 정상 라인 없음 |
| `UNSUPPORTED_PAYMENT_METHOD` | 400 | 카드 결제 (미구현) |
| `PRODUCT_NOT_FOUND` | 404 | 미등록 바코드 |
| `CART_LINE_NOT_FOUND` | 404 | 수량 변경·취소 대상 라인 없음 (취소된 라인 포함) |
| `TRANSACTION_NOT_FOUND` | 404 | 없는 거래 조회·반품 |
| `PRODUCT_DISCONTINUED` | 409 | 판매중지 상품 담기 |
| `ALREADY_REFUNDED` | 409 | 이미 반품된 거래 |
| `DUPLICATE_TRANSACTION` | 409 | 이미 결제된 거래번호로 결제 |
| `INTERNAL_ERROR` | 500 | 그 외 (재고 행 없음, DB 오류 등). `detail`은 "서버 오류가 발생했습니다."로 고정, 원인은 서버 로그 |

없는 경로는 Spring 기본 404 (`code` 없음).
