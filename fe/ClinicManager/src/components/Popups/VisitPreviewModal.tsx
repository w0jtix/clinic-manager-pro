import React, { useEffect, useState } from "react";
import {
  Receipt,
  UserX,
  Clock,
  Rocket,
  Crown,
  Ticket,
  Eye,
  Snowflake,
} from "lucide-react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import VoucherModal from "./VoucherModal";
import { Action } from "../../models/action";
import { Visit, VisitItem, discountLabelFor } from "../../models/visit";
import { SaleItem } from "../../models/sale";
import { ClientDebt, DebtType } from "../../models/debt";
import { DiscountSettings } from "../../models/app_settings";
import { PaymentMethod } from "../../models/payment";
import VisitService from "../../services/VisitService";
import AppSettingsService from "../../services/AppSettingsService";
import ClientDebtService from "../../services/ClientDebtService";
import { formatDate } from "../../utils/dateUtils";
import { translatePaymentMethod } from "../../utils/paymentUtils";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useRoles } from "../../hooks/useRoles";

export interface VisitPreviewModalProps {
  onClose: () => void;
  visitId?: number | string;
  reviewId?: number | string;
  debtRedemptionSourceId?: number | string;
  selectedSourceVisitIdForVisit?: number | string;
  voucherId?: number | string;
  highlightProductId?: number | null;
  highlightVoucherId?: number | null;
  className?: string;
}

const dotStyle = (color?: string) => ({
  backgroundColor: color ? `rgb(${color})` : "rgba(255,255,255,0.2)",
  boxShadow: color ? `0 0 6px rgba(${color}, 0.5)` : undefined,
});

