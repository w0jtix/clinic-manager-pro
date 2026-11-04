import React, { useMemo } from "react";
import {
  CirclePlus,
  SquarePen,
  Trash2,
  LogIn,
  ShieldAlert,
  ShieldBan,
  ChevronsUp,
  ChevronsDown,
  MailCheck,
  ShieldCheck,
  ShieldOff,
  KeyRound,
  Smartphone,
  Tablet,
  PauseCircle,
  PlayCircle,
  Clock,
  LucideIcon,
} from "lucide-react";
import DataList, { Column } from "../DataList";
import DeviceTag, { UnknownDeviceTag } from "../DeviceTag";
import { formatDate, formatTimestamp } from "../../utils/dateUtils";
import { AuditAction, AuditLog } from "../../models/audit_log";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface LogDataListProps {
  logs: AuditLog[];
  onRowClick?: (log: AuditLog) => void;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
  isLoading?: boolean;
  className?: string;
}

interface ActionVisual {
  Icon: LucideIcon;
  className: string;
}

const MFA_VISUALS: Record<string, ActionVisual> = {
  "User-2FA-Enabled": { Icon: ShieldCheck, className: "log-icon create" },
  "User-2FA-Disabled": { Icon: ShieldOff, className: "log-icon delete" },
  "User-2FA-Reset": { Icon: ShieldOff, className: "log-icon delete" },
  "User-2FA-BackupCodes-Regenerated": { Icon: KeyRound, className: "log-icon info" },
  "User-2FA-BackupCode-Used": { Icon: KeyRound, className: "log-icon alert" },
  "User-2FA-TrustedDevice-Revoked": { Icon: Smartphone, className: "log-icon delete" },
  "User-Password": { Icon: KeyRound, className: "log-icon info" },
  "User-CompanyPin": { Icon: KeyRound, className: "log-icon info" },
  "Device-Code-Created": { Icon: KeyRound, className: "log-icon info" },
  "Device-Code-Cancelled": { Icon: KeyRound, className: "log-icon delete" },
  "Device-Registered": { Icon: Tablet, className: "log-icon create" },
  "Device-Renamed": { Icon: SquarePen, className: "log-icon info" },
  "Device-Revoked": { Icon: Tablet, className: "log-icon delete" },
  "Device-Deleted": { Icon: Trash2, className: "log-icon delete" },
  "UserLoginDeviceGate-Enabled": { Icon: ShieldCheck, className: "log-icon create" },
  "UserLoginDeviceGate-Disabled": { Icon: ShieldOff, className: "log-icon delete" },
  "UserLoginDeviceGate-Paused": { Icon: PauseCircle, className: "log-icon alert" },
  "UserLoginDeviceGate-Resumed": { Icon: PlayCircle, className: "log-icon create" },
  "WorkingHours-Updated": { Icon: Clock, className: "log-icon info" },
};

export const getActionVisual = (log: AuditLog): ActionVisual => {
  const mfaVisual = log.entityType ? MFA_VISUALS[log.entityType] : undefined;
  if (mfaVisual) {
    return mfaVisual;
  }
  if (log.entityType?.startsWith("Security-2FA") || log.entityType?.startsWith("Security-Device")) {
    return { Icon: ShieldAlert, className: "log-icon alert" };
  }
  if (log.entityType === "User-Login") {
    return { Icon: LogIn, className: "log-icon info" };
  }
  const demotion =
    log.entityType === "Master-Demotion" ||
    log.entityType === "User-Demotion" ||
    (log.entityType === "User-Elevation" && (log.newValue ?? "").includes("Wyłączono"));
  if (demotion) {
    return { Icon: ChevronsDown, className: "log-icon delete" };
  }
  if (log.entityType === "Master-Elevation" || log.entityType === "User-Elevation") {
    return { Icon: ChevronsUp, className: "log-icon create" };
  }
  if (log.entityType === "Security-Block" || log.entityType?.startsWith("Security-Pin")) {
    return { Icon: ShieldAlert, className: "log-icon alert" };
  }
  if (log.entityType === "Security-ShadowBan") {
    return { Icon: ShieldBan, className: "log-icon alert" };
  }
  if (log.action === AuditAction.SECURITY) {
    return { Icon: ShieldAlert, className: "log-icon alert" };
  }
  if (log.action === AuditAction.CREATE) {
    return { Icon: CirclePlus, className: "log-icon create" };
  }
  if (log.action === AuditAction.UPDATE) {
    return { Icon: SquarePen, className: "log-icon update" };
  }
  if (log.action === AuditAction.SEND) {
    return { Icon: MailCheck, className: "log-icon send" };
  }
  return { Icon: Trash2, className: "log-icon delete" };
};

