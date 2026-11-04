import { ProductSupplySummary } from "../../models/supply-history";
import {
  StockBadge,
  STOCK_STATUS_CLASS,
  getStockStatus,
} from "../StockStatusTag";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductSupplyTilesProps {
  summary: ProductSupplySummary | null;
  lowStockThreshold?: number | null;
  className?: string;
}

const fmtSigned = (value: number): string =>
  `${value > 0 ? "+" : value < 0 ? "−" : ""}${Math.abs(value).toLocaleString("pl-PL")}`;

export function ProductSupplyTiles({
  summary,
  lowStockThreshold,
  className = "",
}: ProductSupplyTilesProps) {
  if (!summary) return null;

  const stockStatus = getStockStatus(summary.currentSupply, lowStockThreshold);

  return (
    <div className={`pd-tiles ${className}`}>
      <div
        className={`pd-tile stock ${STOCK_STATUS_CLASS[stockStatus]} g-05`}
      >
        <span className="flex align-items-center space-between g-05">
          <span className="pd-tile-label">Stan</span>
          <StockBadge
            supply={summary.currentSupply}
            lowStockThreshold={lowStockThreshold}
          />
        </span>
        <span className="pd-tile-value tnum">
          {summary.currentSupply}
          <small>szt.</small>
        </span>
      </div>
      <div className="pd-tile g-05">
        <span className="pd-tile-label">Przyjęto</span>
        <span className="pd-tile-value tnum c-f-green">
          {fmtSigned(summary.totalIn)}
        </span>
      </div>
      <div className="pd-tile g-05">
        <span className="pd-tile-label">Sprzedano</span>
        <span className="pd-tile-value tnum c-f-red">
          {fmtSigned(summary.totalSold)}
        </span>
      </div>
      <div className="pd-tile g-05">
        <span className="pd-tile-label">Zużyto</span>
        <span className="pd-tile-value tnum c-f-red">
          {fmtSigned(summary.totalUsed)}
        </span>
      </div>
      <div className="pd-tile g-05">
        <span className="pd-tile-label">Korekty stanu</span>
        <span className="pd-tile-value tnum c-blue">
          {fmtSigned(summary.totalAdjustments)}
        </span>
      </div>
      <div className="pd-tile g-05">
        <span className="pd-tile-label">Wartość zapasu</span>
        <span className="pd-tile-value tnum">
          {formatAmount(summary.stockValue)}
          <small>zł</small>
        </span>
      </div>
    </div>
  );
}

export default ProductSupplyTiles;
