import { CircleCheck, CircleX, TriangleAlert } from "lucide-react";
import { StockStatus } from "../models/product";

const DEFAULT_LOW_STOCK_THRESHOLD = 1;

export const getStockStatus = (
  supply: number,
  lowStockThreshold?: number | null
): StockStatus => {
  if (supply <= 0) return StockStatus.OUT_OF_STOCK;
  const threshold = lowStockThreshold ?? DEFAULT_LOW_STOCK_THRESHOLD;
  return supply <= threshold ? StockStatus.LOW_STOCK : StockStatus.OK;
};

export const STOCK_STATUS_LABELS: Record<StockStatus, string> = {
  [StockStatus.OK]: "OK",
  [StockStatus.LOW_STOCK]: "NISKI",
  [StockStatus.OUT_OF_STOCK]: "BRAK",
};

export const STOCK_STATUS_HINTS: Record<StockStatus, string> = {
  [StockStatus.OK]: "Stan wystarczający",
  [StockStatus.LOW_STOCK]: "Niski stan magazynowy",
  [StockStatus.OUT_OF_STOCK]: "Brak na stanie",
};

export const STOCK_STATUS_CLASS: Record<StockStatus, string> = {
  [StockStatus.OK]: "ok",
  [StockStatus.LOW_STOCK]: "low",
  [StockStatus.OUT_OF_STOCK]: "out",
};

export interface StockStatusIconProps {
  status: StockStatus;
  className?: string;
}

export function StockStatusIcon({ status, className = "" }: StockStatusIconProps) {
  if (status === StockStatus.OK) {
    return <CircleCheck className={`dl-icon ${className}`} />;
  }
  if (status === StockStatus.LOW_STOCK) {
    return <TriangleAlert className={`dl-icon ${className}`} />;
  }
  return <CircleX className={`dl-icon ${className}`} />;
}

export interface StockBadgeProps {
  supply: number;
  lowStockThreshold?: number | null;
  className?: string;
}

export function StockBadge({
  supply,
  lowStockThreshold,
  className = "",
}: StockBadgeProps) {
  const status = getStockStatus(supply, lowStockThreshold);

  return (
    <span
      className={`sb-badge flex align-items-center g-025 ${STOCK_STATUS_CLASS[status]} ${className}`}
      title={STOCK_STATUS_HINTS[status]}
    >
      <StockStatusIcon status={status} />
      <span className="sb-badge-label">{STOCK_STATUS_LABELS[status]}</span>
    </span>
  );
}

export default StockBadge;
