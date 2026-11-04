package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.KsefCorrectionMatchType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class KsefCorrectionMatcher {

    private KsefCorrectionMatcher() {
    }

    public record Match(KsefCorrectionMatchType type, KsefInvoiceItem originalItem) {

        public static Match none() {
            return new Match(KsefCorrectionMatchType.NONE, null);
        }

        public boolean isResolved() {
            return type.isResolved();
        }
    }

    public record SuggestedQuantity(int value, boolean rounded) {
    }

    public static Match match(KsefInvoiceItem correctionItem, List<KsefInvoiceItem> originalItems) {
        if (correctionItem == null || originalItems == null || originalItems.isEmpty()) {
            return Match.none();
        }

        String uuid = correctionItem.getLineUuid();
        if (!isBlank(uuid)) {
            KsefInvoiceItem byUuid = originalItems.stream()
                    .filter(o -> uuid.equals(o.getLineUuid()))
                    .findFirst()
                    .orElse(null);
            if (byUuid != null) {
                return new Match(KsefCorrectionMatchType.UU_ID, byUuid);
            }
        }

        Integer lineNumber = correctionItem.getLineNumber();
        if (lineNumber != null) {
            KsefInvoiceItem byLine = originalItems.stream()
                    .filter(o -> lineNumber.equals(o.getLineNumber()) && sameGoods(correctionItem, o))
                    .findFirst()
                    .orElse(null);
            if (byLine != null) {
                return new Match(KsefCorrectionMatchType.LINE, byLine);
            }
        }

        String gtin = correctionItem.getKsefGtin();
        if (!isBlank(gtin)) {
            Match byGtin = byUniqueCandidate(originalItems, o -> gtin.equals(o.getKsefGtin()),
                    KsefCorrectionMatchType.GTIN);
            if (byGtin != null) {
                return byGtin;
            }
        }

        String name = correctionItem.getKsefItemName();
        if (name != null) {
            Match byName = byUniqueCandidate(originalItems, o -> name.equals(o.getKsefItemName()),
                    KsefCorrectionMatchType.NAME);
            if (byName != null) {
                return byName;
            }
        }

        return Match.none();
    }

    public static SuggestedQuantity suggestQuantity(BigDecimal correctionDelta, KsefInvoiceItem originalItem) {
        BigDecimal delta = correctionDelta != null ? correctionDelta : BigDecimal.ZERO;
        Integer declared = originalItem != null ? originalItem.getDeclaredQuantity() : null;
        BigDecimal originalQuantity = originalItem != null ? originalItem.getQuantity() : null;

        if (declared != null && originalQuantity != null && originalQuantity.signum() != 0) {
            BigDecimal numerator = BigDecimal.valueOf(declared).multiply(delta.abs());
            BigDecimal divisor = originalQuantity.abs();
            BigDecimal magnitude = numerator.divide(divisor, 0, RoundingMode.HALF_UP);
            boolean rounded = numerator.remainder(divisor).signum() != 0;
            int value = delta.signum() < 0 ? -magnitude.intValue() : magnitude.intValue();
            return new SuggestedQuantity(value, rounded);
        }

        BigDecimal roundedDelta = delta.setScale(0, RoundingMode.HALF_UP);
        boolean rounded = delta.remainder(BigDecimal.ONE).signum() != 0;
        return new SuggestedQuantity(roundedDelta.intValue(), rounded);
    }

    private static Match byUniqueCandidate(List<KsefInvoiceItem> originalItems,
                                           Predicate<KsefInvoiceItem> filter,
                                           KsefCorrectionMatchType uniqueType) {
        List<KsefInvoiceItem> candidates = originalItems.stream().filter(filter).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return new Match(uniqueType, candidates.getFirst());
        }
        return isConsistent(candidates)
                ? new Match(KsefCorrectionMatchType.AMBIGUOUS_CONSISTENT, candidates.getFirst())
                : new Match(KsefCorrectionMatchType.AMBIGUOUS, null);
    }

    private static boolean isConsistent(List<KsefInvoiceItem> candidates) {
        KsefInvoiceItem first = candidates.getFirst();
        Long productId = productId(first.getProduct());
        return candidates.stream().allMatch(c ->
                Objects.equals(productId, productId(c.getProduct())) && sameRatio(first, c));
    }

    private static boolean sameRatio(KsefInvoiceItem a, KsefInvoiceItem b) {
        BigDecimal declaredA = a.getDeclaredQuantity() != null ? BigDecimal.valueOf(a.getDeclaredQuantity()) : null;
        BigDecimal declaredB = b.getDeclaredQuantity() != null ? BigDecimal.valueOf(b.getDeclaredQuantity()) : null;
        BigDecimal quantityA = a.getQuantity() != null ? a.getQuantity().abs() : null;
        BigDecimal quantityB = b.getQuantity() != null ? b.getQuantity().abs() : null;

        boolean hasRatioA = declaredA != null && quantityA != null && quantityA.signum() != 0;
        boolean hasRatioB = declaredB != null && quantityB != null && quantityB.signum() != 0;
        if (!hasRatioA || !hasRatioB) {
            return !hasRatioA && !hasRatioB;
        }
        return declaredA.multiply(quantityB).compareTo(declaredB.multiply(quantityA)) == 0;
    }

    private static boolean sameGoods(KsefInvoiceItem a, KsefInvoiceItem b) {
        if (!isBlank(a.getKsefGtin()) && !isBlank(b.getKsefGtin())) {
            return a.getKsefGtin().equals(b.getKsefGtin());
        }
        return a.getKsefItemName() != null && a.getKsefItemName().equals(b.getKsefItemName());
    }

    private static Long productId(Product product) {
        return product != null ? product.getId() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
