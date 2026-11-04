import { useCallback, useEffect, useState } from "react";
import { TriangleAlert, Ticket, Eye } from "lucide-react";
import Modal from "../Modal";
import RemoveModal from "./RemoveModal";
import ActionButton from "../ActionButton";
import DataList, { Column } from "../DataList";
import VisitPreviewModal from "./VisitPreviewModal";
import { Visit } from "../../models/visit";
import { SaleItem } from "../../models/sale";
import { ClientDebt, DebtType } from "../../models/debt";
import { Voucher, VoucherStatus } from "../../models/voucher";
import { PaymentStatus } from "../../models/payment";
import VisitService from "../../services/VisitService";
import { formatDate } from "../../utils/dateUtils";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { apiErrorMessage } from "../../utils/apiError";

export interface RemoveVisitModalProps {
  onClose: () => void;
  visitId: number | string;
  handleResetFiltersAndData: () => void;
  className?: string;
}

const VOUCHER_STATUS_LABELS: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "AKTYWNY",
  [VoucherStatus.EXPIRED]: "NIEAKTYWNY",
  [VoucherStatus.USED]: "ZREALIZOWANY",
};
const VOUCHER_STATUS_CLASSES: Record<VoucherStatus, string> = {
  [VoucherStatus.ACTIVE]: "active",
  [VoucherStatus.EXPIRED]: "expired",
  [VoucherStatus.USED]: "used",
};

const dotStyle = (color?: string) => ({
  backgroundColor: color ? `rgb(${color})` : "rgba(255,255,255,0.2)",
  boxShadow: color ? `0 0 6px rgba(${color}, 0.5)` : undefined,
});

