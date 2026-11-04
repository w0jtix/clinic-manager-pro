package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;

public interface ProductPdfService {

    /**
     * Generates an inventory-status PDF report.
     * Products are grouped by category, each category has its own table.
     *
     * @param filter filters narrowing the product list (may be null for all products)
     * @return byte array representing the PDF document
     */
    byte[] generateInventoryReport(ProductFilterDTO filter);
}
