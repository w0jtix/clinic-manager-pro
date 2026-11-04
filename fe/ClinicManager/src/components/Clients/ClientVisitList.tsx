import React, { useState } from "react";
import { Eye, Rocket, Crown, UserX, Clock, FileText, SquarePen, Mail } from "lucide-react";
import ActionButton from "../ActionButton";
import RowActionMenu, { RowActionItem } from "../RowActionMenu";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import NamedInvoiceModal from "../Popups/NamedInvoiceModal";
import NamedInvoiceSendModal from "../Popups/NamedInvoiceSendModal";
import { openNamedInvoicePdf } from "../../utils/namedInvoiceUtils";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useRoles } from "../../hooks/useRoles";
import { Visit } from "../../models/visit";
import { PaymentStatus } from "../../models/payment";
import { formatDate, formatDateShort } from "../../utils/dateUtils";
import { useIsMobile } from "../../hooks/useIsMobile";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatAmount } from "../../utils/formatUtils";

export interface ClientVisitListProps {
  visits: Visit[];
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  onInvoiceCreated?: () => void;
}

const PAY_LABEL: Record<PaymentStatus, string> = {
  [PaymentStatus.PAID]: "Opłacona",
  [PaymentStatus.PARTIAL]: "Częściowa",
  [PaymentStatus.UNPAID]: "Nieopłacona",
};
const PAY_CLASS: Record<PaymentStatus, string> = {
  [PaymentStatus.PAID]: "paid",
  [PaymentStatus.PARTIAL]: "partial",
  [PaymentStatus.UNPAID]: "unpaid",
};

