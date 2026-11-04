import Modal from "../Modal";
import LogContent from "../Business/LogContent";
import { getActionVisual } from "../Business/LogDataList";
import { AuditLog } from "../../models/audit_log";
import { formatTimestamp } from "../../utils/dateUtils";

export interface LogPreviewModalProps {
  log: AuditLog;
  onClose: () => void;
  className?: string;
}

export function LogPreviewModal({
  log,
  onClose,
  className = "",
}: LogPreviewModalProps) {
  const { Icon, className: iconClassName } = getActionVisual(log);

  return (
    <Modal
      title={log.entityId != null ? `${log.entityType} #${log.entityId}` : log.entityType}
      onClose={onClose}
      size="2xl"
      height="tall"
      bodyClassName="nested lp-body"
      className={className}
    >
      <section className="log-preview-head flex align-items-center g-2">
        <span className="flex align-items-center g-05">
          <Icon className={`dl-icon-1 ${iconClassName}`} />
          <span className="log-changed-fields">{log.action}</span>
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Użytkownik:</span>
          <span className="dl-dim">{log.performedBy}</span>
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Data:</span>
          <span className="dl-dim">{formatTimestamp(log.timestamp)}</span>
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">IP:</span>
          <span className="dl-dim">{log.ipAddress}</span>
        </span>
        {log.entityKeyTrait && (
          <span className="flex align-items-center g-05">
            <span className="log-meta-label">Nazwa:</span>
            <span className="dl-dim">{log.entityKeyTrait}</span>
          </span>
        )}
      </section>

      <div className="log-preview-body scrollbar f-1 min-height-0">
        <LogContent log={log} />
      </div>
    </Modal>
  );
}

export default LogPreviewModal;
