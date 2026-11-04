import { useState, useEffect, useCallback } from "react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import {
  KsefIssuedInvoice,
  isIssuedCorrectionInvoice,
} from "../../models/ksef_issued_invoice";
import KsefIssuedInvoiceService from "../../services/KsefIssuedInvoiceService";
import { getVatRateDisplay } from "../../models/vatrate";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface KsefIssuedInvoiceModalProps {
  invoiceId: number;
  onClose: () => void;
  className?: string;
}

const ITEMS_GRID_TEMPLATE = [
  "2.5rem",
  "minmax(0, 1.6fr)",
  "2.5rem",
  "3rem",
  "3.5rem",
  "3.5rem",
  "2.5rem",
  "4rem",
  "4rem",
].join(" ");

export function KsefIssuedInvoiceModal({
  invoiceId,
  onClose,
  className = "",
}: KsefIssuedInvoiceModalProps) {
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();
  const [invoice, setInvoice] = useState<KsefIssuedInvoice | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchInvoice = useCallback(() => {
    setIsLoading(true);
    KsefIssuedInvoiceService.getById(invoiceId)
      .then(setInvoice)
      .catch(() => {
        setInvoice(null);
        showAlert("Błąd pobierania faktury.", AlertType.ERROR);
      })
      .finally(() => setIsLoading(false));
  }, [invoiceId]);

  useEffect(() => {
    fetchInvoice();
  }, [fetchInvoice]);

  const isCorrection = invoice ? isIssuedCorrectionInvoice(invoice) : false;

  const title = !invoice
    ? "Faktura"
    : `${isCorrection ? "Faktura korygująca" : "Faktura VAT"}${
        invoice.invoiceNumber ? ` ${invoice.invoiceNumber}` : ""
      }`;

  return (
    <Modal
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
        <span className="qv-span text-align-center opacity-half italic">
          Nie znaleziono faktury
        </span>
      )}

      {!isLoading && invoice && (
        <div className="ksef-invoice-document flex-column">
          <div className="ksef-invoice-meta flex space-between">
            <div className="flex-column g-05">
              <span className="ksef-invoice-meta-item flex g-05 ksef-full-opacity">
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
                  <span className="ksef-label op-max italic">
                    {invoice.correctedKsefNumber ?? "-"}
                  </span>
                </span>
              )}
            </div>
            <div className="flex-column align-items-end g-05">
              <span className="ksef-invoice-meta-item">
                <span className="ksef-label">Data wystawienia:</span>{" "}
                {invoice.issueDate}
              </span>
              {invoice.paymentDueDate && (
                <span className="ksef-invoice-meta-item">
                  <span className="ksef-label">Termin płatności:</span>{" "}
                  {invoice.paymentDueDate}
                </span>
              )}
              <span className="ksef-invoice-meta-item ksef-reference-number">
                <span className="ksef-label">Nr KSeF:</span>{" "}
                {invoice.ksefReferenceNumber}
              </span>
            </div>
          </div>

          <div className="ksef-invoice-divider width-max" />

          <div className="ksef-invoice-parties flex g-15">
            <div className="ksef-invoice-party flex-column f-1">
              <span className="ksef-party-label">Sprzedawca</span>
              {invoice.sellerName || invoice.sellerNip || invoice.sellerAddress ? (
                <>
                  {invoice.sellerName && (
                    <span className="ksef-party-name">{invoice.sellerName}</span>
                  )}
                  {invoice.sellerNip && (
                    <span className="ksef-party-detail">
                      NIP: {invoice.sellerNip}
                    </span>
                  )}
                  {invoice.sellerAddress && (
                    <span className="ksef-party-detail">
                      {invoice.sellerAddress}
                    </span>
                  )}
                </>
              ) : (
                <span className="ksef-party-detail ksef-party-empty italic">
                  Brak danych sprzedawcy
                </span>
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
                    <span className="ksef-party-detail">
                      NIP: {invoice.buyerNip}
                    </span>
                  )}
                  {invoice.buyerAddress && (
                    <span className="ksef-party-detail">
                      {invoice.buyerAddress}
                    </span>
                  )}
                </>
              ) : (
                <span className="ksef-party-detail ksef-party-empty italic">
                  Brak danych nabywcy
                </span>
              )}
            </div>
          </div>

          <div className="ksef-invoice-divider width-max" />

          <div className="ksef-invoice-items-section flex-column">
            {!isMobile && (
              <div
                className="ksef-items-header"
                style={{ gridTemplateColumns: ITEMS_GRID_TEMPLATE }}
              >
                <span className="ksef-col-lp">Lp.</span>
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
                    className={`ksef-item-card flex-column g-05 ${
                      i % 2 === 1 ? "ksef-items-row-alt" : ""
                    }`}
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
                  </div>
                );
              }

              return (
                <div
                  key={item.id}
                  className={`ksef-items-row ${
                    i % 2 === 1 ? "ksef-items-row-alt" : ""
                  }`}
                  style={{ gridTemplateColumns: ITEMS_GRID_TEMPLATE }}
                >
                  <span className="ksef-col-lp">{i + 1}</span>
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

          <section className="ksef-summary flex justify-end">
            <div className="ksef-invoice-summary flex-column align-items-end">
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">Wartość netto:</span>
                <span className="ksef-summary-value">
                  {invoice.netAmount ? invoice.netAmount.toFixed(2) : 0}{" "}
                  {invoice.currency}
                </span>
              </div>
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">VAT:</span>
                <span className="ksef-summary-value">
                  {invoice.vatAmount ? invoice.vatAmount.toFixed(2) : 0}{" "}
                  {invoice.currency}
                </span>
              </div>
              <div className="ksef-summary-row ksef-summary-gross flex">
                <span className="ksef-summary-label">Wartość brutto:</span>
                <span className="ksef-summary-value">
                  {invoice.grossAmount ? invoice.grossAmount.toFixed(2) : 0}{" "}
                  {invoice.currency}
                </span>
              </div>
            </div>
          </section>
        </div>
      )}
    </Modal>
  );
}

export default KsefIssuedInvoiceModal;