export function RemoveVisitModal({
  onClose,
  visitId,
  handleResetFiltersAndData,
  className = "",
}: RemoveVisitModalProps) {
  const { showAlert } = useAlert();
  const [fetchedVisit, setFetchedVisit] = useState<Visit | null>(null);
  const [saleItemProducts, setSaleItemProducts] = useState<SaleItem[]>([]);
  const [saleItemVouchers, setSaleItemVouchers] = useState<SaleItem[]>([]);
  const [paidClientDebts, setPaidClientDebts] = useState<ClientDebt[]>([]);
  const [vouchersAsPayment, setVouchersAsPayment] = useState<Voucher[]>([]);
  const [visitWithDebtRedempted, setVisitWithDebtRedempted] =
    useState<Visit | null>(null);
  const [previewVisitId, setPreviewVisitId] = useState<number | string | null>(
    null
  );

  const voucherConflict =
    saleItemVouchers.length > 0 &&
    saleItemVouchers.some((v) => v.voucher?.status === VoucherStatus.USED);

  const blocked = voucherConflict || visitWithDebtRedempted !== null;

  const handleDebtRedempted = async (id: string | number) => {
    VisitService.findVisitByDebtSourceVisitId(id)
      .then((data) => setVisitWithDebtRedempted(data))
      .catch((error) => {
        console.error(error);
        setVisitWithDebtRedempted(null);
      });
  };

  useEffect(() => {
    VisitService.getVisitById(visitId)
      .then((data) => setFetchedVisit(data))
      .catch((error) => {
        console.error("Error fetching Visit: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  }, [visitId]);

  useEffect(() => {
    if (!fetchedVisit) return;
    if (fetchedVisit.sale) {
      setSaleItemProducts(
        fetchedVisit.sale.items.filter((item) => item.product !== null)
      );
      setSaleItemVouchers(
        fetchedVisit.sale.items.filter((item) => item.voucher !== null)
      );
    }
    if (fetchedVisit.debtRedemptions.length > 0) {
      setPaidClientDebts(
        fetchedVisit.debtRedemptions.map((r) => r.debtSource)
      );
    }
    if (fetchedVisit.payments.length > 0) {
      setVouchersAsPayment(
        fetchedVisit.payments.map((p) => p.voucher).filter((v) => v != null)
      );
    }
    const statusPaid = fetchedVisit.paymentStatus === PaymentStatus.PAID;
    const totalPaid = fetchedVisit.payments.reduce(
      (sum, p) => sum + p.amount,
      0
    );
    const debtFromPayment = totalPaid < fetchedVisit.totalValue;
    if (statusPaid && (fetchedVisit.absence || debtFromPayment)) {
      handleDebtRedempted(fetchedVisit.id);
    }
  }, [fetchedVisit]);

  const handleRemove = useCallback(() => {
    VisitService.deleteVisit(visitId)
      .then(() => {
        showAlert("Wizyta usunięta!", AlertType.SUCCESS);
        handleResetFiltersAndData();
        onClose();
      })
      .catch((error) => {
        console.error("Error removing Visit", error);
        showAlert(apiErrorMessage(error) ?? "Błąd usuwania wizyty.", AlertType.ERROR);
      });
  }, [visitId, showAlert, handleResetFiltersAndData, onClose]);

  if (!fetchedVisit) return null;

  const hasDetails =
    saleItemProducts.length > 0 ||
    saleItemVouchers.length > 0 ||
    paidClientDebts.length > 0 ||
    vouchersAsPayment.length > 0 ||
    visitWithDebtRedempted !== null;

  if (!hasDetails) {
    return (
      <RemoveModal
        onClose={onClose}
        warningText="Wizyta zostanie trwale usunięta."
        handleRemove={handleRemove}
        className={className}
      />
    );
  }

  const productColumns: Column<SaleItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: (item) => (
        <span className="dl-name flex align-items-center g-05">
          <span
            className="dl-dot"
            style={dotStyle(item.product?.category?.color)}
          />
          {item.product?.name}
        </span>
      ),
    },
    {
      key: "Cena",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.price} zł</span>,
    },
  ];

  const voucherSaleColumns: Column<SaleItem>[] = [
    {
      key: "Nazwa",
      grow: true,
      render: () => (
        <span className="dl-name flex align-items-center g-05">
          <Ticket className="dl-icon-1 icon-voucher" />
          Voucher podarunkowy
        </span>
      ),
    },
    {
      key: "Wartość",
      width: "6rem",
      align: "end",
      render: (item) => (
        <span className="vc-price">
          {item.price ?? item.voucher?.value} zł
        </span>
      ),
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

  const paymentVoucherColumns: Column<Voucher>[] = [
    {
      key: "Status",
      width: "9rem",
      align: "center",
      render: (item) => (
        <span className={`rv-status ${VOUCHER_STATUS_CLASSES[item.status]}`}>
          {VOUCHER_STATUS_LABELS[item.status]}
        </span>
      ),
    },
    {
      key: "Klient",
      grow: true,
      render: (item) => (
        <span className="dl-name">
          {item.client.firstName} {item.client.lastName}
        </span>
      ),
    },
    {
      key: "Wartość",
      width: "6rem",
      align: "end",
      render: (item) => <span className="vc-price">{item.value} zł</span>,
    },
  ];

  return (
    <Modal
      title="Usunąć Wizytę?"
      onClose={onClose}
      size="lg"
      height="tall"
      bodyClassName="nested"
      className={className}
      footer={
        <ActionButton
          text="Usuń"
          className="modal-button danger"
          disabled={blocked}
          onClick={handleRemove}
        />
      }
    >
      <div className="rvm-scroll flex-column f-1 min-height-0 scrollbar g-1">
        <div className="rvm-warning flex align-items-center g-05">
          <TriangleAlert className="dl-icon-1 c-red" />
          <span className={`qv-span ${blocked ? "c-red" : ""}`}>
            {blocked
              ? "Nie można usunąć tej Wizyty - powoduje konflikt (szczegóły poniżej)."
              : "Wizyta zostanie trwale usunięta."}
          </span>
        </div>

        {visitWithDebtRedempted && (
          <div className="rvm-conflict flex-column g-025">
            <span className="qv-span warning">Konflikt:</span>
            <span className="qv-span warning">
              {fetchedVisit.absence
                ? "Nieobecność usuwanej Wizyty została spłacona podczas innej Wizyty:"
                : "Zadłużenie tej Wizyty z powodu niepełnej płatności zostało spłacone podczas Wizyty:"}
            </span>
            <span
              className="qv-span warning pointer flex align-items-center g-05 width-fit-content"
              title="Podgląd Wizyty"
              onClick={() => setPreviewVisitId(visitWithDebtRedempted.id)}
            >
              <Eye className="dl-icon-1" />
              {formatDate(visitWithDebtRedempted.date)}{" "}
              {visitWithDebtRedempted.client.firstName}{" "}
              {visitWithDebtRedempted.client.lastName}
            </span>
          </div>
        )}

        {saleItemProducts.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">
                Produkty zostaną przywrócone do Magazynu:
              </span>
            </div>
            <DataList<SaleItem>
              columns={productColumns}
              items={saleItemProducts}
              getRowKey={(item, index) => item.id ?? `p-${index}`}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        {saleItemVouchers.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">Zakupione Vouchery zostaną usunięte:</span>
            </div>
            <DataList<SaleItem>
              columns={voucherSaleColumns}
              items={saleItemVouchers}
              getRowKey={(item, index) => item.id ?? `v-${index}`}
              hideHeader={true}
              className="vm-cart-list"
            />
            {voucherConflict && (
              <span className="qv-span warning">
                Konflikt! Voucher został użyty jako forma płatności do innej
                Wizyty.
              </span>
            )}
          </section>
        )}

        {paidClientDebts.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">Spłacone zadłużenia zostaną przywrócone:</span>
            </div>
            <DataList<ClientDebt>
              columns={debtColumns}
              items={paidClientDebts}
              getRowKey={(item) => item.id}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}

        {vouchersAsPayment.length > 0 && (
          <section className="vm-section flex-column g-05">
            <div className="vm-section-head flex align-items-center">
              <span className="qv-span">
                Status Vouchera zostanie przywrócony:
              </span>
            </div>
            <DataList<Voucher>
              columns={paymentVoucherColumns}
              items={vouchersAsPayment}
              getRowKey={(item) => item.id}
              hideHeader={true}
              className="vm-cart-list"
            />
          </section>
        )}
      </div>

      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
    </Modal>
  );
}

export default RemoveVisitModal;
