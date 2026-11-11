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
 * Cart 도메인 모델
 *
 * <h3>핵심 불변식(Invariants)</h3>
 * <ul>
 *   <li>바코드(barcode) 당 ACTIVE(정상) 라인은 0개 또는 1개만 존재한다.</li>
 *   <li>취소된 라인(CANCELLED)은 {@code lines} 에 남겨두되, 인덱스 {@code barcodeToLineMap}에서는 제거한다.</li>
 *   <li>{@code barcodeToLineMap} 은 항상 "바코드 → ACTIVE lineId" 만을 가리킨다(상태 문자열 저장 금지).</li>
 * </ul>
 *
 * <h3>자료구조</h3>
 * <ul>
 *   <li>{@code lines} : lineId → CartLine (실데이터 저장소)</li>
 *   <li>{@code barcodeToLineMap} : barcode → ACTIVE lineId (조회 인덱스)</li>
 * </ul>
 *
 * <h3>메서드 사용 가이드</h3>
 * <ul>
 *   <li>{@link #getActiveLineId(String)} : 조회 전용(맵 변경 없음)</li>
 *   <li>{@link #ensureActiveLineId(String)} : 쓰기 전용(없거나 잘못된 경우 새 lineId 생성)</li>
 *   <li>{@link #addOrIncrease(CartLine)} : 스캔/추가. 없으면 생성, 있으면 수량 증가(merge)</li>
 *   <li>{@link #updatedItemQty(String, int)} : 수량을 "절대값"으로 설정(compute)</li>
 *   <li>{@link #cancelCartLine(String)} : ACTIVE 라인을 취소 상태로 전환하고 인덱스에서 제거</li>
 * </ul>
 *
 * <h3>주의</h3>
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
    private final String strCd;
    private final String posNo;
    private final String tranNo;

    // 실데이터: lineId → CartLine
    // 빌더 패턴 제거 : 항상 내부에서 초기화 되도록
    @Getter(AccessLevel.NONE)
    private final Map<String, CartLine> lines;

    // 조회 인덱스: barcode → ACTIVE lineId
    @Builder.Default
    private final Map<String, String> barcodeToLineMap = new HashMap<>();

    // 시퀀스 (카트 인스턴스 단위 증가) — lineId 생성에 사용
    @Builder.Default
    private final AtomicInteger seq = new AtomicInteger(0);

    // builder 메소드 사용해 Cart 모델에 값 설정 하는 정적 팩토리 메소드 (Cart 생성 책임을 도메인이 가짐)
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
     *   <li>ACTIVE 라인이 CANCELLED 상태면 예외(정책상 업데이트 불가).</li>
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

    public void refundAll() {
        lines.replaceAll((lineId, oldLine) ->
                oldLine.getItemStatus() == ItemStatus.NORMAL
                        ? oldLine.refundOf()
                        : oldLine
        );
    }

    public int getTotalQtyInLines() {
        return getTotalQtyInLines(false);
    }

    public int getTotalQtyInLines(boolean isRefunded) {
        ItemStatus status = isRefunded ? ItemStatus.REFUNDED : ItemStatus.NORMAL;

        return lines.values().stream()
                .filter(i -> i.getItemStatus().equals(status))
                .mapToInt(i -> i.getQty())
                .sum()
                ;
    }

    public BigDecimal getTotalAmountInLines() {
        return getTotalAmountInLines(false);
    }

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
