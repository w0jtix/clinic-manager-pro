import React, { useState } from "react";
import ActionButton from "../ActionButton";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import { BonusVisit } from "../../models/employee";
import { Eye } from "lucide-react";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { formatNumber } from "../../utils/formatUtils";

export interface BonusVisitTableProps {
  visits: BonusVisit[];
}

export function BonusVisitTable({ visits }: BonusVisitTableProps) {
  const [previewVisitId, setPreviewVisitId] = useState<string | number | null>(
    null
  );
  const { isMobile } = useBreakpoint();

  const previewButton = (visit: BonusVisit) => (
    <ActionButton
      iconTitle="Podgląd Wizyty"
      disableText={true}
      disableBorder={true}
      onClick={(e: React.MouseEvent) => {
        e.stopPropagation();
        setPreviewVisitId(visit.visitId);
      }}
    >
      <Eye className="dl-icon" />
    </ActionButton>
  );

  return (
    <>
      {!isMobile && (
        <div className="pv-visit-head align-items-center g-05">
          <span>Klient</span>
          <span className="c text-align-end">Płatn.</span>
          <span className="c text-align-end">Voucher</span>
          <span className="c text-align-end">Produkty</span>
          <span className="c text-align-end">Wartość</span>
          <span></span>
        </div>
      )}
      {visits.map((visit) =>
        isMobile ? (
          <div className="pv-visit pv-visit-m align-items-center g-05" key={visit.visitId}>
            <span className="pv-visit-m-main flex-column mw-0">
              <span className="name" title={visit.clientName}>
                {visit.clientName}
              </span>
              <span className="pv-visit-m-sub flex align-items-center">
                <span title="Suma płatności.">
                  <span className="pv-visit-m-lbl">Płatn.</span>
                  <span className="pos">+{formatNumber(visit.paymentsSum)}</span>
                </span>
                <span title="Suma płatności Voucherem. Voucher rozliczany jest w momencie sprzedaży, a nie realizacji.">
                  <span className="pv-visit-m-lbl">Voucher</span>
                  <span className="neg">-{formatNumber(visit.voucherPaymentsSum)}</span>
                </span>
                <span title="Suma wartości sprzedanych Produktów. Premia Produktowa liczona jest osobno.">
                  <span className="pv-visit-m-lbl">Prod.</span>
                  <span className="neg">-{formatNumber(visit.productsValue)}</span>
                </span>
              </span>
            </span>
            <span className="r val">{formatNumber(visit.adjustedRevenue)} zł</span>
            {previewButton(visit)}
          </div>
        ) : (
          <div className="pv-visit align-items-center g-05" key={visit.visitId}>
            <span className="name" title={visit.clientName}>
              {visit.clientName}
            </span>
            <span className="r pos" title="Suma płatności.">
              +{formatNumber(visit.paymentsSum)}
            </span>
            <span
              className="r neg"
              title="Suma płatności Voucherem. Voucher rozliczany jest w momencie sprzedaży, a nie realizacji."
            >
              -{formatNumber(visit.voucherPaymentsSum)}
            </span>
            <span
              className="r neg"
              title="Suma wartości sprzedanych Produktów. Premia Produktowa liczona jest osobno."
            >
              -{formatNumber(visit.productsValue)}
            </span>
            <span className="r val">{formatNumber(visit.adjustedRevenue)} zł</span>
            {previewButton(visit)}
          </div>
        )
      )}
      {previewVisitId != null && (
        <VisitPreviewModal
          onClose={() => setPreviewVisitId(null)}
          visitId={previewVisitId}
        />
      )}
    </>
  );
}

export default BonusVisitTable;
