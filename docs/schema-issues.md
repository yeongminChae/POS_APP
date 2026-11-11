# 스키마 문제점

운영 DB 스키마(`src/main/resources/db/schema.sql`)를 검토하며 발견한 문제 목록. 아직 수정하지 않았다.

> ⚠️ = 실제 런타임 오류 가능성

| # | 분류 | 위치 | 내용 |
|---|---|---|---|
| 1 | FK 미적용 | 전체 | SQLite는 연결마다 `PRAGMA foreign_keys`가 기본 OFF이며 운영 연결도 OFF라 선언된 FK가 검사되지 않는다. 실제로 `PRODUCT` 710건이 존재하지 않는 `CATEGORY_ID = '42'`를 참조한다 (`CATEGORY_CODE = '42'`는 존재 → ID 자리에 코드가 들어간 것으로 보임). 테스트 DB는 `foreign_keys=true`. |
| 2 | FK 타입 불일치 | `PRODUCT.BRAND_ID`, `PRODUCT.CATEGORY_ID` | TEXT 컬럼이 INTEGER PK(`BRAND.BRAND_ID`, `CATEGORY.CATEGORY_ID`)를 참조한다. `CATEGORY_BRAND`는 INTEGER 사용. Java DTO는 String. |
| 3 | 금액 타입 | `PRODUCT.PRICE` | `REAL`(부동소수). `SALES`/`SALE_LINES` 금액은 `NUMERIC`, Java는 `BigDecimal` → 변환 시 오차 가능. 원화는 정수이므로 `INTEGER`가 적합. |
| 4 | 선언 타입과 실제 값 불일치 | `PRODUCT.CREATED_AT`, `PRODUCT.UPDATED_AT` | `INTEGER`로 선언됐지만 `'YYYY-MM-DD HH:MM:SS'` 문자열이 저장되어 있다. 다른 테이블은 `TEXT`. |
| 5 | NOT NULL / DEFAULT 누락 | `PRODUCT.IS_ADULT_ONLY`, `PRODUCT.PRODUCT_STATUS`, 각 테이블 `CREATED_AT`/`UPDATED_AT` | NULL 허용이고 기본값이 없다. 성인 여부·판매 상태가 NULL이면 판정이 모호해진다. |
| 6 | 값 제약 없음 | `PRODUCT.PRODUCT_STATUS` | CHECK 제약이 없다. 주석은 '판매중/발주중단/판매중단'인데 실제 값은 `normal` / `suspend_order` / `discontinued`. |
| 7 ⚠️ | **enum ↔ CHECK 불일치 (실제 런타임 오류 가능성)** | `SALE_LINES.ITEM_STATUS`, `PAYMENTS.STATUS`, `PAYMENTS.METHOD` | Java `ItemStatus.EMPTY`, `PaymentStatus.CREATED`는 DB CHECK에 없어 저장 시 `CHECK constraint failed` 발생. 반대로 DB의 `VOIDED`(STATUS), `VOUCHER`/`POINT`(METHOD)는 Java enum에 없어 조회 시 매핑 실패 가능. |
| 8 | 정렬 문제 | `SALE_LINES.LINE_NO` | `TEXT`라서 문자열 정렬 시 `'10'` < `'2'`. |
| 9 | 의미 불일치 | `CASH_PAYMENTS.PAYMENT_ID` | `PAYMENTS`와 1:1 FK인데 `AUTOINCREMENT`로 선언됨. 현재는 매퍼가 ID를 명시적으로 넣어 문제가 드러나지 않음. |
| 10 | 인덱스 | `PRODUCT`, `CATEGORY`, `PAYMENTS`, `SALES` | 중복: `idx_product_id`(PK와 중복), `idx_product_barcode`·`idx_category_code`(UNIQUE 자동 인덱스와 중복). 누락: `PAYMENTS.SALE_ID`, `SALES.ORIG_SALE_ID`. |
