import { useMemo, useState } from "react";
import { BonusProduct } from "../../models/employee";
import ProductBonusModal from "./ProductBonusModal";
import { TriangleAlert, TrendingDown, Crown } from "lucide-react";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductBonusChartProps {
  items: BonusProduct[];
  onProductUpdated?: () => void;
}

export function ProductBonusChart({
  items,
  onProductUpdated,
}: ProductBonusChartProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { isCompact } = useBreakpoint();

  const selected = useMemo(
    () => items.find((item) => item.productId === selectedId) ?? null,
    [items, selectedId]
  );

  const { sorted, maxAbs, bestId } = useMemo(() => {
    const s = [...items].sort((a, b) => b.totalBonus - a.totalBonus);
    const m = Math.max(...items.map((p) => Math.abs(p.totalBonus)), 1);
    const best = s.length > 0 && s[0].totalBonus > 0 ? s[0].productId : null;
    return { sorted: s, maxAbs: m, bestId: best };
  }, [items]);

  if (items.length === 0) {
    return (
      <div className="pv-empty text-align-center">
        Brak sprzedaży w tym miesiącu
      </div>
    );
  }

  return (
    <div className="pv-gr">
      <div className="pv-gr-key">
        <span>Produkt · wkład w premię</span>
        <span className="r">Marża</span>
        <span className="r">Premia</span>
      </div>
      {sorted.map((item) => {
        const missingPrice =
          item.noPurchaseHistory && !item.fallbackPurchasePriceUsed;
        const fallback =
          item.noPurchaseHistory && item.fallbackPurchasePriceUsed;
        const hasLoss = item.items.some((i) => i.margin < 0);

        const totalPurchase = item.items.reduce(
          (s, i) => s + i.purchaseNetPrice,
          0
        );
        const totalMargin = item.items.reduce((s, i) => s + i.margin, 0);
        const narzut =
          !missingPrice && totalPurchase > 0
            ? Math.round((totalMargin / totalPurchase) * 100)
            : null;

        const fillPct = (Math.abs(item.totalBonus) / maxAbs) * 100;
        const bonusZero = missingPrice || item.totalBonus === 0;
        const bonusNeg = !bonusZero && item.totalBonus < 0;

        return (
          <div className="pv-gr-item" key={item.productId}>
            <div
              className={`pv-gr-row align-items-center pointer ${missingPrice ? "muted" : ""}`}
              onClick={() => setSelectedId(item.productId)}
            >
              <div className="pv-gr-main flex-column">
                <div className="pv-gr-name-line flex align-items-center">
                  {item.productId === bestId && (
                    <Crown className="pv-cal-crown pc-y" />
                  )}
                  <span
                    className={`pv-gr-nm ${
                      item.productId === bestId ? "pc-y" : ""
                    }`}
                  >
                    {item.productName}
                  </span>
                  {!isCompact && (
                    <>
                      <span className="pv-gr-brand">{item.brandName}</span>
                      <span className="pv-gr-qty tnum">×{item.quantitySold}</span>
                    </>
                  )}
                  {fallback && (
                    <TriangleAlert
                      className="dl-icon"
                      style={{ color: "var(--yellow-clinicmanager)", flexShrink: 0 }}
                    />
                  )}
                  {missingPrice && (
                    <TriangleAlert
                      className="dl-icon"
                      style={{
                        color: "var(--color-finance-red)",
                        flexShrink: 0,
                      }}
                    />
                  )}
                  {hasLoss && (
                    <TrendingDown
                      className="dl-icon"
                      style={{
                        color: "var(--color-finance-red)",
                        flexShrink: 0,
                      }}
                    />
                  )}
                </div>
                {isCompact && (
                  <div className="pv-gr-sub flex align-items-center g-05">
                    <span className="pv-gr-brand">{item.brandName}</span>
                    <span className="pv-gr-qty tnum">×{item.quantitySold}</span>
                  </div>
                )}
                <div className="pv-gr-track relative">
                  {!missingPrice && (
                    <span
                      className={`pv-gr-fill absolute ${bonusNeg ? "neg" : "pos"}`}
                      style={{ width: `${fillPct}%` }}
                    />
                  )}
                </div>
              </div>
              {narzut == null ? (
                <span className="pv-gr-margin warnlabel">brak</span>
              ) : (
                <span className="pv-gr-margin tnum">
                  {narzut > 0 ? "+" : ""}
                  {narzut}%
                </span>
              )}
              <span
                className={`pv-gr-bonus tnum ${bonusNeg ? "neg" : ""} ${
                  bonusZero ? "zero" : ""
                }`}
              >
                {missingPrice
                  ? "-"
                  : `${item.totalBonus >= 0 ? "+ " : "− "}${formatAmount(
                      Math.abs(item.totalBonus)
                    )} zł`}
              </span>
            </div>
          </div>
        );
      })}
      {selected && (
        <ProductBonusModal
          product={selected}
          onClose={() => setSelectedId(null)}
          onProductUpdated={onProductUpdated}
        />
      )}
    </div>
  );
}

export default ProductBonusChart;
