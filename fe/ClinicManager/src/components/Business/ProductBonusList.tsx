import { useState, useEffect } from "react";
import { BonusProduct } from "../../models/employee";
import ProductBonusContent from "./ProductBonusContent";
import { ChevronDown, TriangleAlert, TrendingDown } from "lucide-react";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductBonusListProps {
  items: BonusProduct[];
  onProductUpdated?: () => void;
}

export function ProductBonusList({
  items,
  onProductUpdated,
}: ProductBonusListProps) {
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  const toggleExpand = (productId: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  useEffect(() => {
    setExpandedIds(new Set());
  }, [items]);

  if (items.length === 0) {
    return <div className="pv-empty text-align-center">Brak sprzedaży w tym miesiącu</div>;
  }

  return (
    <div className="pv-prods">
      {items.map((item) => {
        const missingPrice = item.noPurchaseHistory && !item.fallbackPurchasePriceUsed;
        const fallback = item.noPurchaseHistory && item.fallbackPurchasePriceUsed;
        const hasLoss = item.items.some((i) => i.margin < 0);
        const state = missingPrice ? "err" : fallback ? "warn" : "";
        const expanded = expandedIds.has(item.productId);
        return (
          <div className={`pv-prod ${state}`} key={item.productId}>
            <div
              className="pv-prod-head align-items-center pointer"
              onClick={() => toggleExpand(item.productId)}
            >
              <ChevronDown className={`pv-chev dl-icon ${expanded ? "open" : ""}`} />
              <span className="pv-prod-name flex align-items-center">
                <span className="pv-prod-nm">{item.productName}</span>
                <span className="pv-prod-brand">{item.brandName}</span>
                {fallback && (
                  <TriangleAlert
                    className="dl-icon"
                    style={{ color: "var(--yellow-clinicmanager)", flexShrink: 0 }}
                  />
                )}
                {missingPrice && (
                  <TriangleAlert
                    className="dl-icon"
                    style={{ color: "var(--color-finance-red)", flexShrink: 0 }}
                  />
                )}
                {hasLoss && (
                  <TrendingDown
                    className="dl-icon"
                    style={{ color: "var(--color-finance-red)", flexShrink: 0 }}
                  />
                )}
              </span>
              <span className="pv-prod-qty text-align-center">×{item.quantitySold}</span>
              <span
                className={`pv-prod-bonus ${state} ${item.totalBonus < 0 ? "neg" : ""}`}
              >
                {item.totalBonus >= 0 ? "+ " : "− "}
                {formatAmount(Math.abs(item.totalBonus))} zł
              </span>
            </div>
            {expanded && (
              <ProductBonusContent
                product={item}
                onProductUpdated={onProductUpdated}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default ProductBonusList;