export function ClientVisitList({
  visits,
  onScroll,
  isLoading = false,
  onInvoiceCreated,
}: ClientVisitListProps) {
  const [previewVisitId, setPreviewVisitId] = useState<number | null>(null);
  const [invoiceVisitId, setInvoiceVisitId] = useState<number | null>(null);
  const [invoiceId, setInvoiceId] = useState<number | null>(null);
  const [sendInvoiceId, setSendInvoiceId] = useState<number | null>(null);
  const { showAlert } = useAlert();

  const openInvoicePdf = async (namedInvoiceId: number) => {
    const ok = await openNamedInvoicePdf(namedInvoiceId);
    if (!ok) showAlert("Błąd generowania PDF.", AlertType.ERROR);
  };
  const { isMaster } = useRoles();
  const { isCompact } = useBreakpoint();
  const isNarrow500 = useIsMobile(501);

  const invoiceItems = (visit: Visit): RowActionItem[] =>
    visit.namedInvoiceId != null
      ? [
          {
            label: "Podgląd faktury imiennej",
            icon: <FileText className="dl-icon" />,
            onClick: () => openInvoicePdf(visit.namedInvoiceId!),
          },
          {
            label: "Edytuj fakturę imienną",
            icon: <SquarePen className="dl-icon c-blue" />,
            onClick: () => setInvoiceId(visit.namedInvoiceId!),
          },
          {
            label: "Wyślij fakturę emailem",
            icon: <Mail className="dl-icon" />,
            onClick: () => setSendInvoiceId(visit.namedInvoiceId!),
          },
        ]
      : [
          {
            label: "Utwórz fakturę imienną",
            icon: <FileText className="dl-icon" />,
            onClick: () => setInvoiceVisitId(visit.id),
          },
        ];

  return (
    <>
      {!isCompact && (
        <div className="cl-vhead">
          <span>Data</span>
          <span>Podolog</span>
          <span>Usługi i produkty</span>
          <span className="r">Wartość</span>
          <span className="c">Płatność</span>
          <span></span>
        </div>
      )}
      <div className="cl-vscroll scrollbar" onScroll={onScroll}>
        {!isLoading && visits.length === 0 && (
          <div className="cl-empty mb-025 flex f-1 align-items-center justify-center">
            Brak wizyt
          </div>
        )}
        {visits.map((visit) => {
          const services = visit.items ?? [];
          const products = visit.sale?.items ?? [];
          const late = (visit.delayTime ?? 0) > 0;
          return (
            <div className={`cl-vrow ${isNarrow500 ? "narrow" : ""}`} key={visit.id}>
              {isCompact ? (
                <span className="cl-vdate-stack">
                  <span className="cl-vdate tnum">
                    {isNarrow500 ? formatDateShort(visit.date) : formatDate(visit.date)}
                  </span>
                  <span className="cl-vemp-sub">
                    {visit.employee ? `${visit.employee.name}` : "-"}
                  </span>
                </span>
              ) : (
                <>
                  <span className="cl-vdate tnum">{formatDate(visit.date)}</span>
                  <span className="cl-vemp">
                    <span className="dl-dim">{visit.employee ? `${visit.employee.name}` : "-"}</span>
                  </span>
                </>
              )}
              <span className="cl-vserv flex-column align-items-start">
                {visit.absence ? (
                  <div className="flex align-items-center g-05">
                    <div className="flex align-items-center">
                      <span className="cl-vicons">
                        {visit.isBoost && (
                          <Rocket
                            className="dl-icon-1"
                            style={{ color: "var(--color-boost)" }}
                          />
                        )}
                        {visit.isVip && <Crown className="dl-icon-1 c-blue" />}
                        {late && (
                          <Clock
                            className="dl-icon-1"
                            style={{ color: "var(--yellow-clinicmanager)" }}
                          />
                        )}
                      </span>
                    </div>
                    <div className="cl-v-abs">
                    {visit.absence && <UserX className="dl-icon-1 c-red" />}
                    <span className="cl-chip absence">Nieobecność</span>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="flex align-items-center">
                      <span className="cl-vicons">
                        {visit.isBoost && (
                          <Rocket
                            className="dl-icon-1"
                            style={{ color: "var(--color-boost)" }}
                          />
                        )}
                        {visit.isVip && <Crown className="dl-icon-1 c-blue" />}
                        {late && (
                          <Clock
                            className="dl-icon-1"
                            style={{ color: "var(--yellow-clinicmanager)" }}
                          />
                        )}
                        {isMaster && visit.namedInvoiceId != null && (
                          <FileText
                            className="dl-icon-1 pc-y pointer"
                            aria-label="Faktura imienna - otwórz PDF"
                            onClick={(e: React.MouseEvent) => {
                              e.stopPropagation();
                              openInvoicePdf(visit.namedInvoiceId!);
                            }}
                          />
                        )}
                      </span>
                    </div>
                    <div className="cl-vchips flex-column align-items-start">
                    {services.map((item) => (
                      
                        <span className="cl-chip" key={`s-${item.id}`}>
                          <span
                            className="cl-dot"
                            style={{
                              backgroundColor: item.service?.category?.color
                                ? `rgb(${item.service.category.color})`
                                : "#8f76e6",
                            }}
                          />
                          <span className="cl-chip-name">{item.name}</span>
                        </span>                     
                    ))}
                    </div>
                    <div className="cl-vchips flex-column align-items-start">
                    {products.map((item, i) => (
                      
                        <span className="cl-chip" key={`p-${i}`}>
                          <span
                            className="cl-dot"
                            style={{
                              backgroundColor: item.product?.category?.color
                                ? `rgb(${item.product.category.color})`
                                : "#ffffff99",
                            }}
                          />
                          <span className="cl-chip-name">{item.name}</span>
                        </span>
                      
                    ))}
                    </div>
                  </>
                )}
              </span>
              {isCompact ? (
                <span className="cl-vval-stack">
                  <span className="cl-vval tnum">{formatAmount(visit.totalValue)} zł</span>
                  <span className={`cl-pay ${PAY_CLASS[visit.paymentStatus]}`}>
                    {PAY_LABEL[visit.paymentStatus]}
                  </span>
                </span>
              ) : (
                <span className="cl-vval r tnum">{formatAmount(visit.totalValue)} zł</span>
              )}
              {isCompact ? (
                <span className="flex align-items-center justify-center">
                  <RowActionMenu
                    items={[
                      {
                        label: "Podgląd wizyty",
                        icon: <Eye className="dl-icon" />,
                        onClick: () => setPreviewVisitId(visit.id),
                      },
                      ...(isMaster && !visit.absence ? invoiceItems(visit) : []),
                    ]}
                  />
                </span>
              ) : (
                <>
                  <span className="c flex align-items-center justify-center">
                    <span className={`cl-pay ${PAY_CLASS[visit.paymentStatus]}`}>
                      {PAY_LABEL[visit.paymentStatus]}
                    </span>
                  </span>
                  <span className="flex align-items-center justify-center">
                    <ActionButton
                      iconTitle="Podgląd Wizyty"
                      disableText={true}
                      disableBorder={true}
                      onClick={(e: React.MouseEvent) => {
                        e.stopPropagation();
                        setPreviewVisitId(visit.id);
                      }}
                    >
                      <Eye className="dl-icon" />
                    </ActionButton>
                    {isMaster && !visit.absence && (
                      <RowActionMenu items={invoiceItems(visit)} />
                    )}
                  </span>
                </>
              )}
            </div>
          );
        })}
      </div>
      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
      {invoiceVisitId != null && (
        <NamedInvoiceModal
          visitId={invoiceVisitId}
          onClose={() => setInvoiceVisitId(null)}
          onSaved={() => onInvoiceCreated?.()}
        />
      )}
      {invoiceId != null && (
        <NamedInvoiceModal
          invoiceId={invoiceId}
          onClose={() => setInvoiceId(null)}
          onSaved={() => onInvoiceCreated?.()}
        />
      )}
      {sendInvoiceId != null && (
        <NamedInvoiceSendModal
          invoiceId={sendInvoiceId}
          onClose={() => setSendInvoiceId(null)}
          onSent={() => onInvoiceCreated?.()}
        />
      )}
    </>
  );
}

export default ClientVisitList;
