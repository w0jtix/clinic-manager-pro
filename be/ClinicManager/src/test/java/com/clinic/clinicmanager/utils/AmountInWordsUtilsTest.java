package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "0.00, zero PLN zero gr",
            "1.01, jeden PLN jeden gr",
            "12.00, dwanaście PLN zero gr",
            "140.00, sto czterdzieści PLN zero gr",
            "129.63, sto dwadzieścia dziewięć PLN sześćdziesiąt trzy gr",
            "1234.56, tysiąc dwieście trzydzieści cztery PLN pięćdziesiąt sześć gr"
    })
    void amountInWords_convertsAmountToPolishWords(String amount, String expected) {
        assertThat(AmountInWordsUtils.amountInWords(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    void amountInWords_treatsNullAsZero() {
        assertThat(AmountInWordsUtils.amountInWords(null)).isEqualTo("zero PLN zero gr");
    }

    @Test
    void amountInWords_roundsToTwoDecimalPlaces() {
        assertThat(AmountInWordsUtils.amountInWords(new BigDecimal("10.005")))
                .isEqualTo("dziesięć PLN jeden gr");
    }

    @ParameterizedTest
    @CsvSource({
            "11, jedenaście",
            "15, piętnaście",
            "19, dziewiętnaście",
            "20, dwadzieścia",
            "21, dwadzieścia jeden",
            "100, sto",
            "500, pięćset",
            "999, dziewięćset dziewięćdziesiąt dziewięć",
            "1000, tysiąc",
            "2000, dwa tysiące",
            "5000, pięć tysięcy",
            "12000, dwanaście tysięcy",
            "22000, dwadzieścia dwa tysiące",
            "1000000, milion"
    })
    void numberToWords_handlesTeensAndPluralForms(long number, String expected) {
        assertThat(AmountInWordsUtils.numberToWords(number)).isEqualTo(expected);
    }
}
