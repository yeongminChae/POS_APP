# 문서 안내

> 마지막 갱신: 2026-11-15, `f31dc92` 기준 (테스트 46개 통과, 스킵 0)

## 지금 어디까지 왔나

| 단계 | 문서 | 상태 | 커밋 범위 |
|---|---|---|---|
| 1. 리팩토링 (동작 변경 없음) | [architecture.md](architecture.md) | ✅ 완료 — R1~R12 + 잔여 정리 2건 | `6df59ec` ~ `f858f26` |
| 2. 버그 수정 | [bugfix-plan.md](bugfix-plan.md) | ✅ 완료 — 배치 1~5 (B1~B10, B12a·b, B14a, B15, B16, N1~N5) | `6fe8514` ~ `36c3353` |
| 3. API 변경 | [api-change-plan.md](api-change-plan.md) | 📝 계획 완료, **구현 전** — 결정 Q1~Q10, 사전 확인 X1~X3 끝 | `2e59437` ~ `f31dc92` (문서만) |

**다음 작업**: API 변경 배치 A — A0-0 남은 주석·중복 코드 정리 → A0-1 전용 예외 클래스 → A0-2 B11(거래번호 재사용) → A4 전역 예외 처리기 ([api-change-plan.md](api-change-plan.md) 3장, 6장).

## 남은 것 한눈에

| 구분 | 항목 | 어디서 처리 |
|---|---|---|
| API 변경 | GET 바디(A1·A2), 오류가 전부 500(A4), 요청 검증 없음(A6), 응답 형식 혼재(A3·A5), 현금 아닌 결제의 cash 블록(A7) | api-change-plan.md |
| API 단계로 넘긴 버그 | B11 거래번호 재사용 → A0-2·A4, B13 수량 누락 NPE·빈 TODO(L2) → A6 | api-change-plan.md |
| 잔여 정리 | 주석 처리된 매퍼·서비스 코드, `withOriginSaleId` 중복 호출 | api-change-plan.md A0-0 |
| 보류 (설계·정책 필요) | B12c 성인 인증, B14b 계산원(`"테스터"`), B12d 재고 부족 차단(정책상 안 함), B17 enum ↔ CHECK, T2 조회 트랜잭션, L5 결제 수단별 저장 정책 | bugfix-plan.md 1.3절, 7장 "버그 수정 단계 마무리" |

## 문서별 내용

| 문서 | 내용 | 읽을 곳 |
|---|---|---|
| [architecture.md](architecture.md) | 리팩토링 전 구조 분석(1·2장, `d2a038f` 기준 기록), 문제 분류(3장), 리팩토링 항목·진행 | 3장 분류표, 6장 진행 현황 |
| [bugfix-plan.md](bugfix-plan.md) | 버그별 재현·기대 동작·테스트, 사전 실험(E1~E4), 결정(Q1~Q6), 진행 기록 | 7장 진행 현황·마무리, 8장 실험 결과, 9장 SQLite 동시성·PostgreSQL 메모 |
| [api-change-plan.md](api-change-plan.md) | 현재·목표 API 계약, 오류 코드표, 배치별 바뀌는 테스트, 결정(Q1~Q10), 사전 확인 | 2장 목표 계약, 3장 배치, 5장 결정 |
| [schema-issues.md](schema-issues.md) | 운영 스키마 문제점 (FK, 타입, enum ↔ CHECK 등) | — |

## 작업 규칙 (요약)

- 항목마다: 실패 테스트 먼저(빨간 상태 확인) → 수정 → `./gradlew test` 전체 통과 → 계획에 없는 기대값 변경이 있으면 멈추고 보고
- 커밋: 하나의 목적만. refactor·fix·API 변경(`feat(api)`)·설정(`chore`)을 섞지 않음. 배치 단위로 메시지·파일을 보여주고 한 번에 승인받은 뒤 항목별 커밋 + 진행 현황 `docs` 커밋
- 로컬 테스트용 DB: `data/pos.db` (git 미추적, 운영 복사본 + 판매중지 대부분을 `normal`로 바꿈 — `data/README.md`)
