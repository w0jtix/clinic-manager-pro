package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KsefCorrectionUtilsTest {

    private KsefInvoice invoice() {
        return KsefInvoice.builder().build();
    }

    private KsefInvoiceItem item(Long id, String name, String gtin, Product product) {
        return KsefInvoiceItem.builder()
                .id(id)
                .ksefItemName(name)
                .ksefGtin(gtin)
                .product(product)
                .build();
    }

    private Product product(Long id) {
        return Product.builder().id(id).name("Product " + id).build();
    }

    @Test
    void applyParentCategory_leavesCorrectionUntouched_whenAlreadyBookedAsProducts() {
        KsefInvoice correction = invoice();
        correction.setCategory(ExpenseCategoryCode.PRODUCTS);
        correction.setAwaitingProductVerification(false);
        KsefInvoice parent = invoice();
        parent.setCategory("SERVICES");

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertEquals(ExpenseCategoryCode.PRODUCTS, correction.getCategory());
        assertFalse(correction.isAwaitingProductVerification());
    }

    @Test
    void applyParentCategory_inheritsResolvedCategory_andClearsAwaiting() {
        KsefInvoice correction = invoice();
        correction.setAwaitingProductVerification(true);
        KsefInvoice parent = invoice();
        parent.setCategory("SERVICES");

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertEquals("SERVICES", correction.getCategory());
        assertFalse(correction.isAwaitingProductVerification());
    }

    @Test
    void applyParentCategory_resetsToNull_whenParentHasNoCategoryYet() {
        KsefInvoice correction = invoice();
        correction.setCategory("EQUIPMENT");
        correction.setAwaitingProductVerification(true);
        KsefInvoice parent = invoice();
        parent.setCategory(null);

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getCategory());
        assertFalse(correction.isAwaitingProductVerification());
    }

    @Test
    void applyParentCategory_marksAwaitingAndCopiesProducts_whenParentIsProducts() {
        Product parentProduct = product(50L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", "5901234567890", parentProduct));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", "5901234567890", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getCategory());
        assertTrue(correction.isAwaitingProductVerification());
        assertSame(parentProduct, correction.getItems().getFirst().getProduct());
    }

    @Test
    void applyParentCategory_matchesByName_whenGtinBlank() {
        Product parentProduct = product(50L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem nawilżający", null, parentProduct));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem nawilżający", "", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertSame(parentProduct, correction.getItems().getFirst().getProduct());
    }

    @Test
    void applyParentCategory_prefersGtinMatchOverNameMatch() {
        Product byName = product(1L);
        Product byGtin = product(2L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", null, byName));
        parent.addItem(item(2L, "Inny", "5901234567890", byGtin));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", "5901234567890", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertSame(byGtin, correction.getItems().getFirst().getProduct());
    }

    @Test
    void applyParentCategory_leavesProductNull_whenParentItemHasNoProduct() {
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", "5901234567890", null));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", "5901234567890", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getItems().getFirst().getProduct());
        assertTrue(correction.isAwaitingProductVerification());
    }

    @Test
    void applyParentCategory_leavesProductNull_whenNoMatchingParentItem() {
        Product parentProduct = product(50L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", "111", parentProduct));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Zupełnie inny", "999", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getItems().getFirst().getProduct());
    }

    @Test
    void applyParentCategory_copiesProductByLineNumber_whenNamesRepeatWithDifferentProducts() {
        Product first = product(1L);
        Product second = product(2L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        KsefInvoiceItem p1 = item(1L, "Krem", null, first);
        p1.setLineNumber(1);
        KsefInvoiceItem p2 = item(2L, "Krem", null, second);
        p2.setLineNumber(2);
        parent.addItem(p1);
        parent.addItem(p2);

        KsefInvoice correction = invoice();
        KsefInvoiceItem k = item(10L, "Krem", null, null);
        k.setLineNumber(2);
        correction.addItem(k);

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertSame(second, k.getProduct());
    }

    @Test
    void applyParentCategory_leavesProductNull_whenMatchAmbiguous() {
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", null, product(1L)));
        parent.addItem(item(2L, "Krem", null, product(2L)));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", null, null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getItems().getFirst().getProduct());
        assertTrue(correction.isAwaitingProductVerification());
    }

    @Test
    void applyParentCategory_copiesProduct_whenRepeatedPositionsAreConsistent() {
        Product shared = product(1L);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", null, shared));
        parent.addItem(item(2L, "Krem", null, shared));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", null, null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertSame(shared, correction.getItems().getFirst().getProduct());
    }

    @Test
    void applyParentCategory_doesNotCopyDeletedProduct() {
        Product deleted = product(1L);
        deleted.setIsDeleted(true);
        KsefInvoice parent = invoice();
        parent.setCategory(ExpenseCategoryCode.PRODUCTS);
        parent.addItem(item(1L, "Krem", "590", deleted));

        KsefInvoice correction = invoice();
        correction.addItem(item(10L, "Krem", "590", null));

        KsefCorrectionUtils.applyParentCategory(correction, parent);

        assertNull(correction.getItems().getFirst().getProduct());
    }
}
