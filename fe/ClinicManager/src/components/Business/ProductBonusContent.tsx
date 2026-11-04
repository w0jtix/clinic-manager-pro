import React, { useState } from "react";
import { BonusProduct, BonusProductItem } from "../../models/employee";
import ActionButton from "../ActionButton";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import ProductModal from "../Popups/ProductModal";
import { Eye, TriangleAlert } from "lucide-react";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductBonusContentProps {
  product: BonusProduct;
  onProductUpdated?: () => void;
}

export function ProductBonusContent({
  product,
  onProductUpdated,
}: ProductBonusContentProps) {
  const { noPurchaseHistory, fallbackPurchasePriceUsed } = product;
  const missingPrice = noPurchaseHistory && !fallbackPurchasePriceUsed;
  const [previewVisitId, setPreviewVisitId] = useState<number | null>(null);
  const [isFillPriceOpen, setIsFillPriceOpen] = useState<boolean>(false);

  return (
    <div className="pv-prod-body">
      <div className="pv-unit-head align-items-center g-05">
        <span></span>
        <span className="r text-align-end">Zakup netto</span>
        <span className="r text-align-end">Sprzedaż netto</span>
        <span className="r text-align-end">Marża</span>
        <span className="r text-align-end">Premia</span>
      </div>
      {product.items.map((item: BonusProductItem, index) => (
        <div className="pv-unit align-items-center" key={index}>
          <ActionButton
            iconTitle="Podgląd Wizyty sprzedaży"
            disableText={true}
            disableBorder={true}
            onClick={(e: React.MouseEvent) => {
              e.stopPropagation();
              setPreviewVisitId(item.visitId);
            }}
          >
            <Eye className="dl-icon" />
          </ActionButton>
          {missingPrice ? (
            <span className="r flex align-items-center justify-end g-05">
              {index === 0 ? (
                <ActionButton
                  text="Uzupełnij"
                  iconTitle="Uzupełnij cenę zakupu produktu"
                  onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    setIsFillPriceOpen(true);
                  }}
                  className="pv-fill-price"
                />
              ) : (
                <TriangleAlert
                  className="dl-icon"
                  style={{ color: "var(--color-finance-red)" }}
                />
              )}
            </span>
          ) : (
            <span className={`r text-align-end znet ${fallbackPurchasePriceUsed ? "fallback" : ""}`}>
              {formatAmount(item.purchaseNetPrice)}
            </span>
          )}
          <span className="r text-align-end snet">{formatAmount(item.saleNetPrice)}</span>
          <span className={`r text-align-end margin ${item.margin < 0 ? "neg" : ""}`}>
            {item.margin >= 0 ? "+" : ""}
            {formatAmount(item.margin)}
            {!missingPrice && item.purchaseNetPrice > 0 && (
              <span className="pv-margin-pct">
                ({Math.round((item.margin / item.purchaseNetPrice) * 100)}%)
              </span>
            )}
          </span>
          <span className={`r text-align-end prem ${item.bonusPerUnit < 0 ? "neg" : ""}`}>
            {item.bonusPerUnit >= 0 ? "+" : ""}
            {formatAmount(item.bonusPerUnit)}
          </span>
        </div>
      ))}
      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
      {isFillPriceOpen && (
        <ProductModal
          productId={product.productId}
          purchaseInfoOnly={true}
          onClose={() => setIsFillPriceOpen(false)}
          onReset={() => onProductUpdated?.()}
        />
      )}
    </div>
  );
}

export default ProductBonusContent;
