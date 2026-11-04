package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.model.constants.VatRate;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int MONEY_SCALE = 2;
    public static final int PRICE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(MONEY_SCALE, ROUNDING);
    }

    public static BigDecimal price(BigDecimal value) {
        return nz(value).setScale(PRICE_SCALE, ROUNDING);
    }

    public static BigDecimal vatPercent(VatRate vatRate) {
        if (vatRate == null || !vatRate.isNumeric()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(vatRate.getRate());
    }

    public static BigDecimal vatFromGross(BigDecimal gross, VatRate vatRate) {
        BigDecimal rate = vatPercent(vatRate);
        if (rate.signum() == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
        }
        return nz(gross)
                .multiply(rate)
                .divide(HUNDRED.add(rate), MONEY_SCALE, ROUNDING);
    }
}
