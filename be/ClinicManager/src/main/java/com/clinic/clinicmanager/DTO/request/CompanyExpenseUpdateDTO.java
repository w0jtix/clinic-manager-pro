package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.DTO.ExpenseSellerDTO;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyExpenseUpdateDTO {

    private LocalDate expenseDate;
    private String source;
    private String category;
    private ExpenseSellerDTO seller;
    private String invoiceNumber;
    private ExpenseCurrency currency;
}
