const LOCALE = "pl-PL";

export const formatNumber = (value: number): string =>
  value.toLocaleString(LOCALE);

export const formatAmount = (value: number | null | undefined): string =>
  (value ?? 0).toLocaleString(LOCALE, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

export const formatCurrency = (value: number | null | undefined): string =>
  `${formatAmount(value)} zł`;

export const formatWholeAmount = (value: number): string =>
  Math.round(value).toLocaleString(LOCALE);

export const formatWholeCurrency = (value: number): string =>
  `${formatWholeAmount(value)} zł`;
