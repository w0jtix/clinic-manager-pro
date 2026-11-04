import React, { useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import CategoryMenu from "../CategoryMenu";
import { ExpenseCategory, ExpenseCategoryCode } from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { KSEF_FORBIDDEN_CATEGORIES, KsefSeller } from "../../models/ksef_invoice";
import {
  KsefSellerSummary,
  SELLER_ORIGIN_LABELS,
  SellerPageOrigin,
  getEffectiveDefaultCategory,
} from "../../models/ksef_seller";
import { formatAmount } from "../../utils/formatUtils";
import { splitAddress } from "../../utils/addressUtils";

export interface SellerIdentityCardProps {
  seller: KsefSeller;
  summary: KsefSellerSummary | null;
  origin: SellerPageOrigin | null;
  onSetDefaultCategory: (category: ExpenseCategoryCode | null) => void;
  className?: string;
}

export function SellerIdentityCard({
  seller,
  summary,
  origin,
  onSetDefaultCategory,
  className = "",
}: SellerIdentityCardProps) {
  const { items: expenseCategoryItems } = useExpenseCategories();
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const pillRef = useRef<HTMLButtonElement>(null);

  const defaultCategory = getEffectiveDefaultCategory(seller);
  const categoryItem = expenseCategoryItems.find(
    (item) => item.id === defaultCategory
  );

  const menuItems = useMemo(
    () =>
      expenseCategoryItems.filter(
        (item) => !KSEF_FORBIDDEN_CATEGORIES.includes(item.id)
      ),
    [expenseCategoryItems]
  );

  const renderAddress = (address: string | null) => {
    if (!address) return "-";
    const parts = splitAddress(address);
    if (!parts) return address;
    return (
      <>
        <span className="sd-addr-street">{parts.street},</span>{" "}
        <span className="sd-addr-city">
          {parts.city}
          {parts.rest && <span className="sd-addr-rest">, {parts.rest}</span>}
        </span>
      </>
    );
  };

  return (
    <section className={`pd-card sd-identity ${className}`}>
      <div className="sd-head-row flex flex-wrap align-items-center space-between g-1">
        <nav className="pd-crumbs flex align-items-center g-05">
          {origin?.module === "Product" && origin.productId != null ? (
            <Link to={`/warehouse/product/${origin.productId}`}>
              {SELLER_ORIGIN_LABELS.Product}
            </Link>
          ) : origin ? (
            <>
              <Link
                to="/my-company"
                state={{ module: origin.module, period: origin.period }}
              >
                {SELLER_ORIGIN_LABELS[origin.module]}
              </Link>
              {origin.invoiceId != null && (
                <>
                  <span>›</span>
                  <Link
                    to="/my-company"
                    state={{
                      module: origin.module,
                      period: origin.period,
                      previewInvoiceId: origin.invoiceId,
                    }}
                  >
                    Podgląd
                  </Link>
                </>
              )}
            </>
          ) : (
            <Link to="/my-company">Firma</Link>
          )}
          <span>›</span>
          <span className="dl-dim">{seller.name ?? seller.nip}</span>
        </nav>

        {summary && (
          <dl className="sd-stats flex flex-wrap align-items-baseline m-0">
            <div className="sd-stat flex align-items-baseline g-05">
              <dt>Faktury</dt>
              <dd className="tnum c-f-green">{summary.invoiceCount}</dd>
            </div>
            <div className="sd-stat flex align-items-baseline g-05">
              <dt>Suma z faktur</dt>
              <dd className="tnum">{formatAmount(summary.totalGross)} zł</dd>
            </div>
            {summary.manualExpenseCount > 0 && (
              <>
                <div className="sd-stat flex align-items-baseline g-05">
                  <dt>Koszty ręczne</dt>
                  <dd className="tnum c-f-green">{summary.manualExpenseCount}</dd>
                </div>
                <div className="sd-stat flex align-items-baseline g-05">
                  <dt>Suma z kosztów</dt>
                  <dd className="tnum">{formatAmount(summary.manualExpenseTotal)} zł</dd>
                </div>
              </>
            )}
            {summary.awaitingVerificationCount > 0 && (
              <div className="sd-stat flex align-items-baseline g-05">
                <dt>Faktury do weryfikacji</dt>
                <dd className="tnum pc-y">{summary.awaitingVerificationCount}</dd>
              </div>
            )}
            {summary.unpaidCount > 0 && (
              <div className="sd-stat flex align-items-baseline g-05">
                <dt>Nieopłacone faktury</dt>
                <dd className="tnum c-f-red">{summary.unpaidCount}</dd>
              </div>
            )}
          </dl>
        )}
      </div>

      <h1 className="pd-name pc-y flex align-items-center g-05 m-0">
        {seller.name ?? "Dostawca bez nazwy"}
      </h1>

      <div className="pd-meta flex flex-wrap align-items-center g-1">
        <span className="pd-meta-item flex align-items-center g-05">
          NIP <b className="tnum">{seller.nip}</b>
        </span>
        <span className="pd-meta-item flex align-items-center g-05">
          Adres <b title={seller.address ?? undefined}>{renderAddress(seller.address)}</b>
        </span>
        <span className="pd-meta-item flex align-items-center g-05">
          E-mail <b>{seller.email ?? "-"}</b>
        </span>
        <span className="pd-meta-item flex align-items-center g-05">
          Telefon <b>{seller.phone ?? "-"}</b>
        </span>
      </div>

      <div className="sd-badges flex flex-wrap align-items-center g-05">
        {categoryItem ? (
          <>
            <span
              className="sd-badge flex align-items-center g-05"
              style={{ "--cat-rgb": categoryItem.color } as React.CSSProperties}
            >
              <span className="dl-dot" />
              Domyślna kategoria: {categoryItem.name}
              <button
                ref={pillRef}
                type="button"
                className="sd-badge-action pointer"
                title="Zmień kategorię dla przyszłych faktur"
                onClick={() => setMenuAnchor(menuAnchor ? null : pillRef.current)}
              >
                Zmień
              </button>
              <button
                type="button"
                className="sd-badge-action pointer"
                title="Nowe faktury będą przychodzić bez kategorii"
                onClick={() => onSetDefaultCategory(null)}
              >
                Wyłącz
              </button>
            </span>
            {defaultCategory === ExpenseCategory.PRODUCTS && (
              <span className="sd-hint">
                Dla tej kategorii nowe faktury trafiają do weryfikacji produktów.
              </span>
            )}
          </>
        ) : (
          <>
            <button
              ref={pillRef}
              type="button"
              className="sd-badge empty flex align-items-center g-05 pointer"
              title="Ustaw kategorię dla przyszłych faktur"
              onClick={() => setMenuAnchor(menuAnchor ? null : pillRef.current)}
            >
              <span className="dl-dot" />
              Ustaw domyślną kategorię
            </button>
            <span className="sd-hint">
              Nowe faktury przychodzą bez kategorii i wymagają przypisania.
            </span>
          </>
        )}
      </div>

      {menuAnchor && (
        <CategoryMenu
          anchor={menuAnchor}
          items={menuItems}
          selected={defaultCategory}
          onSelect={onSetDefaultCategory}
          onClose={() => setMenuAnchor(null)}
        />
      )}
    </section>
  );
}

export default SellerIdentityCard;
