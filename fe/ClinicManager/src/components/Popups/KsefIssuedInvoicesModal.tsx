import React, { useState, useEffect, useCallback, useMemo } from "react";
import Modal from "../Modal";
import KsefIssuedInvoiceDataList, {
  IssuedInvoiceRow,
  toRowFromKsef,
  toRowFromNamed,
} from "../Business/KsefIssuedInvoiceDataList";
import KsefIssuedInvoiceModal from "./KsefIssuedInvoiceModal";
import NamedInvoiceModal from "./NamedInvoiceModal";
import NamedInvoiceSendModal from "./NamedInvoiceSendModal";
import RemoveModal from "./RemoveModal";
import NamedInvoiceService from "../../services/NamedInvoiceService";
import { openNamedInvoicePdf } from "../../utils/namedInvoiceUtils";
import { NamedInvoice } from "../../models/named_invoice";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import {
  KsefIssuedInvoice,
  KsefIssuedInvoiceFilterDTO,
} from "../../models/ksef_issued_invoice";
import KsefIssuedInvoiceService from "../../services/KsefIssuedInvoiceService";
import { MONTHS } from "../../utils/dateUtils";

export interface KsefIssuedInvoicesModalProps {
  year: number | null | undefined;
  month: number | null | undefined;
  onClose: () => void;
  className?: string;
}

export function KsefIssuedInvoicesModal({
  year,
  month,
  onClose,
  className = "",
}: KsefIssuedInvoicesModalProps) {
  const { showAlert } = useAlert();
  const [invoices, setInvoices] = useState<KsefIssuedInvoice[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [previewInvoiceId, setPreviewInvoiceId] = useState<number | null>(null);
  const [namedInvoices, setNamedInvoices] = useState<NamedInvoice[]>([]);
  const [editNamedInvoiceId, setEditNamedInvoiceId] = useState<number | null>(null);
  const [sendNamedInvoice, setSendNamedInvoice] = useState<NamedInvoice | null>(null);
  const [removeNamedInvoiceId, setRemoveNamedInvoiceId] = useState<number | null>(
    null
  );

  const fetchInvoices = useCallback(
    (pageNum: number = 0, append: boolean = false) => {
      setLoading(true);
      const filter: KsefIssuedInvoiceFilterDTO = {
        year: year ?? null,
        month: month ?? null,
      };
      KsefIssuedInvoiceService.getInvoices(filter, pageNum, 30)
        .then((data) => {
          const content = data?.content ?? [];
          setInvoices((prev) => (append ? [...prev, ...content] : content));
          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch(() => {
          if (!append) setInvoices([]);
          setHasMore(false);
          showAlert("Błąd pobierania faktur wystawionych.", AlertType.ERROR);
        })
        .finally(() => setLoading(false));
    },
    [year, month]
  );

  const fetchNamedInvoices = useCallback(() => {
    if (!year || !month) {
      setNamedInvoices([]);
      return;
    }
    NamedInvoiceService.getByPeriod(year, month)
      .then((data) => setNamedInvoices(Array.isArray(data) ? data : []))
      .catch(() => setNamedInvoices([]));
  }, [year, month]);

  useEffect(() => {
    fetchInvoices(0, false);
  }, [fetchInvoices]);

  useEffect(() => {
    fetchNamedInvoices();
  }, [fetchNamedInvoices]);

  // Faktury imienne przychodza jako pelna lista okresu, faktury z KSeF sa
  // stronicowane - scalamy je dopiero na potrzeby wyswietlenia i sortujemy
  // wspolnie po dacie wystawienia.
  const rows = useMemo<IssuedInvoiceRow[]>(() => {
    const merged = [
      ...invoices.map(toRowFromKsef),
      ...namedInvoices.map(toRowFromNamed),
    ];
    return merged.sort((a, b) => {
      const cmp = (b.issueDate ?? "").localeCompare(a.issueDate ?? "");
      return cmp !== 0 ? cmp : b.rowKey.localeCompare(a.rowKey);
    });
  }, [invoices, namedInvoices]);

  const handleRemoveNamedInvoice = useCallback(async () => {
    if (removeNamedInvoiceId === null) return;
    try {
      await NamedInvoiceService.delete(removeNamedInvoiceId);
      showAlert("Faktura imienna usunięta!", AlertType.SUCCESS);
      setRemoveNamedInvoiceId(null);
      fetchNamedInvoices();
    } catch {
      showAlert("Błąd usuwania faktury!", AlertType.ERROR);
    }
  }, [removeNamedInvoiceId, fetchNamedInvoices, showAlert]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !loading) {
        fetchInvoices(page + 1, true);
      }
    },
    [hasMore, loading, page, fetchInvoices]
  );

  const monthName = month
    ? (MONTHS.find((m) => m.id === month)?.name ?? null)
    : null;
  const period = [monthName, year].filter(Boolean).join(" ");

  return (
    <>
      <Modal
        title={
          <span className="flex align-items-center g-05">
            <span className="pv-day-title pc-y">Faktury wystawione</span>
            {period && <span className="pv-day-title period">{period}</span>}
          </span>
        }
        onClose={onClose}
        size="2xl"
        height="tall"
        bodyClassName="nested"
        hideFooter
        className={className}
      >
        <KsefIssuedInvoiceDataList
          items={rows}
          onScroll={handleScroll}
          isLoading={loading}
          onOpenPreview={(row) =>
            row.source === "NAMED"
              ? setEditNamedInvoiceId(row.id)
              : setPreviewInvoiceId(row.id)
          }
          onOpenPdf={async (row) => {
            const ok = await openNamedInvoicePdf(row.id, row.invoiceNumber);
            if (!ok) showAlert("Błąd generowania PDF.", AlertType.ERROR);
          }}
          onRemove={(row) => setRemoveNamedInvoiceId(row.id)}
          onSendEmail={(row) => setSendNamedInvoice(row.named ?? null)}
        />
      </Modal>

      {previewInvoiceId !== null && (
        <KsefIssuedInvoiceModal
          invoiceId={previewInvoiceId}
          onClose={() => setPreviewInvoiceId(null)}
        />
      )}
      {removeNamedInvoiceId !== null && (
        <RemoveModal
          onClose={() => setRemoveNamedInvoiceId(null)}
          warningText={"Faktura imienna zostanie trwale usunięta."}
          handleRemove={handleRemoveNamedInvoice}
        />
      )}
      {editNamedInvoiceId !== null && (
        <NamedInvoiceModal
          invoiceId={editNamedInvoiceId}
          onClose={() => setEditNamedInvoiceId(null)}
          onSaved={() => fetchNamedInvoices()}
        />
      )}
      {sendNamedInvoice !== null && (
        <NamedInvoiceSendModal
          invoiceId={sendNamedInvoice.id}
          invoice={sendNamedInvoice}
          onClose={() => setSendNamedInvoice(null)}
          onSent={() => fetchNamedInvoices()}
        />
      )}
    </>
  );
}

export default KsefIssuedInvoicesModal;
