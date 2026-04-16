package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.DTO.request.VoucherFilterDTO;

import java.util.List;

public interface VoucherService {

    VoucherDTO getVoucherById(Long id);

    List<VoucherDTO> getVouchers(VoucherFilterDTO filter);

    VoucherDTO createVoucher(VoucherDTO voucher);

    VoucherDTO updateVoucher(Long id, VoucherDTO voucher);

    void deleteVoucherById(Long id);

    Boolean hasSaleReference(Long voucherId);
}
