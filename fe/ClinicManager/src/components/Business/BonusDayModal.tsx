import Modal from "../Modal";
import BonusVisitTable from "./BonusVisitTable";
import { formatDate, getWeekday } from "../../utils/dateUtils";
import { BonusVisit } from "../../models/employee";
import { formatNumber } from "../../utils/formatUtils";

export interface BonusDayModalProps {
  date: string;
  visits: BonusVisit[];
  onClose: () => void;
}

export function BonusDayModal({ date, visits, onClose }: BonusDayModalProps) {
  const daySum = visits.reduce((s, v) => s + v.adjustedRevenue, 0);

  return (
    <Modal
      title={
        <span className="flex align-items-center g-05">
          <span className="pv-day-title pc-y">
            {formatDate(date)} - {getWeekday(date)}
          </span>
          <span className="pv-day-sum">+ {formatNumber(daySum)} zł</span>
        </span>
      }
      onClose={onClose}
      size="md"
      hideFooter
      bodyClassName="pv-day-body"
    >
      <BonusVisitTable visits={visits} />
    </Modal>
  );
}

export default BonusDayModal;
