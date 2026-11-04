import { Cross } from "lucide-react";
import { VatRate, getVatRateDisplay } from "../models/vatrate";

const MEDICAL_HINT = "Produkt medyczny - VAT 8%";

export const isMedicalVat = (vatRate?: VatRate | null): boolean =>
  vatRate === VatRate.VAT_8;

export interface ProductVatProps {
  vatRate?: VatRate | null;
  className?: string;
}

export function MedicalMark({ vatRate, className = "" }: ProductVatProps) {
  if (!isMedicalVat(vatRate)) return null;

  return (
    <span
      className={`pvt-mark flex align-items-center ${className}`}
      title={MEDICAL_HINT}
    >
      <Cross className="dl-icon pvt-icon" />
    </span>
  );
}

export function ProductVatTag({ vatRate, className = "" }: ProductVatProps) {
  if (!vatRate) return null;

  return (
    <span
      className={`pvt align-self-end ${
        isMedicalVat(vatRate) ? "medical" : "dl-dim"
      } ${className}`}
      title={isMedicalVat(vatRate) ? MEDICAL_HINT : undefined}
    >
      {getVatRateDisplay(vatRate)} VAT
    </span>
  );
}

export default ProductVatTag;
