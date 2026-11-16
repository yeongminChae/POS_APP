# 문서 안내

> 마지막 갱신: 2026-11-16, `5855623` 기준 (테스트 63개 통과, 스킵 0)

## 지금 어디까지 왔나

| 단계 | 문서 | 상태 | 커밋 범위 |
|---|---|---|---|
| 1. 리팩토링 (동작 변경 없음) | [architecture.md](architecture.md) | ✅ 완료 — R1~R12 + 잔여 정리 2건 | `6df59ec` ~ `f858f26` |
| 2. 버그 수정 | [bugfix-plan.md](bugfix-plan.md) | ✅ 완료 — 배치 1~5 (B1~B10, B12a·b, B14a, B15, B16, N1~N5) | `6fe8514` ~ `36c3353` |
| 3. API 변경 | [api.md](api.md) | **현재 API 명세** — 엔드포인트, 요청·응답 예시, 오류 코드표 | — |
| [api-change-plan.md](api-change-plan.md) | ✅ 완료 — 배치 A~C (A0~A8, 문서). 결과 명세 [api.md](api.md) | `2e59437` ~ `5855623` |
| 4. 카페 키오스크 (선불) → 데이터 보강 → ERP | [expansion-notes.md](expansion-notes.md) | 💭 방향 메모만 — 시작할 때 계획 문서를 따로 만듦 | — |

**다음 작업**: 4단계(카페 키오스크) 계획 문서 작성 — [expansion-notes.md](expansion-notes.md)의 "시작 전에 정할 것"부터. 보류 항목(아래 표) 중 키오스크에 필요한 것을 함께 정한다.

## 남은 것 한눈에

| 구분 | 항목 | 어디서 처리 |
|---|---|---|
| 보류 (설계·정책 필요) | B12c 성인 인증, B14b 계산원(`"테스터"`), B12d 재고 부족 차단(정책상 안 함), B17 enum ↔ CHECK, T2 조회 트랜잭션, L5 결제 수단별 저장 정책 | bugfix-plan.md 1.3절, 7장 "버그 수정 단계 마무리" |

## 문서별 내용

| 문서 | 내용 | 읽을 곳 |
|---|---|---|
| [architecture.md](architecture.md) | 리팩토링 전 구조 분석(1·2장, `d2a038f` 기준 기록), 문제 분류(3장), 리팩토링 항목·진행 | 3장 분류표, 6장 진행 현황 |
| [bugfix-plan.md](bugfix-plan.md) | 버그별 재현·기대 동작·테스트, 사전 실험(E1~E4), 결정(Q1~Q6), 진행 기록 | 7장 진행 현황·마무리, 8장 실험 결과, 9장 SQLite 동시성·PostgreSQL 메모 |
| [api.md](api.md) | **현재 API 명세** — 엔드포인트, 요청·응답 예시, 오류 코드표 | — |
| [api-change-plan.md](api-change-plan.md) | 현재·목표 API 계약, 오류 코드표, 배치별 바뀌는 테스트, 결정(Q1~Q11), 사전 확인 | 2.3절 목표 경로표, 3장 배치, 5장 결정 |
| [expansion-notes.md](expansion-notes.md) | 카페 키오스크(선불, POS와 같은 API)·판매 데이터 보강·ERP 방향, 각 단계 전에 정할 것 | 2장 키오스크, 3장 데이터 보강 |
| [schema-issues.md](schema-issues.md) | 운영 스키마 문제점 (FK, 타입, enum ↔ CHECK 등) | — |

## 작업 규칙 (요약)

- 항목마다: 실패 테스트 먼저(빨간 상태 확인) → 수정 → `./gradlew test` 전체 통과 → 계획에 없는 기대값 변경이 있으면 멈추고 보고
- 커밋: 하나의 목적만. refactor·fix·API 변경(`feat(api)`)·설정(`chore`)을 섞지 않음. 배치 단위로 메시지·파일을 보여주고 한 번에 승인받은 뒤 항목별 커밋 + 진행 현황 `docs` 커밋
- 로컬 테스트용 DB: `data/pos.db` (git 미추적, 운영 복사본 + 판매중지 대부분을 `normal`로 바꿈 — `data/README.md`)
