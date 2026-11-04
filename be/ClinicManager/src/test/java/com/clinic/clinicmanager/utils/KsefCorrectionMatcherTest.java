package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.DTO.KsefCorrectionMatchDTO;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.KsefCorrectionMatchType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KsefCorrectionMatcherTest {

    private KsefInvoiceItem item(Long id, String name, String gtin, Integer line, String uuid) {
        return KsefInvoiceItem.builder()
                .id(id)
                .ksefItemName(name)
                .ksefGtin(gtin)
                .lineNumber(line)
                .lineUuid(uuid)
                .build();
    }

    private KsefInvoiceItem original(Long id, String name, String quantity, Integer declared, Product product) {
        return KsefInvoiceItem.builder()
                .id(id)
                .ksefItemName(name)
                .quantity(new BigDecimal(quantity))
                .declaredQuantity(declared)
                .product(product)
                .build();
    }

    private KsefInvoiceItem correction(String name, String delta) {
        return KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName(name)
                .quantity(delta != null ? new BigDecimal(delta) : null)
                .build();
    }

    private Product product(Long id) {
        return Product.builder().id(id).name("Product " + id).build();
    }

    @Test
    void match_returnsUuId_whenLineUuidsEqual_evenIfNameAndLineDiffer() {
        KsefInvoiceItem target = item(10L, "Krem po zmianie", null, 7, "uuid-1");
        KsefInvoiceItem other = item(1L, "Krem po zmianie", null, 7, "uuid-2");
        KsefInvoiceItem byUuid = item(2L, "Krem", null, 1, "uuid-1");

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(target, List.of(other, byUuid));

        assertEquals(KsefCorrectionMatchType.UU_ID, match.type());
        assertSame(byUuid, match.originalItem());
    }

    @Test
    void match_returnsLine_whenSameLineNumberAndSameName() {
        KsefInvoiceItem first = item(1L, "Krem", null, 1, null);
        KsefInvoiceItem second = item(2L, "Krem", null, 2, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem", null, 2, null),
                List.of(first, second));

        assertEquals(KsefCorrectionMatchType.LINE, match.type());
        assertSame(second, match.originalItem());
    }

    @Test
    void match_returnsLine_whenSameLineNumberAndSameGtin_despiteDifferentName() {
        KsefInvoiceItem original = item(1L, "Krem 50ml", "590", 3, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem 50 ml", "590", 3, null),
                List.of(original));

        assertEquals(KsefCorrectionMatchType.LINE, match.type());
    }

    @Test
    void match_doesNotUseLine_whenLineNumberEqualButGoodsDiffer() {
        KsefInvoiceItem sameLineOtherGoods = item(1L, "Balsam", null, 1, null);
        KsefInvoiceItem byName = item(2L, "Krem", null, 2, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem", null, 1, null),
                List.of(sameLineOtherGoods, byName));

        assertEquals(KsefCorrectionMatchType.NAME, match.type());
        assertSame(byName, match.originalItem());
    }

    @Test
    void match_doesNotUseLine_whenGtinsDiffer_evenIfNamesEqual() {
        KsefInvoiceItem original = item(1L, "Krem", "111", 1, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem", "222", 1, null),
                List.of(original));

        assertEquals(KsefCorrectionMatchType.NAME, match.type());
    }

    @Test
    void match_returnsGtin_whenExactlyOneCandidate_inDifferentRowOrder() {
        KsefInvoiceItem a = item(1L, "Balsam", "111", 1, null);
        KsefInvoiceItem b = item(2L, "Krem", "222", 2, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem nowy", "222", null, null),
                List.of(a, b));

        assertEquals(KsefCorrectionMatchType.GTIN, match.type());
        assertSame(b, match.originalItem());
    }

    @Test
    void match_returnsName_whenGtinBlankAndExactlyOneNameCandidate() {
        KsefInvoiceItem a = item(1L, "Krem", null, null, null);
        KsefInvoiceItem b = item(2L, "Balsam", null, null, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Balsam", " ", null, null),
                List.of(a, b));

        assertEquals(KsefCorrectionMatchType.NAME, match.type());
        assertSame(b, match.originalItem());
    }

    @Test
    void match_fallsBackToName_whenGtinHasNoCandidates() {
        KsefInvoiceItem a = item(1L, "Krem", null, null, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem", "999", null, null),
                List.of(a));

        assertEquals(KsefCorrectionMatchType.NAME, match.type());
    }

    @Test
    void match_returnsAmbiguousConsistent_whenRepeatedPositionsShareProductAndRatio() {
        Product product = product(5L);
        KsefInvoiceItem a = original(1L, "Krem", "2", 20, product);
        KsefInvoiceItem b = original(2L, "Krem", "1", 10, product);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(correction("Krem", "-1"), List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS_CONSISTENT, match.type());
        assertSame(a, match.originalItem());
        assertTrue(match.isResolved());
    }

    @Test
    void match_returnsAmbiguousConsistent_whenRepeatedPositionsHaveNoProductAtAll() {
        KsefInvoiceItem a = original(1L, "Dostawa", "1", null, null);
        KsefInvoiceItem b = original(2L, "Dostawa", "1", null, null);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(correction("Dostawa", "-1"), List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS_CONSISTENT, match.type());
    }

    @Test
    void match_returnsAmbiguous_whenRepeatedPositionsHaveDifferentProducts() {
        KsefInvoiceItem a = original(1L, "Krem", "1", 10, product(5L));
        KsefInvoiceItem b = original(2L, "Krem", "1", 10, product(6L));

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(correction("Krem", "-1"), List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS, match.type());
        assertNull(match.originalItem());
        assertFalse(match.isResolved());
    }

    @Test
    void match_returnsAmbiguous_whenRepeatedPositionsHaveDifferentRatio() {
        Product product = product(5L);
        KsefInvoiceItem a = original(1L, "Krem", "1", 10, product);
        KsefInvoiceItem b = original(2L, "Krem", "1", 12, product);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(correction("Krem", "-1"), List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS, match.type());
    }

    @Test
    void match_returnsAmbiguous_whenOnlyOneRepeatedPositionHasDeclaredQuantity() {
        Product product = product(5L);
        KsefInvoiceItem a = original(1L, "Krem", "1", 10, product);
        KsefInvoiceItem b = original(2L, "Krem", "1", null, product);

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(correction("Krem", "-1"), List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS, match.type());
    }

    @Test
    void match_returnsAmbiguous_forRepeatedGtin_withoutFallingBackToName() {
        KsefInvoiceItem a = item(1L, "Krem", "590", null, null);
        a.setProduct(product(5L));
        KsefInvoiceItem b = item(2L, "Balsam", "590", null, null);
        b.setProduct(product(6L));

        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Krem", "590", null, null),
                List.of(a, b));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS, match.type());
    }

    @Test
    void match_returnsNone_whenNoCandidate() {
        KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item(10L, "Inny", "999", 9, "x"),
                List.of(item(1L, "Krem", "111", 1, "y")));

        assertEquals(KsefCorrectionMatchType.NONE, match.type());
        assertNull(match.originalItem());
    }

    @Test
    void match_returnsNone_whenOriginalItemsEmptyOrNull() {
        assertEquals(KsefCorrectionMatchType.NONE,
                KsefCorrectionMatcher.match(item(10L, "Krem", null, null, null), List.of()).type());
        assertEquals(KsefCorrectionMatchType.NONE,
                KsefCorrectionMatcher.match(item(10L, "Krem", null, null, null), null).type());
    }

    @Test
    void suggestQuantity_scalesDeclaredByKsefQuantity_andKeepsDeltaSign() {
        KsefCorrectionMatcher.SuggestedQuantity result = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-1"), original(1L, "Krem", "2", 20, product(5L)));

        assertEquals(-10, result.value());
        assertFalse(result.rounded());
    }

    @Test
    void suggestQuantity_keepsPositiveSign_forUpwardCorrection() {
        KsefCorrectionMatcher.SuggestedQuantity result = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("3"), original(1L, "Krem", "1", 12, product(5L)));

        assertEquals(36, result.value());
        assertFalse(result.rounded());
    }

    @Test
    void suggestQuantity_roundsHalfUp_andFlagsRounded_whenScaledResultNotInteger() {
        KsefCorrectionMatcher.SuggestedQuantity result = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-1"), original(1L, "Krem", "4", 10, product(5L)));

        assertEquals(-3, result.value());
        assertTrue(result.rounded());
    }

    @Test
    void suggestQuantity_roundsDown_whenFractionBelowHalf() {
        KsefCorrectionMatcher.SuggestedQuantity result = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-1"), original(1L, "Krem", "3", 10, product(5L)));

        assertEquals(-3, result.value());
        assertTrue(result.rounded());
    }

    @Test
    void suggestQuantity_usesRoundedDelta_whenDeclaredMissing() {
        KsefCorrectionMatcher.SuggestedQuantity exact = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-2.000"), original(1L, "Krem", "5", null, product(5L)));
        KsefCorrectionMatcher.SuggestedQuantity fractional = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-2.5"), original(1L, "Krem", "5", null, product(5L)));

        assertEquals(-2, exact.value());
        assertFalse(exact.rounded());
        assertEquals(-3, fractional.value());
        assertTrue(fractional.rounded());
    }

    @Test
    void suggestQuantity_usesRoundedDelta_whenOriginalQuantityZero() {
        KsefCorrectionMatcher.SuggestedQuantity result = KsefCorrectionMatcher.suggestQuantity(
                new BigDecimal("-1"), original(1L, "Krem", "0", 10, product(5L)));

        assertEquals(-1, result.value());
        assertFalse(result.rounded());
    }

    @Test
    void matchDto_suggestsProductAndScaledQuantity_forResolvedMatch() {
        KsefInvoiceItem originalItem = original(12L, "Krem", "2", 20, product(5L));
        KsefInvoiceItem correctionItem = correction("Krem", "-1");

        KsefCorrectionMatchDTO dto = new KsefCorrectionMatchDTO(correctionItem,
                KsefCorrectionMatcher.match(correctionItem, List.of(originalItem)));

        assertEquals(KsefCorrectionMatchType.NAME, dto.getMatchType());
        assertEquals(12L, dto.getOriginalItemId());
        assertEquals(5L, dto.getSuggestedProductId());
        assertEquals(-10, dto.getSuggestedQuantity());
        assertFalse(dto.isNotProduct());
        assertFalse(dto.isRounded());
        assertFalse(dto.isProductDeleted());
    }

    @Test
    void matchDto_marksNotProduct_withZeroQuantity_whenOriginalHasNoProduct() {
        KsefInvoiceItem originalItem = original(12L, "Dostawa", "1", null, null);
        KsefInvoiceItem correctionItem = correction("Dostawa", "-1");

        KsefCorrectionMatchDTO dto = new KsefCorrectionMatchDTO(correctionItem,
                KsefCorrectionMatcher.match(correctionItem, List.of(originalItem)));

        assertTrue(dto.isNotProduct());
        assertEquals(12L, dto.getOriginalItemId());
        assertNull(dto.getSuggestedProductId());
        assertEquals(0, dto.getSuggestedQuantity());
    }

    @Test
    void matchDto_hidesSuggestedProduct_whenOriginalProductDeleted() {
        Product deleted = product(5L);
        deleted.setIsDeleted(true);
        KsefInvoiceItem originalItem = original(12L, "Krem", "1", 10, deleted);
        KsefInvoiceItem correctionItem = correction("Krem", "-1");

        KsefCorrectionMatchDTO dto = new KsefCorrectionMatchDTO(correctionItem,
                KsefCorrectionMatcher.match(correctionItem, List.of(originalItem)));

        assertTrue(dto.isProductDeleted());
        assertNull(dto.getSuggestedProductId());
        assertEquals(12L, dto.getOriginalItemId());
        assertEquals(-10, dto.getSuggestedQuantity());
    }

    @Test
    void matchDto_hasNoSuggestion_forAmbiguousMatch() {
        KsefInvoiceItem a = original(1L, "Krem", "1", 10, product(5L));
        KsefInvoiceItem b = original(2L, "Krem", "1", 10, product(6L));
        KsefInvoiceItem correctionItem = correction("Krem", "-1");

        KsefCorrectionMatchDTO dto = new KsefCorrectionMatchDTO(correctionItem,
                KsefCorrectionMatcher.match(correctionItem, List.of(a, b)));

        assertEquals(KsefCorrectionMatchType.AMBIGUOUS, dto.getMatchType());
        assertNull(dto.getOriginalItemId());
        assertNull(dto.getSuggestedProductId());
        assertNull(dto.getSuggestedQuantity());
        assertFalse(dto.isNotProduct());
    }
}
