import { ShieldCheck, UserRound, Zap, ZapOff } from "lucide-react";
import ActionButton from "../ActionButton";
import { useCompanyMode } from "../../hooks/useCompanyMode";

export function CompanyModePanel() {
  const { active, busy, openPinModal, dropCompanyMode, pinModal } = useCompanyMode();

  return (
    <section className="pf-panel pf-company-panel flex-column g-1 height-fit-content">
      <div className="flex align-items-center space-between g-05">
        <h2 className="pf-panel-title m-0">Tryb firmowy</h2>
        <span className={`pf-company-state flex align-items-center g-025 ${active ? "active" : ""}`}>
          {active ? <ShieldCheck className="dl-icon" /> : <UserRound className="dl-icon" />}
          {active ? "Konto firmowe" : "Konto pracownicze"}
        </span>
      </div>
      <div className="pf-panel-body flex justify-center">
        {active ? (
          <ActionButton
            text="Wyłącz tryb firmowy"
            onClick={dropCompanyMode}
            disabled={busy}
            className="pf-company-leave"
          >
            <ZapOff className="dl-icon-1" />
          </ActionButton>
        ) : (
          <ActionButton
            text="Przełącz na konto firmowe"
            onClick={openPinModal}
            disabled={busy}
            className="pf-company-enter"
          >
            <Zap className="dl-icon-1" />
          </ActionButton>
        )}
      </div>
      {pinModal}
    </section>
  );
}

export default CompanyModePanel;
