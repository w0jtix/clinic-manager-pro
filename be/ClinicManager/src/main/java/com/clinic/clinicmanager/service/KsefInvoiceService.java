package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefInvoiceDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionRequestDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsRequestDTO;
import org.springframework.data.domain.Page;

public interface KsefInvoiceService {

    Page<KsefInvoiceDTO> getInvoices(KsefInvoiceFilterDTO filter, int page, int size);

    KsefInvoiceDTO getInvoiceById(Long id);

    KsefInvoiceDTO togglePaidStatus(Long id);

    KsefInvoiceDTO toggleProductInvoice(Long id);

    KsefInvoiceDTO verifyProducts(Long id, KsefVerifyProductsRequestDTO request);

    StockChangePreviewDTO previewCorrectionBooking(Long id, KsefBookCorrectionRequestDTO request);

    KsefInvoiceDTO bookCorrection(Long id, KsefBookCorrectionRequestDTO request);

    KsefInvoiceDTO setCategory(Long id, String category, boolean rememberForSeller);
}
