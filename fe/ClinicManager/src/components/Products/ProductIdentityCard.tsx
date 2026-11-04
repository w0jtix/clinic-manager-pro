import { Link } from "react-router-dom";
import { SquarePen, LayersPlus } from "lucide-react";
import ActionButton from "../ActionButton";
import ProductSupplyTiles from "./ProductSupplyTiles";
import { MedicalMark } from "../ProductVatTag";
import { getVatRateDisplay } from "../../models/vatrate";
import { Product, Unit } from "../../models/product";
import { ProductSupplySummary } from "../../models/supply-history";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductIdentityCardProps {
  product: Product;
  summary: ProductSupplySummary | null;
  onEdit: () => void;
  onUsage: () => void;
  className?: string;
}

export function ProductIdentityCard({
  product,
  summary,
  onEdit,
  onUsage,
  className = "",
}: ProductIdentityCardProps) {
  const { isCompact } = useBreakpoint();
  const unitLabel =
    product.unit === Unit.ML ? "ml" : product.unit === Unit.G ? "g" : "";

  return (
    <section className={`pd-card ${className}`}>
      <nav className="pd-crumbs flex align-items-center g-05">
        <Link to="/">Magazyn</Link>
        <span>›</span>
        <span className="dl-dim">{product.name}</span>
      </nav>

      <div className="pd-id-row flex align-items-start space-between g-1">
        <div className="pd-id-main flex-column f-1 min-width-0">
          <h1 className="pd-name flex align-items-center g-05 m-0">
            <span
              className="dl-dot"
              style={{
                backgroundColor: product.category?.color
                  ? `rgb(${product.category.color})`
                  : "rgba(255,255,255,0.2)",
              }}
            />
            <MedicalMark vatRate={product.vatRate} />
            {product.name}
          </h1>
          <div className="pd-meta flex flex-wrap align-items-center g-1">
            <span className="pd-meta-item flex align-items-center g-025">
              <span className="pd-meta-label">Marka</span>
              <b>{product.brand?.name ?? "-"}</b>
            </span>
            <span className="pd-meta-item flex align-items-center g-025">
              <span className="pd-meta-label">Kategoria</span>
              <b>{product.category?.name ?? "-"}</b>
            </span>
            <span className="pd-meta-item flex align-items-center g-025">
              <span className="pd-meta-label">VAT sprzedaży</span>
              <b>{getVatRateDisplay(product.vatRate)}</b>
            </span>
            {product.volume ? (
              <span className="pd-meta-item flex align-items-center g-025">
                <span className="pd-meta-label">Objętość</span>
                <b>
                  {product.volume}
                  {unitLabel}
                </b>
              </span>
            ) : null}
            <span className="pd-meta-item flex align-items-center g-025">
              <span className="pd-meta-label">Cena</span>
              <b>{product.sellingPrice ? `${formatAmount(product.sellingPrice)} zł` : "-"}</b>
            </span>
            <span className="pd-meta-item flex align-items-center g-025">
              <span className="pd-meta-label">Śr. zakup netto</span>
              <b>
                {summary?.avgNetPurchasePrice != null
                  ? `${formatAmount(summary.avgNetPurchasePrice)} zł`
                  : "-"}
              </b>
              {summary?.fallbackPriceUsed && (
                <span className="pd-meta-note italic" title="Brak historii zakupu - cena awaryjna z produktu">
                  awaryjna
                </span>
              )}
            </span>
          </div>
        </div>

        <div className="pd-id-actions flex align-items-center g-05">
          <ActionButton
            text="Zużycie"
            iconTitle="Dodaj zużycie"
            disableText={isCompact}
            onClick={onUsage}
            className="pd-id-btn"
          >
            <LayersPlus className="dl-icon-1 pc-y" />
          </ActionButton>
          <ActionButton
            text="Edycja"
            iconTitle="Edytuj produkt"
            disableText={isCompact}
            onClick={onEdit}
            className="pd-edit pd-id-btn"
          >
            <SquarePen className="dl-icon-1 c-blue" />
          </ActionButton>
        </div>
      </div>

      <ProductSupplyTiles
        summary={summary}
        lowStockThreshold={product.lowStockThreshold}
        className="mt-1"
      />
    </section>
  );
}

export default ProductIdentityCard;
