package com.toyproject.pos.domain.model.cart;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Cart 도메인 모델 — 결제 전 "한 거래의 장바구니"
 *
 * <h2>앱에서의 역할 (비즈니스 플로우)</h2>
 * <ol>
 *   <li>POS·키오스크가 상품을 스캔하면 {@code CartServiceImpl}이 상품을 조회해 {@link CartLine}을 만들고,
 *       인메모리 저장소({@code CartRepositoryImpl})가 거래 키({@link CartKey})로 찾은 이 Cart 에 {@link #addOrIncrease}로 담는다.</li>
 *   <li>수량 변경({@link #updatedItemQty})·지정 취소({@link #cancelCartLine})도 이 객체에서 처리한다.</li>
 *   <li>결제 시 {@link CartSummary#of(Cart)}로 합계 스냅샷을 만들어 {@code Sale}에 담고, 커밋 후 저장소에서 삭제된다.</li>
 *   <li>반품 시에는 DB 에 저장된 판매 라인으로 Cart 를 다시 만든 뒤({@code SaleRowMapper}) {@link #refundAll}로 정상 라인을 반품 라인으로 바꾼다.</li>
 * </ol>
 * <p>DB 테이블이 없다. 서버를 재시작하면 결제 전 장바구니는 사라진다.</p>
 * <p>동시성: 같은 거래 키의 Cart 는 한 POS 만 다룬다는 전제다. 맵 자체(HashMap/LinkedHashMap)는 스레드 안전하지 않고,
 * 거래 키 사이의 동시 접근은 저장소의 {@code ConcurrentHashMap}이 막는다.</p>
 *
 * <h2>핵심 불변식(Invariants)</h2>
 * <ul>
 *   <li>바코드(barcode) 당 ACTIVE(정상) 라인은 0개 또는 1개만 존재한다.</li>
 *   <li>취소된 라인(CANCELLED)은 {@code lines} 에 남겨두되, 인덱스 {@code barcodeToLineMap}에서는 제거한다.</li>
 *   <li>{@code barcodeToLineMap} 은 항상 "바코드 → ACTIVE lineId" 만을 가리킨다(상태 문자열 저장 금지).</li>
 * </ul>
 *
 * <h2>자료구조</h2>
 * <ul>
 *   <li>{@code lines} : lineId → CartLine (실데이터 저장소)</li>
 *   <li>{@code barcodeToLineMap} : barcode → ACTIVE lineId (조회 인덱스)</li>
 * </ul>
 *
 * <h2>메서드 사용 가이드</h2>
 * <ul>
 *   <li>{@link #getActiveLineId(String)} : 조회 전용(맵 변경 없음)</li>
 *   <li>{@link #ensureActiveLineId(String)} : 쓰기 전용(없거나 잘못된 경우 새 lineId 생성)</li>
 *   <li>{@link #addOrIncrease(CartLine)} : 스캔/추가. 없으면 생성, 있으면 수량 증가(merge)</li>
 *   <li>{@link #updatedItemQty(String, int)} : 수량을 "절대값"으로 설정(compute)</li>
 *   <li>{@link #cancelCartLine(String)} : ACTIVE 라인을 취소 상태로 전환하고 인덱스에서 제거</li>
 * </ul>
 *
 * <h2>주의</h2>
 * <ul>
 *   <li>조회(get*) 메서드는 절대 맵을 변경하지 않도록 유지한다.</li>
 *   <li>라인을 취소로 바꾸는 경우 {@code lines}에 새 객체를 다시 저장해야 실제로 반영된다(불변 도메인).</li>
 *   <li>빌더 사용 시 {@code seq}, {@code barcodeToLineMap}도 @Builder.Default 적용 권장(널 방지).</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@Builder
public class Cart {
    private final String strCd;   // 점포 코드
    private final String posNo;   // POS(또는 키오스크) 번호
    private final String tranNo;  // 거래번호 — 세 값이 합쳐 거래 키(CartKey)가 된다

    // 실데이터: lineId → CartLine. 담은 순서를 유지해야 영수증·응답의 라인 순서가 맞으므로 LinkedHashMap 으로 만든다 ({@link #of})
    // 외부에서 맵을 직접 바꾸지 못하도록 getter 를 막고, 조회는 {@link #getLinesList()}로만 한다
    @Getter(AccessLevel.NONE)
    private final Map<String, CartLine> lines;

    // 조회 인덱스: barcode → ACTIVE lineId (취소된 라인은 여기서 빠진다 — 같은 바코드를 다시 스캔하면 새 라인이 생기는 이유)
    @Builder.Default
    private final Map<String, String> barcodeToLineMap = new HashMap<>();

    // 시퀀스 (카트 인스턴스 단위 증가) — lineId 생성에 사용
    @Builder.Default
    private final AtomicInteger seq = new AtomicInteger(0);

    /**
     * 거래 키와 라인으로 Cart 를 만드는 정적 팩토리 (Cart 생성 책임을 도메인이 가진다).
     *
     * <ul>
     *   <li>새 장바구니: 저장소가 빈 맵으로 호출한다 ({@code CartRepositoryImpl}).</li>
     *   <li>DB 복원: 저장된 판매 라인 맵으로 호출한다 ({@code SaleRowMapper}). 이때 바코드 인덱스·시퀀스는 비어 있으므로,
     *       복원한 Cart 는 조회·반품({@link #refundAll}) 용도로만 쓴다.</li>
     * </ul>
     * 넘겨받은 맵은 복사해서 쓰므로 호출한 쪽의 맵이 바뀌어도 영향이 없다.
     */
    public static Cart of(CartKey cartKey, Map<String, CartLine> lines) {
        return Cart.builder()
                .strCd(cartKey.getStrCd())
                .posNo(cartKey.getPosNo())
                .tranNo(cartKey.getTranNo())
                .lines(new LinkedHashMap<>(lines))
                .build();
    }

    /**
     * 다음 lineId를 반환한다 (예: 0001, 0002 ...).
     * 내부 시퀀스를 1 증가시키며, 4자리로 부족한 길이만큼 0패딩을 적용한다.
     */
    private String getNextLineId() {
        return String.format("%04d", seq.incrementAndGet());
    }

    /**
     * 조회 전용: 주어진 바코드의 ACTIVE lineId를 반환한다.
     * 맵을 변경하지 않으며, 없으면 null 을 반환한다.
     */
    public String getActiveLineId(String barcode) {
        return barcodeToLineMap.get(barcode);
    }

    /**
     * 조회 전용: 모든 라인(취소·반품 라인 포함)을 담은 순서대로, 수정할 수 없는 목록으로 돌려준다.
     * 라인 객체에는 lineId 가 없으므로 맵의 키(lineId)를 각 라인에 넣어서 돌려준다.
     * 응답 DTO·요약({@link CartSummary})·DB 저장이 모두 이 목록을 쓴다.
     */
    public List<CartLine> getLinesList() {
        return lines.entrySet().stream()
                .map(i -> i.getValue().withLineId(i.getKey())) // 여기서 lineId 주입
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 쓰기(보장)용: 주어진 바코드의 ACTIVE lineId를 "보장"하여 반환한다.
     * <ul>
     *   <li>키가 없으면 새 lineId를 생성해 매핑한다.</li>
     *   <li>키는 있는데 실제 lines 의 라인이 없거나,
     *       혹은 현재 라인이 CANCELLED 라면 새 lineId로 교체한다.</li>
     *   <li>정상이라면 기존 lineId를 그대로 유지한다.</li>
     * </ul>
     *
     * <p>여기서는 {@code Map.compute} 를 사용한다.
     *  - 참고 : compute는 항상 remapping 함수가 호출된다.
     *  remapping 함수에 인자로 전달 되어 계산된 값을 맵에 저장 하고 종료 된다. (remapping : 키에 대한 값을 동적으로 변경하는 것을 의미)
     *  remapping 함수가 {@code null}을 반환 하면, 맵에서 해당 키는 삭제됨.
     */
    public String ensureActiveLineId(String barcode) {
        return barcodeToLineMap.compute(barcode, (key, oldId) -> {
            // 1) 인덱스가 없으면 새 lineId 생성
            if (oldId == null) return getNextLineId();

            // 2) 인덱스는 있는데 실데이터가 없거나, 현재 라인이 취소 상태면 새 lineId 생성
            CartLine curCart = getCartLine(barcode); // 조회 전용(부수효과 없음)
            if (curCart == null || curCart.getItemStatus().equals(ItemStatus.CANCELLED)) {
                return getNextLineId();
            }

            // 3) 정상: 기존 lineId 유지
            return oldId;
        });
    }

    /**
     * 스캔/추가: 바코드가 동일한 NORMAL 상품이면 수량을 증가,
     * ACTIVE 라인이 없거나 비정상 상태면 새 라인을 만든다.
     *
     * <p>여기서는 {@code Map.merge} 를 사용한다.
     * - 키(lineId)가 없으면 value(cartLine)를 그대로 put.
     * - 키가 있으면 remappingFunction(old, new)를 호출하고 그 결과로 교체.
     * - remappingFunction이 null을 리턴하면 해당 키는 remove.</p>
     */
    public void addOrIncrease(CartLine cartLine) {
        String barcode = cartLine.getBarcode();
        // ACTIVE lineId를 "보장"하여 받는다(필요 시 새 lineId 생성)
        String lineId = ensureActiveLineId(barcode);

        lines.merge(
                lineId,
                cartLine,
                (oldLine, newLine) -> oldLine.increaseOf(newLine) // 불변 도메인: 새 객체를 반환해 교체
        );
    }

    /**
     * 수량을 "절대값"으로 설정한다 (0 이하 요청은 거부).
     * <ul>
     *   <li>ACTIVE 라인이 없으면 false 반환.</li>
     *   <li>ACTIVE 라인이 CANCELLED 상태면 예외(정책상 업데이트 불가). 취소하면 인덱스에서 빠지므로 실제로는 도달하지 않는다 —
     *       서비스는 false 를 받아 404 CART_LINE_NOT_FOUND 로 응답한다.</li>
     *   <li>성공 시 true 반환.</li>
     * </ul>
     *
     * <p>여기서는 {@code Map.compute} 를 사용한다.
     *  - compute는 항상 remapping 함수가 호출되고, 리턴값으로 값이 교체된다.
     *  - old가 null이면 예외(정책상 없는 라인 업데이트 금지).</p>
     */
    public boolean updatedItemQty(String barcode, int updatedQty) {
        String lineId = getActiveLineId(barcode); // 조회만: 부수효과 없음

        if (updatedQty <= 0 || lineId == null) return false;

        CartLine computeLine = lines.compute(lineId, (key, oldCartLine) -> {
            if (oldCartLine == null || oldCartLine.getItemStatus() == ItemStatus.CANCELLED) {
                throw new IllegalStateException("업데이트 실패");
            }

            return oldCartLine.updatedOf(updatedQty); // 불변 업데이트
        });

        return computeLine.getQty() == updatedQty;
    }

    /**
     * 조회 전용: 바코드에 해당하는 ACTIVE 라인을 반환한다.
     * <ul>
     *   <li>인덱스가 없거나 라인이 없으면 {@code CartLine.EMPTY} 반환.</li>
     *   <li>맵을 변경하지 않는다.</li>
     * </ul>
     *
     * <p>주의: 조회 메서드는 절대 인덱스/실데이터를 수정하지 않도록 유지한다.</p>
     */
    public CartLine getCartLine(String barcode) {
        return lines.getOrDefault(getActiveLineId(barcode), CartLine.EMPTY);
    }

    /**
     * 조회 전용: ACTIVE 라인의 수량을 반환한다.
     * 라인이 없으면 0을 반환하도록 {@code CartLine.EMPTY} 정책을 따른다.
     */
    public int getCartLineQty(String barcode) {
        return getCartLine(barcode).getQty();
    }

    /**
     * 취소 처리:
     * <ul>
     *   <li>ACTIVE 라인을 CANCELLED 상태로 교체한다(불변 업데이트).</li>
     *   <li>barcodeToLineMap 인덱스에서 해당 바코드를 제거하여, 이후 조회는 더 이상 이 라인을 ACTIVE로 보지 않게 한다.</li>
     * </ul>
     *
     * <p>여기서는 {@code Map.computeIfPresent} 를 사용한다.
     *  - 키가 있을 때만 remapping 함수가 호출된다.</p>
     */
    public boolean cancelCartLine(String barcode) {
        String lineId = getActiveLineId(barcode);
        if (lineId == null) return false; // ACTIVE 없음: no-op

        lines.computeIfPresent(lineId, (k, old) -> old.cancelledOf()); // 불변 업데이트로 CANCELLED로 교체
        barcodeToLineMap.remove(barcode); // barcodeToLineMap 인덱스에서 제거하여 ACTIVE 참조 해제

        return true;
    }

    /**
     * 반품 처리: 정상(NORMAL) 라인을 모두 반품(REFUNDED) 라인으로 바꾼다. 취소 라인은 그대로 둔다.
     * 반품 라인은 수량·금액이 음수가 되어({@link CartLine#refundOf()}), 저장 후 재고 반영 시 재고가 복원된다.
     * 부분 반품은 없다 — 반품은 항상 원거래 전체다 ({@code CheckoutServiceImpl.refundTransaction}).
     */
    public void refundAll() {
        lines.replaceAll((lineId, oldLine) ->
                oldLine.getItemStatus() == ItemStatus.NORMAL
                        ? oldLine.refundOf()
                        : oldLine
        );
    }

    /** 결제 대상 총수량: 정상(NORMAL) 라인 수량의 합. 0이면 결제할 상품이 없다는 뜻이다 (EMPTY_CART). */
    public int getTotalQtyInLines() {
        return getTotalQtyInLines(false);
    }

    /**
     * 상태별 총수량: {@code isRefunded}가 true 면 반품(REFUNDED) 라인, false 면 정상(NORMAL) 라인의 수량 합.
     * 취소(CANCELLED) 라인은 어느 쪽에도 포함되지 않는다.
     */
    public int getTotalQtyInLines(boolean isRefunded) {
        ItemStatus status = isRefunded ? ItemStatus.REFUNDED : ItemStatus.NORMAL;

        return lines.values().stream()
                .filter(i -> i.getItemStatus().equals(status))
                .mapToInt(i -> i.getQty())
                .sum()
                ;
    }

    /** 결제할 금액: 정상(NORMAL) 라인 금액의 합. 현금 결제의 청구액이 된다. */
    public BigDecimal getTotalAmountInLines() {
        return getTotalAmountInLines(false);
    }

    /**
     * 상태별 총금액: {@code isRefunded}가 true 면 반품 라인(음수 금액), false 면 정상 라인 금액의 합.
     * 취소 라인은 금액이 0이고 합계에서도 빠진다.
     */
    public BigDecimal getTotalAmountInLines(boolean isRefunded) {
        ItemStatus status = isRefunded ? ItemStatus.REFUNDED : ItemStatus.NORMAL;

        return lines.values().stream()
                .filter(i -> i.getItemStatus().equals(status))
                .map(i -> i.getAmount())
                .reduce(BigDecimal.valueOf(0),
                        (bigDecimalA, bigDecimalB) -> bigDecimalA.add(bigDecimalB)
                )
                ;
    }

}
