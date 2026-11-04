import { useState, useEffect, useMemo, useCallback } from "react";
import { Link } from "react-router-dom";
import { RotateCcw, X, TriangleAlert, ExternalLink } from "lucide-react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { KsefBookCorrectionItem, KsefInvoice, KsefVerifyProductsItem, KsefVerifyProductsRequest, KSEF_FORBIDDEN_CATEGORIES, isCorrectionInvoice } from "../../models/ksef_invoice";
import KsefCorrectionBookModal from "./KsefCorrectionBookModal";
import KsefInvoiceService from "../../services/KsefInvoiceService";
import { getVatRateDisplay } from "../../models/vatrate";
import { ExpenseCategory, ExpenseCategoryCode } from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { SellerOriginModule, SellerOriginPeriod } from "../../models/ksef_seller";
import { Product } from "../../models/product";
import AllProductService from "../../services/AllProductService";
import DropdownSelect from "../DropdownSelect";
import DigitInput from "../DigitInput";
import ActionButton from "../ActionButton";
import ProductModal from "./ProductModal";
import { KsefRememberCategoryModal } from "./KsefRememberCategoryModal";
import { validateKsefVerifyProducts, validateKsefBookCorrection } from "../../utils/validators";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface KsefInvoiceModalProps {
  invoiceId: number;
  onClose: () => void;
  onVerified?: (updated: KsefInvoice) => void;
  showCategory?: boolean;
  onCategoryChanged?: () => void;
  onPaidChanged?: (invoiceId: number, isPaid: boolean) => void;
  highlightProductId?: number | null;
  originModule?: SellerOriginModule;
  originPeriod?: SellerOriginPeriod;
  originProductId?: number;
  allowVerification?: boolean;
  className?: string;
}