export function LogDataList({
  logs = [],
  onRowClick,
  onScroll,
  isLoading = false,
  className = "",
}: LogDataListProps) {
  const { isMobile } = useBreakpoint();

  const mobileColumns = useMemo<Column<AuditLog>[]>(
    () => [
      {
        key: "entity",
        grow: true,
        render: (log) => {
          const { Icon, className } = getActionVisual(log);
          return (
            <span className="log-m-entity flex align-items-center g-05 mw-0">
              <Icon className={`dl-icon-1 ${className}`} />
              <span className="exp-m-stack flex-column mw-0">
                <span className="flex align-items-center g-05 mw-0">
                  <span className="dl-name">{log.entityType}</span>
                  {log.clinicDeviceName ? (
                    <DeviceTag name={log.clinicDeviceName} />
                  ) : (
                    <UnknownDeviceTag />
                  )}
                </span>
                <span className="exp-m-sub flex align-items-center g-05 mw-0">
                  <span className="dl-dim">{log.performedBy}</span>
                  {log.entityKeyTrait && (
                    <span className="dl-dim log-m-trait">{log.entityKeyTrait}</span>
                  )}
                </span>
              </span>
            </span>
          );
        },
      },
      {
        key: "timestamp",
        width: "4rem",
        align: "end",
        render: (log) => (
          <span className="flex-column align-items-end">
            <span className="dl-dim">{formatDate(log.timestamp)}</span>
            <span className="dl-dim">
              {new Date(log.timestamp).toLocaleTimeString("pl-PL")}
            </span>
          </span>
        ),
      },
    ],
    []
  );

  const columns = useMemo<Column<AuditLog>[]>(
    () => [
      {
        key: "action",
        header: "",
        width: "2.5rem",
        align: "center",
        render: (log) => {
          const { Icon, className } = getActionVisual(log);
          return <Icon className={`dl-icon-1 ${className}`} />;
        },
      },
      {
        key: "entityType",
        header: "Obiekt",
        width: "17.25rem",
        render: (log) => (
          <span className="log-obj-cell">
            <span className="dl-name">{log.entityType}</span>
            {log.clinicDeviceName ? (
              <DeviceTag name={log.clinicDeviceName} />
            ) : (
              <UnknownDeviceTag />
            )}
          </span>
        ),
      },
      {
        key: "entityKeyTrait",
        header: "Nazwa",
        width: "16.75rem",
        render: (log) => (
          <span className="dl-dim">{log.entityKeyTrait ?? ""}</span>
        ),
      },
      {
        key: "changedFields",
        header: "Zmiana",
        grow: true,
        render: (log) => (
          <span className="log-changed-fields">{log.changedFields}</span>
        ),
      },
      {
        key: "performedBy",
        header: "Użytkownik",
        width: "8rem",
        align: "center",
        render: (log) => <span className="dl-dim">{log.performedBy}</span>,
      },
      {
        key: "timestamp",
        header: "Data",
        width: "10rem",
        align: "center",
        render: (log) => (
          <span className="dl-dim">{formatTimestamp(log.timestamp)}</span>
        ),
      },
      {
        key: "ipAddress",
        header: "IP",
        width: "8rem",
        align: "center",
        render: (log) => <span className="dl-dim">{log.ipAddress}</span>,
      },
    ],
    []
  );

  return (
    <DataList<AuditLog>
      columns={isMobile ? mobileColumns : columns}
      hideHeader={isMobile}
      items={logs}
      getRowKey={(log) => log.id}
      onRowClick={onRowClick}
      onScroll={onScroll}
      isLoading={isLoading}
      emptyText="Brak logów"
      className={`log-list ${className}`}
    />
  );
}

export default LogDataList;
