package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;

/**
 * Shared logic for a correction invoice (KOR) inheriting its category from the original invoice.
 * A correction never holds its own category - it is always re-stamped with the parent's state:
 * <ul>
 *   <li>parent PRODUCTS → copy products from the original positions and mark the correction as
 *       awaiting booking (awaiting=true, category=null);</li>
 *   <li>parent with another (resolved) category → the correction inherits it (awaiting=false);</li>
 *   <li>parent without a category → the correction waits for the parent to be categorized
 *       (category=null, awaiting=false).</li>
 * </ul>
 * A correction that is already booked as products (category=PRODUCTS) is left untouched.
 */
public final class KsefCorrectionUtils {

    private KsefCorrectionUtils() {
    }

    public static void applyParentCategory(KsefInvoice correction, KsefInvoice parent) {
        if (ExpenseCategoryCode.PRODUCTS.equals(correction.getCategory())) {
            return;
        }

        String parentCategory = parent.getCategory();
        if (ExpenseCategoryCode.PRODUCTS.equals(parentCategory)) {
            copyProducts(correction, parent);
            correction.setAwaitingProductVerification(true);
            correction.setCategory(null);
        } else if (parentCategory != null) {
            correction.setCategory(parentCategory);
            correction.setAwaitingProductVerification(false);
        } else {
            correction.setCategory(null);
            correction.setAwaitingProductVerification(false);
        }
    }

    private static void copyProducts(KsefInvoice correction, KsefInvoice parent) {
        for (KsefInvoiceItem item : correction.getItems()) {
            KsefCorrectionMatcher.Match match = KsefCorrectionMatcher.match(item, parent.getItems());
            if (!match.isResolved()) continue;

            Product product = match.originalItem().getProduct();
            if (product != null && !Boolean.TRUE.equals(product.getIsDeleted())) {
                item.setProduct(product);
            }
        }
    }
}
