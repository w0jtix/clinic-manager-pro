package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.KsefCorrectionMatchType;
import com.clinic.clinicmanager.utils.KsefCorrectionMatcher;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KsefCorrectionMatchDTO {

    private KsefCorrectionMatchType matchType;
    private Long originalItemId;
    private boolean notProduct;
    private Long suggestedProductId;
    private Integer suggestedQuantity;
    private boolean rounded;
    private boolean productDeleted;

    public KsefCorrectionMatchDTO(KsefInvoiceItem correctionItem, KsefCorrectionMatcher.Match match) {
        this.matchType = match.type();
        if (!match.isResolved()) {
            return;
        }

        KsefInvoiceItem original = match.originalItem();
        this.originalItemId = original.getId();

        Product product = original.getProduct();
        if (product == null) {
            this.notProduct = true;
            this.suggestedQuantity = 0;
            return;
        }

        this.productDeleted = Boolean.TRUE.equals(product.getIsDeleted());
        this.suggestedProductId = productDeleted ? null : product.getId();

        KsefCorrectionMatcher.SuggestedQuantity quantity =
                KsefCorrectionMatcher.suggestQuantity(correctionItem.getQuantity(), original);
        this.suggestedQuantity = quantity.value();
        this.rounded = quantity.rounded();
    }
}