export function VisitPreviewModal({
  onClose,
  visitId,
  reviewId,
  debtRedemptionSourceId,
  selectedSourceVisitIdForVisit,
  voucherId,
  highlightProductId = null,
  highlightVoucherId = null,
  className = "",
}: VisitPreviewModalProps) {
  const { showAlert } = useAlert();
  const { isAdmin, isCompany, isMaster } = useRoles();
  const canSeeReceipt = isAdmin || isCompany || isMaster;
  const [visit, setVisit] = useState<Visit | null>(null);
  const [discountSettings, setDiscountSettings] =
    useState<DiscountSettings | null>(null);
  const [generatedDebt, setGeneratedDebt] = useState<ClientDebt | null>(null);
  const [previewSourceVisitId, setPreviewSourceVisitId] = useState<
    number | string | null
  >(null);
  const [previewVoucherId, setPreviewVoucherId] = useState<
    number | string | null
  >(null);

  useEffect(() => {
    const request = visitId
      ? VisitService.getVisitById(visitId)
      : selectedSourceVisitIdForVisit
      ? VisitService.getVisitById(selectedSourceVisitIdForVisit)
      : reviewId
      ? VisitService.findVisitByReviewId(reviewId)
      : debtRedemptionSourceId
      ? VisitService.findVisitByDebtSourceId(debtRedemptionSourceId)
      : voucherId
      ? VisitService.findVisitPaidByVoucher(voucherId)
      : null;

    if (request) {
      request
        .then((data) => setVisit(data))
        .catch((error) => {
          console.error("Error fetching Visit: ", error);
          showAlert("Nie udało się pobrać Wizyty!", AlertType.ERROR);
        });
    }
    AppSettingsService.getDiscountSettings()
      .then((data) => setDiscountSettings(data))
      .catch(() => setDiscountSettings(null));
  }, [
    visitId,
    reviewId,
    debtRedemptionSourceId,
    selectedSourceVisitIdForVisit,
    voucherId,
  ]);

  useEffect(() => {
    if (!visit) return;
    ClientDebtService.getDebtBySourceVisitId(visit.id)
      .then((data) => {
        if (Array.isArray(data)) setGeneratedDebt(data[0] ?? null);
        else setGeneratedDebt(data ?? null);
      })
      .catch(() => setGeneratedDebt(null));
  }, [visit]);

  if (!visit) return null;

  const hasDiscount = visit.serviceDiscounts.length > 0;

  const serviceColumns: Column<VisitItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item) => (
        <span
          className={`dl-name flex align-items-center g-05 ${
            item.boostItem ? "vpm-boost-name" : ""
          }`}
        >
          {item.boostItem && (
            <span className="flex" title="Usługa Boost">
              <Rocket className="dl-icon-1 icon-rocket" />
            </span>
          )}
          <span
            className="dl-dot"
            style={dotStyle(item.service?.category?.color)}
          />
          <span className={item.boostItem ? "vc-boost-name" : ""}>
            {item.serviceVariant
              ? `${item.service?.name} - ${item.serviceVariant.name}`
              : item.name}
          </span>
        </span>
      ),
    },
    {
      key: "Cena",
      width: "8rem",
      align: "end",
      render: (item) => {
        const discounted =
          item.finalPrice != null && item.finalPrice !== item.price;
        const frozen = !discounted && hasDiscount;
        const vip = discounted && visit.isVip;
        return (
          <span className="flex align-items-center justify-end g-05">
            {vip && (
              <span className="flex" title="Cena VIP">
                <Crown className="dl-icon-1 c-blue" />
              </span>
            )}
            {frozen && (
              <span
                className="flex"
                title="Cena zamrożona - rabat nie objął tej usługi"
              >
                <Snowflake className="dl-icon-1 vc-frozen-icon" />
              </span>
            )}
            <span
              className={`vc-price ${discounted ? "struck" : ""} ${
                frozen ? "frozen" : ""
              }`}
            >
              {item.price} zł
            </span>
            {discounted && (
              <span className={`vc-price-final ${vip ? "vip" : ""}`}>
                {item.finalPrice} zł
              </span>
            )}
          </span>
        );
      },
    },
  ];

  const productColumns: Column<SaleItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item) => {
        const isVoucher = !item.product;
        const isHighlighted =
          (highlightProductId != null &&
            item.product?.id === highlightProductId) ||
          (highlightVoucherId != null &&
            item.voucher?.id === highlightVoucherId);
        return (
          <span
            className={`dl-name flex align-items-center g-05 ${
              isHighlighted ? "pc-y" : ""
            }`}
          >
            {isVoucher ? (
              <Ticket className="dl-icon-1 icon-voucher" />
            ) : (
              <span
                className="dl-dot"
                style={dotStyle(item.product?.category?.color)}
              />
            )}
            {isVoucher ? "Voucher podarunkowy" : item.product?.name}
          </span>
        );
      },
    },
    {
      key: "Cena",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.price} zł</span>,
    },
  ];

  const debtColumns: Column<ClientDebt>[] = [
    {
      key: "Przyczyna",
      grow: true,
      render: (item) =>
        item.type === DebtType.ABSENCE_FEE ? (
          <span className="rv-status absence">NIEOBECNOŚĆ</span>
        ) : (
          <span className="rv-status onhold">ZALEGŁOŚĆ</span>
        ),
    },
    {
      key: "Wartość",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.value} zł</span>,
    },
  ];

  const paidDebtColumns: Column<ClientDebt>[] = [
    {
      key: "Źródło",
      width: "2.5rem",
      align: "center",
      render: (item) =>
        item.sourceVisit != null ? (
          <ActionButton
            iconTitle="Podgląd Wizyty, w której powstał dług"
            disableText={true}
            disableBorder={true}
            onClick={(e: React.MouseEvent) => {
              e.stopPropagation();
              setPreviewSourceVisitId(item.sourceVisit!.id);
            }}
          >
            <Eye className="dl-icon" />
          </ActionButton>
        ) : (
          <span className="dl-dim">—</span>
        ),
    },
    debtColumns[0],
    {
      key: "Wartość",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.value} zł</span>,
    },
  ];

  const paidDebts = visit.debtRedemptions.map((r) => r.debtSource);
  const saleItems = visit.sale?.items ?? [];
  const hasCheckers =
    (canSeeReceipt && visit.receipt === false) ||
    visit.absence ||
    visit.delayTime != null ||
    visit.isBoost ||
    visit.isVip;

  return (
    <Modal
      title="Podgląd Wizyty"
      onClose={onClose}
      size="lg"
      height="tall"
      bodyClassName="nested"
      hideFooter={true}
      className={`vpm-modal ${className}`}
      overlayClassName="vpm-overlay"
    >
      <div className="vpm-scroll flex-column f-1 min-height-0 scrollbar g-1">
        <div className="vpm-head flex align-items-center justify-center g-05 flex-wrap">
          <span className="qv-span vpm-person">{visit.employee.name}</span>
          <span className="qv-span dl-dim">•</span>
          <span
            className={`qv-span vpm-person ${
              visit.client.isDeleted ? "client-removed" : ""
            }`}
            title={visit.client.isDeleted ? "Klient usunięty" : undefined}
          >
            {visit.client.firstName} {visit.client.lastName}
          </span>
          <span className="qv-span dl-dim">•</span>
          <span className="qv-span vpm-person">{formatDate(visit.date)}</span>
        </div>

        {visit.notes && (
          <span className="qv-span notes italic">{visit.notes}</span>
        )}

        {hasCheckers && (
          <div className="vpm-checkers flex align-items-center g-05 flex-wrap">
            {canSeeReceipt && visit.receipt === false && (
              <span className="vpm-flag vm-seg-absence">
                <Receipt className="dl-icon-1" /> Bez paragonu
              </span>
            )}
            {visit.absence && (
              <span className="vpm-flag vm-seg-absence">
                <UserX className="dl-icon-1" /> Nieobecność
              </span>
            )}
            {visit.delayTime != null && (
              <span className="vpm-flag vm-seg-delay">
                <Clock className="dl-icon-1" /> Spóźnienie {visit.delayTime} min
              </span>
            )}
            {visit.isBoost && (
              <span className="vpm-flag vm-seg-boost">
                <Rocket className="dl-icon-1" /> Boost
              </span>
            )}
            {visit.isVip && (
              <span className="vpm-flag vm-seg-vip">
                <Crown className="dl-icon-1" /> VIP
              </span>
            )}
          </div>
        )}

        {visit.serviceDiscounts.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">
                Rabaty ({visit.serviceDiscounts.length})
              </span>
            </div>
            <div className="flex align-items-center g-05 flex-wrap">
              {visit.serviceDiscounts.map((d) => (
                <span key={d.id} className="vpm-discount">
                  {discountLabelFor(
                    d.type,
                    visit,
                    discountSettings as DiscountSettings
                  )}
                </span>
              ))}
            </div>
          </section>
        )}

        {visit.items.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">Usługi ({visit.items.length})</span>
            </div>
            <DataList<VisitItem>
              columns={serviceColumns}
              items={visit.items}
              getRowKey={(item) => item.id}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        {saleItems.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">Produkty ({saleItems.length})</span>
            </div>
            <DataList<SaleItem>
              columns={productColumns}
              items={saleItems}
              getRowKey={(item, index) => item.id ?? `voucher-${index}`}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        {paidDebts.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">
                Klient spłacił ({paidDebts.length})
              </span>
            </div>
            <DataList<ClientDebt>
              columns={paidDebtColumns}
              items={paidDebts}
              getRowKey={(item) => item.id}
              onRowClick={(item) =>
                item.sourceVisit && setPreviewSourceVisitId(item.sourceVisit.id)
              }
              isRowClickable={(item) => item.sourceVisit != null}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        {visit.payments.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">Płatność ({visit.payments.length})</span>
            </div>
            <div className="flex-column g-05">
              {visit.payments.map((p) => (
                <div
                  key={p.id}
                  className="vpm-payment flex space-between align-items-center"
                >
                  <span className="flex align-items-center g-05">
                    {p.method === PaymentMethod.VOUCHER && p.voucher != null && (
                      <ActionButton
                        iconTitle="Podgląd Vouchera"
                        disableText={true}
                        disableBorder={true}
                        onClick={(e: React.MouseEvent) => {
                          e.stopPropagation();
                          setPreviewVoucherId(p.voucher!.id);
                        }}
                      >
                        <Eye className="dl-icon" />
                      </ActionButton>
                    )}
                    <span
                      className={`qv-span ${
                        p.method === PaymentMethod.VOUCHER &&
                        highlightVoucherId != null &&
                        p.voucher?.id === highlightVoucherId
                          ? "pc-y"
                          : ""
                      }`}
                    >
                      {translatePaymentMethod(p.method)}
                    </span>
                  </span>
                  <span className="vc-price">{p.amount} zł</span>
                </div>
              ))}
            </div>
          </section>
        )}

        {generatedDebt != null && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span warning">Powstało zadłużenie</span>
            </div>
            <DataList<ClientDebt>
              columns={debtColumns}
              items={[generatedDebt]}
              getRowKey={(item) => item.id}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        <div className="vpm-totals flex-column g-025">
          <div className="flex space-between">
            <span className="qv-span dl-dim">Razem netto:</span>
            <span className="qv-span">{visit.totalNet} zł</span>
          </div>
          <div className="flex space-between">
            <span className="qv-span dl-dim">Razem VAT:</span>
            <span className="qv-span">{visit.totalVat} zł</span>
          </div>
          <div className="flex space-between">
            <span className="qv-span totals">Razem brutto:</span>
            <span className="qv-span totals vpm-gross">{visit.totalValue} zł</span>
          </div>
        </div>
      </div>

      {previewSourceVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewSourceVisitId(null)}
          visitId={previewSourceVisitId}
        />
      )}
      {previewVoucherId != null && (
        <VoucherModal
          onClose={() => setPreviewVoucherId(null)}
          voucherId={previewVoucherId}
          action={Action.DISPLAY}
        />
      )}
    </Modal>
  );
}

export default VisitPreviewModal;