export function KsefInvoiceModal({
  invoiceId,
  onClose,
  onVerified,
  showCategory = false,
  onCategoryChanged,
  onPaidChanged,
  highlightProductId = null,
  originModule,
  originPeriod,
  originProductId,
  allowVerification = true,
  className = "",
}: KsefInvoiceModalProps) {
  const { items: expenseCategoryItems } = useExpenseCategories();
  const [invoice, setInvoice] = useState<KsefInvoice | null>(null);
  const [currentId, setCurrentId] = useState(invoiceId);
  const [isLoading, setIsLoading] = useState(true);
  const [allProducts, setAllProducts] = useState<Product[]>([]);
  const [verifyItems, setVerifyItems] = useState<KsefVerifyProductsItem[]>([]);
  const [bookingItems, setBookingItems] = useState<KsefBookCorrectionItem[] | null>(null);
  const [rememberSeller, setRememberSeller] = useState(true);
  const [categoryMenuOpen, setCategoryMenuOpen] = useState(false);
  const [pendingCategory, setPendingCategory] = useState<ExpenseCategoryCode | null>(null);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  useEffect(() => {
    setCurrentId(invoiceId);
  }, [invoiceId]);

  const loadInvoice = useCallback(() => {
    setIsLoading(true);
    KsefInvoiceService.getById(currentId)
      .then((data) => {
        setInvoice(data);
        const needsProducts = data.awaitingProductVerification
          ? allowVerification
          : data.category === ExpenseCategory.PRODUCTS;
        if (needsProducts) {
          AllProductService.getProducts({ includeZero: true }, 0, 500)
            .then((result) => {
              const products = result.content ?? [];
              setAllProducts(products);
              const isCorrection = isCorrectionInvoice(data);
              const initial: KsefVerifyProductsItem[] = data.items.flatMap((item) => {
                const match = isCorrection ? item.correctionMatch : null;
                if (match?.notProduct) return [];
                if (isCorrection) {
                  const assigned =
                    match?.suggestedProductId ??
                    (item.product && !item.product.isDeleted ? item.product.id : null);
                  const suggested = match?.suggestedQuantity ?? Math.round(item.quantity ?? 0);
                  return [
                    {
                      itemId: item.id,
                      productId: products.some((p) => p.id === assigned) ? assigned : null,
                      quantity: Math.abs(suggested),
                      rememberForSeller: true,
                    },
                  ];
                }
                let productId: number | null = null;
                if (item.product) {
                  productId = item.product.id;
                } else if (item.suggestedProductId !== null) {
                  const found = products.find((p) => p.id === item.suggestedProductId);
                  productId = found ? found.id : null;
                }
                const qty = isCorrection
                  ? Math.round(Math.abs(item.quantity ?? 0))
                  : item.declaredQuantity !== null && Number.isInteger(item.declaredQuantity)
                  ? item.declaredQuantity
                  : item.quantity !== null && Number.isInteger(item.quantity)
                  ? item.quantity
                  : 0;
                return [
                  {
                    itemId: item.id,
                    productId,
                    quantity: qty,
                    rememberForSeller: true,
                  },
                ];
              });
              setVerifyItems(initial);
            })
            .catch(() => showAlert("Błąd pobierania listy produktów!", AlertType.ERROR));
        } else {
          setVerifyItems([]);
        }
      })
      .catch(() => showAlert("Błąd pobierania faktury!", AlertType.ERROR))
      .finally(() => setIsLoading(false));
  }, [currentId, showAlert, allowVerification]);

  useEffect(() => {
    loadInvoice();
  }, [loadInvoice]);

  const handleSaveProductsAndInvoiceStatus = () => {
    if (!invoice) return;
    const error = validateKsefVerifyProducts(verifyItems, invoice.items);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    const request: KsefVerifyProductsRequest = { items: verifyItems, rememberSeller };
    KsefInvoiceService.verifyProducts(invoice.id, request)
      .then((updated) => {
        showAlert("Faktura została zaksięgowana!", AlertType.SUCCESS);
        onVerified?.(updated);
        onClose();
      })
      .catch(() => showAlert("Błąd księgowania faktury!", AlertType.ERROR));
  };

  useEffect(() => {
    if (!categoryMenuOpen) return;
    const close = () => setCategoryMenuOpen(false);
    document.addEventListener("click", close);
    return () => document.removeEventListener("click", close);
  }, [categoryMenuOpen]);

  const doSetCategory = (rememberForSeller: boolean) => {
    if (!invoice || pendingCategory === null) return;
    const category = pendingCategory;
    KsefInvoiceService.setCategory(invoice.id, category, rememberForSeller)
      .then(() => {
        loadInvoice();
        showAlert("Pomyślnie zmieniono kategorię faktury!", AlertType.SUCCESS);
        onCategoryChanged?.();
      })
      .catch(() => showAlert("Błąd zmiany kategorii faktury.", AlertType.ERROR))
      .finally(() => setPendingCategory(null));
  };

  const handleSelectProductsCategory = () => {
    if (!invoice) return;
    setCategoryMenuOpen(false);
    KsefInvoiceService.toggleProductInvoice(invoice.id)
      .then(() => {
        loadInvoice();
        onCategoryChanged?.();
      })
      .catch(() => showAlert("Błąd zmiany kategorii faktury.", AlertType.ERROR));
  };

  const handleTogglePaid = () => {
    if (!invoice) return;
    KsefInvoiceService.togglePaid(invoice.id)
      .then((updated) => {
        setInvoice((prev) => (prev ? { ...prev, isPaid: updated.isPaid } : prev));
        onPaidChanged?.(invoice.id, updated.isPaid);
        showAlert("Pomyślnie zmieniono status faktury.", AlertType.SUCCESS);
      })
      .catch(() => showAlert("Błąd zmiany statusu faktury.", AlertType.ERROR));
  };

  const handleBookCorrection = () => {
    if (!invoice) return;
    const items = verifyItems.map((v) => ({
      itemId: v.itemId,
      productId: v.productId,
      quantity: v.quantity ?? 0,
    }));
    const error = validateKsefBookCorrection(items);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    setBookingItems(items);
  };

  const productOptions = useMemo(
    () => allProducts.map((p) => ({ ...p, color: p.category?.color })),
    [allProducts]
  );

  const isCorrection = invoice ? isCorrectionInvoice(invoice) : false;

  const canVerify =
    !!invoice && allowVerification && invoice.awaitingProductVerification;
  const showVerifyCols =
    !!invoice &&
    (canVerify ||
      (invoice.category === ExpenseCategory.PRODUCTS &&
        !invoice.awaitingProductVerification));
  const showRowActionCols = canVerify && !isCorrection;
  const itemsGridTemplate = [
    "2.5rem",
    showRowActionCols && "1.25rem",
    showRowActionCols && "1.5rem",
    showVerifyCols && "minmax(0, 1.3fr)",
    showVerifyCols && "3.25rem",
    "minmax(0, 1.6fr)",
    "2.5rem",
    "3rem",
    "3.5rem",
    "3.5rem",
    "2.5rem",
    "4rem",
    "4rem",
  ]
    .filter(Boolean)
    .join(" ");

  const title = !invoice
    ? "Faktura"
    : `${isCorrection ? "Faktura korygująca" : "Faktura VAT"}${
        invoice.invoiceNumber ? ` ${invoice.invoiceNumber}` : ""
      }`;

  return (
    <Modal
      dirtyValue={canVerify ? verifyItems : undefined}
      title={title}
      onClose={onClose}
      size="2xl"
      className={`scroller ksm-modal ${className}`}
      bodyClassName="ksm-body"
      hideFooter
    >
        {isLoading && (
          <span className="qv-span text-align-center">Ładowanie...</span>
        )}

        {!isLoading && !invoice && (
          <span className="qv-span text-align-center opacity-half italic">Nie znaleziono faktury</span>
        )}

        {!isLoading && invoice && (
          <div className="ksef-invoice-document flex-column">

            <div className="ksef-invoice-meta flex space-between">
              <div className="flex-column g-05">
                <span className={`ksef-invoice-meta-item flex g-05 ksef-full-opacity`}>
                  <span className="ksef-label">Typ:</span>
                  <div className="flex g-1 align-items-center">
                              {isCorrection ? (
                                <span className="ksef-badge">KOR</span>
                              ) : (
                                <span className="ksef-badge vat">VAT</span>
                              )}
                            </div>
                </span>
                {isCorrection && (
                  <span className="ksef-invoice-meta-item ksef-full-opacity g-05 flex align-items-center">
                    <span className="ksef-label">Faktura Korygowana:</span>
                    {invoice.correctedInvoiceId !== null ? (
                      <span
                        className="ksef-corrected-link vat pointer"
                        title="Przejdź do faktury korygowanej"
                        onClick={(e) => {
                          e.stopPropagation();
                          setCurrentId(invoice.correctedInvoiceId!);
                        }}
                      >
                        {invoice.correctedKsefNumber ?? "-"}
                        <ExternalLink className="dl-icon" />
                      </span>
                    ) : (
                      <span className="ksef-label op-max italic">{invoice.correctedKsefNumber ?? "-"}</span>
                    )}
                  </span>
                )}
                {!isCorrection && (
                  <>
                <span className="ksef-invoice-meta-item g-05 flex align-items-center ksef-full-opacity">
                  <span className="ksef-label">Status:</span>
                  <span
                    className={`rv-status ${invoice.isPaid ? "active" : "expired"} pointer`}
                    title="Zmień status płatności"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleTogglePaid();
                    }}
                  >
                    {invoice.isPaid ? "OPŁACONA" : "NIEOPŁACONA"}
                  </span>
                </span>


                {invoice.corrections.length > 0 && (
                    <span className="ksef-invoice-meta-item ksef-full-opacity g-05 flex align-items-center">
                      <span className="ksef-label">Faktury Korygujące: </span>

                      {invoice.corrections.map((corr, index) => (
                        <span key={corr.id}>
                          <span
                            className="ksef-corrected-link pointer"
                            title="Przejdź do faktury korygującej"
                            onClick={(e) => {
                              e.stopPropagation();
                              setCurrentId(corr.id);
                            }}
                          >
                            {corr.ksefNumber ?? "-"}
                            <ExternalLink className="dl-icon" />
                          </span>

                          {index < invoice.corrections.length - 1 ? ", " : ""}
                        </span>
                      ))}
                    </span>
                  )}
                  </>
                  )}
                {showCategory && !isCorrection && (
                  <span className="ksef-invoice-meta-item ksef-category-meta g-5px flex align-items-center">
                    <span className="ksef-label">Kategoria:</span>
                    <span className="relative flex align-items-center">
                      {invoice.category ? (
                        <span
                          className={`exp-cat-badge colored ${
                            invoice.category !== ExpenseCategory.PRODUCTS ? "pointer" : ""
                          }`}
                          style={
                            {
                              "--cat-rgb": expenseCategoryItems.find(
                                (c) => c.id === invoice.category
                              )?.color,
                            } as React.CSSProperties
                          }
                          title={
                            invoice.category !== ExpenseCategory.PRODUCTS
                              ? "Zmień kategorię"
                              : undefined
                          }
                          onClick={
                            invoice.category !== ExpenseCategory.PRODUCTS
                              ? (e) => {
                                  e.stopPropagation();
                                  setCategoryMenuOpen((open) => !open);
                                }
                              : undefined
                          }
                        >
                          {expenseCategoryItems.find((c) => c.id === invoice.category)?.name}
                        </span>
                      ) : (
                        <span
                          className={`exp-cat-badge pointer ${
                            invoice.awaitingProductVerification ? "warn" : ""
                          }`}
                          title="Zmień kategorię"
                          onClick={(e) => {
                            e.stopPropagation();
                            setCategoryMenuOpen((open) => !open);
                          }}
                        >
                          {invoice.awaitingProductVerification
                            ? "Wymaga zaksięgowania"
                            : "Przypisz"}
                        </span>
                      )}
                      {categoryMenuOpen && (
                        <div
                          className="exp-cat-menu absolute"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {expenseCategoryItems
                            .filter(
                              (item) => !KSEF_FORBIDDEN_CATEGORIES.includes(item.id)
                            )
                            .map((item) => (
                              <div
                                key={item.id}
                                className="exp-cat-menu-item flex align-items-center g-05 pointer"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setCategoryMenuOpen(false);
                                  if (item.id === invoice.category) return;
                                  if (item.id === ExpenseCategory.PRODUCTS) {
                                    if (invoice.awaitingProductVerification) return;
                                    handleSelectProductsCategory();
                                  } else {
                                    setPendingCategory(item.id);
                                  }
                                }}
                              >
                                <span
                                  className="dl-dot"
                                  style={{ backgroundColor: `rgb(${item.color})` }}
                                />
                                <span>{item.name}</span>
                              </div>
                            ))}
                        </div>
                      )}
                    </span>
                  </span>
                )}
              </div>
              <div className="flex-column align-items-end g-05">
                <span className="ksef-invoice-meta-item">
                  <span className="ksef-label">Data wystawienia:</span> {invoice.issueDate}
                </span>
                {invoice.paymentDueDate && (
                  <span className="ksef-invoice-meta-item">
                    <span className="ksef-label">Termin płatności:</span> {invoice.paymentDueDate}
                  </span>
                )}
                {invoice.paymentDate && (
                  <span className="ksef-invoice-meta-item">
                    <span className="ksef-label">Data zapłaty:</span> {invoice.paymentDate}
                  </span>
                )}
                {invoice.paymentMethod && (
                  <span className="ksef-invoice-meta-item">
                    <span className="ksef-label">Forma płatności:</span> {invoice.paymentMethod}
                  </span>
                )}
                <span className="ksef-invoice-meta-item ksef-reference-number">
                  <span className="ksef-label">Nr KSeF:</span> {invoice.ksefReferenceNumber}
                </span>
              </div>
            </div>

            <div className="ksef-invoice-divider width-max" />

            <div className="ksef-invoice-parties flex g-15">
              <div className="ksef-invoice-party flex-column f-1">
                <span className="ksef-party-label">Sprzedawca</span>
                {originModule && invoice.seller?.id ? (
                  <Link
                    to={`/ksef/seller/${invoice.seller.id}`}
                    state={{
                      module: originModule,
                      invoiceId: invoice.id,
                      period: originPeriod,
                      productId: originProductId,
                    }}
                    className="ksef-party-link flex-column"
                    title="Otwórz stronę dostawcy"
                  >
                    <span className="ksef-party-name">
                      {invoice.seller.name ?? invoice.seller.nip}
                    </span>
                    <span className="ksef-party-detail">
                      NIP: {invoice.seller.nip}
                    </span>
                    {invoice.seller.address && (
                      <span className="ksef-party-detail">
                        {invoice.seller.address}
                      </span>
                    )}
                  </Link>
                ) : (
                  <>
                    <span className="ksef-party-name">{invoice.seller?.name}</span>
                    <span className="ksef-party-detail">
                      NIP: {invoice.seller?.nip}
                    </span>
                    {invoice.seller?.address && (
                      <span className="ksef-party-detail">
                        {invoice.seller.address}
                      </span>
                    )}
                  </>
                )}
              </div>
              <div className="ksef-invoice-party flex-column f-1">
                <span className="ksef-party-label">Nabywca</span>
                {invoice.buyerName || invoice.buyerNip || invoice.buyerAddress ? (
                  <>
                    {invoice.buyerName && (
                      <span className="ksef-party-name">{invoice.buyerName}</span>
                    )}
                    {invoice.buyerNip && (
                      <span className="ksef-party-detail">NIP: {invoice.buyerNip}</span>
                    )}
                    {invoice.buyerAddress && (
                      <span className="ksef-party-detail">{invoice.buyerAddress}</span>
                    )}
                  </>
                ) : (
                  <span className="ksef-party-detail ksef-party-empty italic">Brak danych nabywcy</span>
                )}
              </div>
            </div>

            <div className="ksef-invoice-divider width-max" />

            <div className="ksef-invoice-items-section flex-column">
              {!isMobile && (
                <div
                  className="ksef-items-header"
                  style={{ gridTemplateColumns: itemsGridTemplate }}
                >
                  <span className="ksef-col-lp">Lp.</span>
                  {showVerifyCols && (
                    <>
                      {showRowActionCols && <span className="ksef-col-action" />}
                      {showRowActionCols && <span className="ksef-col-remember"/>}
                      <span className="ksef-col-product">Produkt</span>
                      <span className="ksef-col-assign-qty">Ilość</span>
                    </>
                  )}
                  <span className="ksef-col-name">Pozycja</span>
                  <span className="ksef-col-unit">Jm.</span>
                  <span className="ksef-col-qty">Ilość</span>
                  <span className="ksef-col-price">netto</span>
                  <span className="ksef-col-price">brutto</span>
                  <span className="ksef-col-vat">VAT</span>
                  <span className="ksef-col-net">Σ netto</span>
                  <span className="ksef-col-net">Σ brutto</span>
                </div>
              )}
              {invoice.items.map((item, i) => {
                const isPending = canVerify;
                const isProducts = invoice.category === ExpenseCategory.PRODUCTS;
                const verifyItem = verifyItems.find((v) => v.itemId === item.id);
                const isExcluded = isPending && !verifyItem;
                const assignedProduct = verifyItem
                  ? allProducts.find((p) => p.id === verifyItem.productId) ?? null
                  : null;

                const actionCell = isPending && !isCorrection && (
                  <div className="ksef-col-action flex justify-center align-items-center">
                    {isExcluded ? (
                      <ActionButton
                        iconTitle="Przywróć przypisanie"
                        disableText={true}
                        disableBorder={true}
                        onClick={() => {
                          const suggestedProduct = item.suggestedProductId !== null
                            ? allProducts.find((p) => p.id === item.suggestedProductId) ?? null
                            : null;
                          const qty = item.quantity !== null && Number.isInteger(item.quantity)
                            ? item.quantity
                            : 0;
                          setVerifyItems((prev) => [
                            ...prev,
                            {
                              itemId: item.id,
                              productId: suggestedProduct ? suggestedProduct.id : null,
                              quantity: qty,
                              rememberForSeller: true,
                            },
                          ]);
                        }}
                      >
                        <RotateCcw className="dl-icon" />
                      </ActionButton>
                    ) : (
                      <ActionButton
                        iconTitle="Wyklucz z weryfikacji"
                        disableText={true}
                        disableBorder={true}
                        disabled={isProducts}
                        onClick={() => setVerifyItems((prev) => prev.filter((v) => v.itemId !== item.id))}
                      >
                        <X className="dl-icon c-red" />
                      </ActionButton>
                    )}
                  </div>
                );

                const rememberCell = isPending && !isCorrection && (
                  <div className="ksef-col-remember flex justify-center align-items-center">
                    <input
                      type="checkbox"
                      className={`app-checkbox${isProducts ? " locked" : ""}`}
                      checked={verifyItem?.rememberForSeller ?? true}
                      disabled={isExcluded}
                      readOnly={isProducts}
                      title="Zapamiętaj przypisanie produktu dla tego sprzedawcy"
                      onChange={(e) => {
                        if (!isPending) return;
                        setVerifyItems((prev) =>
                          prev.map((v) =>
                            v.itemId === item.id
                              ? { ...v, rememberForSeller: e.target.checked }
                              : v
                          )
                        );
                      }}
                    />
                  </div>
                );

                const match = isCorrection ? item.correctionMatch : null;
                const correctionDelta = item.quantity ?? 0;

                const productCell = (
                  <div className="ksef-col-product">
                    {isCorrection && isPending && isExcluded && match?.notProduct ? (
                      <span className="ksef-assigned-product-name dl-dim italic">nie produkt</span>
                    ) : (isPending && !isExcluded) ? (
                      <DropdownSelect<Product>
                        items={productOptions}
                        value={assignedProduct}
                        onChange={(selected) => {
                          setVerifyItems((prev) =>
                            prev.map((v) =>
                              v.itemId === item.id
                                ? { ...v, productId: selected ? (selected as Product).id : null }
                                : v
                            )
                          );
                        }}
                        getItemLabel={(p) => p.name}
                        searchable={true}
                        allowNew={true}
                        showNewPopup={true}
                        newItemComponent={ProductModal}
                        newItemLabel="Dodaj produkt"
                        newItemProps={{
                          onReset: (createdProduct?: Product) => {
                            if (!createdProduct) return;
                            setAllProducts((prev) =>
                              prev.some((p) => p.id === createdProduct.id)
                                ? prev
                                : [createdProduct, ...prev]
                            );
                            setVerifyItems((prev) =>
                              prev.map((v) =>
                                v.itemId === item.id
                                  ? { ...v, productId: createdProduct.id }
                                  : v
                              )
                            );
                          },
                          initialName: item.ksefItemName,
                          initialVatRate: item.vatRate,
                          hideStock: true,
                          hidePurchaseInfo: true,
                        }}
                        allowColors={true}
                        placeholder="Wybierz produkt..."
                        searchPlaceholder="Szukaj produktu..."
                        className="ksef-product-dropdown"
                      />
                    ) : (
                      item.product?.isDeleted ? (
                        <span className="ksef-assigned-product-name deleted">
                          Produkt usunięty
                        </span>
                      ) : (
                        <span className="ksef-assigned-product-name flex align-items-center g-05">
                          {(() => {
                            const dotColor =
                              assignedProduct?.category?.color ??
                              item.product?.category?.color;
                            return (
                              <span
                                className="dl-dot"
                                style={
                                  dotColor
                                    ? {
                                        backgroundColor: `rgb(${dotColor})`,
                                        boxShadow: `0 0 6px rgba(${dotColor}, 0.5)`,
                                      }
                                    : { backgroundColor: "rgba(255,255,255,0.2)" }
                                }
                              />
                            );
                          })()}
                          <span
                            className={`ksef-assigned-name-text ${
                              highlightProductId != null &&
                              (assignedProduct?.id ?? item.product?.id) ===
                                highlightProductId
                                ? "pc-y"
                                : ""
                            }`}
                          >
                            {assignedProduct?.name ?? item.product?.name ?? "-"}
                          </span>
                        </span>
                      )
                    )}
                  </div>
                );

                const assignQtyCell = (
                  <div className="ksef-col-assign-qty flex justify-center align-items-center">
                    <DigitInput
                      value={verifyItem?.quantity ?? 0}
                      disabled={
                        isProducts || isExcluded || (isCorrection && correctionDelta === 0)
                      }
                      onChange={(value) => {
                        if (!isPending) return;
                        setVerifyItems((prev) =>
                          prev.map((v) =>
                            v.itemId === item.id
                              ? { ...v, quantity: value }
                              : v
                          )
                        );
                      }}
                      min={0}
                      max={9999}
                      placeholder="0"
                      className="ksef-assign-qty-input"
                    />
                  </div>
                );

                const unit = item.unit ?? "szt.";
                const unitNet = item.unitNetPrice ? item.unitNetPrice.toFixed(2) : 0;
                const unitGross = item.unitGrossPrice ? item.unitGrossPrice.toFixed(2) : 0;
                const vat = item.vatRate ? getVatRateDisplay(item.vatRate) : 0;
                const net = item.netValue ? item.netValue.toFixed(2) : 0;
                const gross = item.grossValue ? item.grossValue.toFixed(2) : 0;

                if (isMobile) {
                  return (
                    <div
                      key={item.id}
                      className={`ksef-item-card flex-column g-05 ${i % 2 === 1 ? "ksef-items-row-alt" : ""} ${isExcluded ? "kic-excluded" : ""}`}
                    >
                      <span className="kic-index pc-y">Pozycja {i + 1}.</span>
                      <div className="kic-line kic-line-name flex align-items-center g-1">
                        <span className="kic-name">{item.ksefItemName}</span>
                        <span className="kic-field flex align-items-center">
                          <span className="ksef-party-label ni-label">Ilość:</span>
                          <span>{item.quantity} {unit}</span>
                        </span>
                      </div>
                      <div className="kic-line kic-line-nums flex align-items-center space-between">
                        <div className="kic-nums-unit">
                          <span className="kic-field flex align-items-center">
                            <span className="ksef-party-label ni-label">Netto:</span>
                            <span>{unitNet}</span>
                          </span>
                          <span className="kic-field flex align-items-center">
                            <span className="ksef-party-label ni-label">VAT:</span>
                            <span>{vat}</span>
                          </span>
                          <span className="kic-field flex align-items-center">
                            <span className="ksef-party-label ni-label">Brutto:</span>
                            <span>{unitGross}</span>
                          </span>
                        </div>
                        <div className="kic-nums-sum">
                          <span className="kic-field flex align-items-center">
                            <span className="ksef-party-label ni-label">Σ netto:</span>
                            <span>{net}</span>
                          </span>
                          <span className="kic-field flex align-items-center">
                            <span className="ksef-party-label ni-label">Σ brutto:</span>
                            <span>{gross}</span>
                          </span>
                        </div>
                      </div>
                      {showVerifyCols && (
                        <div className={`kic-line kic-line-assign flex align-items-center g-05 ${!isPending ? "kic-assign-readonly" : ""}`}>
                          {actionCell}
                          {rememberCell}
                          {productCell}
                          <div className="kic-assign-qty flex align-items-center g-05">
                            <span className="ksef-party-label ni-label">Ilość:</span>
                            {assignQtyCell}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                }

                return (
                  <div
                    key={item.id}
                    className={`ksef-items-row ${i % 2 === 1 ? "ksef-items-row-alt" : ""} ${isExcluded ? "ksef-items-row-excluded" : ""}`}
                    style={{ gridTemplateColumns: itemsGridTemplate }}
                  >
                    <span className="ksef-col-lp">{i + 1}</span>
                    {showVerifyCols && (
                      <>
                        {actionCell}
                        {rememberCell}
                        {productCell}
                        {assignQtyCell}
                      </>
                    )}
                    <span className="ksef-col-name">{item.ksefItemName}</span>
                    <span className="ksef-col-unit">{unit}</span>
                    <span className="ksef-col-qty">{item.quantity}</span>
                    <span className="ksef-col-price">{unitNet}</span>
                    <span className="ksef-col-price">{unitGross}</span>
                    <span className="ksef-col-vat">{vat}</span>
                    <span className="ksef-col-net">{net}</span>
                    <span className="ksef-col-net">{gross}</span>
                  </div>
                );
              })}
            </div>

            <div className="ksef-invoice-divider width-max" />
              <section className="ksef-summary flex space-between">
                <div className="flex-columnm width-half justify-start align-items-center">
                      {canVerify && (
                        <>
                        {!isCorrection && !invoice.seller?.autoMarkProducts && (
                            <label className="ksef-remember-seller-toggle flex align-items-center g-05 pointer mb-1">
                              <input
                                type="checkbox"
                                className="app-checkbox"
                                checked={rememberSeller}
                                onChange={(e) => setRememberSeller(e.target.checked)}
                              />
                              <span className="ksef-remember-seller-toggle-label">
                                Oznaczaj kolejne faktury tego sprzedawcy jako produktowe
                              </span>
                            </label>
                          )}
                          <div className="ksef-book-row flex align-items-center g-1">
                            <ActionButton
                              text={isCorrection ? "Księguj Korektę" : "Księguj fakturę"}
                              onClick={isCorrection ? handleBookCorrection : handleSaveProductsAndInvoiceStatus}
                              className="clx-btn sync"
                            />
                            <div className="ksef-book-warning flex align-items-center g-05">
                              <TriangleAlert className="dl-icon-1 inv-warning c-red" />
                              <span className="ksef-book-warning-text">
                                Zaksięgowania nie będzie można cofnąć.
                              </span>
                            </div>
                          </div>
                        </>
                      )}
                    </div>
            <div className="ksef-invoice-summary flex-column align-items-end">
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">Wartość netto:</span>
                <span className="ksef-summary-value">{invoice.netAmount ?invoice.netAmount.toFixed(2) : 0} {invoice.currency}</span>
              </div>
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">VAT:</span>
                <span className="ksef-summary-value">{invoice.vatAmount ? invoice.vatAmount.toFixed(2) : 0} {invoice.currency}</span>
              </div>
              <div className="ksef-summary-row ksef-summary-gross flex">
                <span className="ksef-summary-label">Do zapłaty brutto:</span>
                <span className="ksef-summary-value">{invoice.grossAmount ? invoice.grossAmount.toFixed(2) : 0} {invoice.currency}</span>
              </div>
              {!isCorrection && (
                <span className={`rv-status ${invoice.isPaid ? "active" : "expired"} mt-05`}>
                  {invoice.isPaid ? "OPŁACONA" : "NIEOPŁACONA"}
                </span>
              )}
            </div>
              
                    </section>
          </div>
        )}
        {bookingItems && invoice && (
          <KsefCorrectionBookModal
            invoiceId={invoice.id}
            items={bookingItems}
            onClose={() => setBookingItems(null)}
            onBooked={(updated) => {
              setBookingItems(null);
              onVerified?.(updated);
              onClose();
            }}
          />
        )}
        {invoice && pendingCategory !== null && (
          <KsefRememberCategoryModal
            sellerName={invoice.seller?.name ?? ""}
            categoryName={expenseCategoryItems.find((c) => c.id === pendingCategory)?.name ?? ""}
            categoryColor={expenseCategoryItems.find((c) => c.id === pendingCategory)?.color}
            onConfirm={() => doSetCategory(true)}
            onCancel={() => doSetCategory(false)}
            onClose={() => setPendingCategory(null)}
          />
        )}
    </Modal>
  );
}

export default KsefInvoiceModal;
