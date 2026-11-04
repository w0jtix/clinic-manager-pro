import { useEffect, useState, useCallback } from "react";
import Modal from "../Modal";
import DataList, { Column } from "../DataList";
import { Voucher, VoucherStatus } from "../../models/voucher";
import VoucherService from "../../services/VoucherService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface AvailableVouchersModalProps {
  onClose: () => void;
  onSave: (voucher: Voucher | null) => void;
  attachedVoucher: Voucher | null;
  className?: string;
}

const sortByIssueDesc = (data: Voucher[]): Voucher[] =>
  [...data].sort(
    (a, b) => new Date(b.issueDate).getTime() - new Date(a.issueDate).getTime()
  );

export function AvailableVouchersModal({
  onClose,
  onSave,
  attachedVoucher,
  className = "",
}: AvailableVouchersModalProps) {
  const [availableVouchers, setAvailableVouchers] = useState<Voucher[]>([]);
  const [expiredVouchers, setExpiredVouchers] = useState<Voucher[]>([]);
  const [selectedVoucher, setSelectedVoucher] = useState<Voucher | null>(
    attachedVoucher
  );
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchAvailableVouchers = async (): Promise<void> => {
    VoucherService.getVouchers({ status: VoucherStatus.ACTIVE })
      .then((data) => setAvailableVouchers(sortByIssueDesc(data)))
      .catch((error) => {
        setAvailableVouchers([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching active Vouchers: ", error);
      });
  };
  const fetchExpiredVouchers = async (): Promise<void> => {
    VoucherService.getVouchers({ status: VoucherStatus.EXPIRED })
      .then((data) => setExpiredVouchers(sortByIssueDesc(data)))
      .catch((error) => {
        setExpiredVouchers([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching expired Vouchers: ", error);
      });
  };

  useEffect(() => {
    fetchAvailableVouchers();
    fetchExpiredVouchers();
  }, []);

  const handleSelect = useCallback((voucher: Voucher) => {
    setSelectedVoucher((prev) => (prev?.id === voucher.id ? null : voucher));
  }, []);

  const items = [...availableVouchers, ...expiredVouchers];

  const isSelected = (item: Voucher): boolean =>
    selectedVoucher?.id === item.id;

  const statusBadge = (item: Voucher) =>
    item.status === VoucherStatus.ACTIVE ? (
      <span className="rv-status active">AKTYWNY</span>
    ) : (
      <span className="rv-status expired">NIEAKTYWNY</span>
    );

  const statusColumn: Column<Voucher> = {
    key: "Status",
    width: isMobile ? "4.75rem" : "5.5rem",
    align: "center",
    render: (item) => statusBadge(item),
  };

  const clientColumn: Column<Voucher> = {
    key: "Klient",
    grow: true,
    render: (item) => {
      const nameNode = (
        <span className={`dl-name ${isSelected(item) ? "pc-y" : ""}`}>
          {item.client.firstName} {item.client.lastName}
          {item.client.isDeleted && <span className="dl-dim"> (usunięty)</span>}
        </span>
      );

      if (!isMobile) return nameNode;

      return (
        <span className="av-name-stack flex-column mw-0">
          {nameNode}
          <span className="av-name-meta flex align-items-center g-05">
            <span className={`dl-dim ${isSelected(item) ? "pc-y" : ""}`}>
              Ważny do {new Date(item.expiryDate).toLocaleDateString("pl-PL")}
            </span>
          </span>
        </span>
      );
    },
  };

  const expiryColumn: Column<Voucher> = {
    key: "Ważny do",
    width: "8rem",
    align: "center",
    render: (item) => (
      <span className={`dl-dim ${isSelected(item) ? "pc-y" : ""}`}>
        {new Date(item.expiryDate).toLocaleDateString("pl-PL")}
      </span>
    ),
  };

  const valueColumn: Column<Voucher> = {
    key: "Wartość",
    width: isMobile ? "3.75rem" : "6rem",
    align: "end",
    render: (item) => (
      <span className={`vc-price nowrap ${isSelected(item) ? "pc-y" : ""}`}>
        {item.value} zł
      </span>
    ),
  };

  const columns: Column<Voucher>[] = isMobile
    ? [clientColumn, statusColumn, valueColumn]
    : [statusColumn, clientColumn, expiryColumn, valueColumn];

  return (
    <Modal
      title="Przypisz Voucher do Płatności"
      onClose={onClose}
      size="lg"
      height="tall"
      bodyClassName="nested"
      confirmText="Zatwierdź"
      onConfirm={() => onSave(selectedVoucher)}
      className={className}
    >
      <DataList<Voucher>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        onRowClick={handleSelect}
        isRowDisabled={(item) => item.status === VoucherStatus.EXPIRED}
        hideHeader={isMobile}
        emptyText="Brak dostępnych voucherów"
        className="av-list"
      />
    </Modal>
  );
}

export default AvailableVouchersModal;
