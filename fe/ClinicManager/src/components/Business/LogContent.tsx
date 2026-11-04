import { useState } from "react";
import { ChevronDown } from "lucide-react";
import DeviceTag from "../DeviceTag";
import { AuditAction, AuditLog } from "../../models/audit_log";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface LogContentProps {
  log: AuditLog;
}

export function LogContent({ log }: LogContentProps) {
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [collapsed, setCollapsed] = useState<{ old: boolean; new: boolean }>({
    old: false,
    new: false,
  });
  const { isCompact } = useBreakpoint();

  const renderPaneTitle = (side: "old" | "new", label: string) => (
    <div
      className={`log-diff-title ${isCompact ? "flex align-items-center space-between pointer" : ""}`}
      onClick={
        isCompact
          ? () => setCollapsed((prev) => ({ ...prev, [side]: !prev[side] }))
          : undefined
      }
    >
      {label}
      {isCompact && (
        <ChevronDown
          className={`dl-icon log-diff-caret ${collapsed[side] ? "" : "open"}`}
        />
      )}
    </div>
  );

  const toggleExpand = (key: string, side: "old" | "new") => {
    const fullKey = `${side}-${key}`;
    setExpandedKeys((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(fullKey)) {
        newSet.delete(fullKey);
      } else {
        newSet.add(fullKey);
      }
      return newSet;
    });
  };

  const parseValue = (value: string | null): Record<string, unknown> => {
    if (!value) return {};
    try {
      return JSON.parse(value);
    } catch {
      return {};
    }
  };

  const oldData = parseValue(log.oldValue);
  const newData = parseValue(log.newValue);

  const allKeys = Array.from(
    new Set([...Object.keys(oldData), ...Object.keys(newData)]),
  );

  const prettyPrint = (obj: unknown): string => {
    const json = JSON.stringify(obj, null, 2);
    return json.replace(/\},\n(\s+)\{/g, "},\n\n$1{");
  };

  const formatValue = (
    value: unknown,
    key: string,
    side: "old" | "new",
  ): React.ReactNode => {
    if (value === null || value === undefined) return "null";

    if (typeof value === "object") {
      const fullKey = `${side}-${key}`;
      const isExpanded = expandedKeys.has(fullKey);

      return (
        <span
          className="log-json-toggle pointer"
          onClick={() => toggleExpand(key, side)}
          title={isExpanded ? "Zwiń" : "Rozwiń"}
        >
          {isExpanded ? (
            <pre className="log-json-pretty">{prettyPrint(value)}</pre>
          ) : (
            <span className="log-json-compact">{JSON.stringify(value)}</span>
          )}
        </span>
      );
    }

    return String(value);
  };

  const isFieldRemoved = (key: string): boolean => {
    return key in oldData && !(key in newData);
  };

  const isFieldAdded = (key: string): boolean => {
    return !(key in oldData) && key in newData;
  };

  const isFieldChanged = (key: string): boolean => {
    return key in oldData && key in newData;
  };

  return (
    <div className="log-diff width-max" onClick={(e) => e.stopPropagation()}>
      <section className="log-diff-meta flex flex-wrap align-items-center g-2 mb-1">
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Urządzenie:</span>
          {log.clinicDeviceName ? (
            <DeviceTag name={log.clinicDeviceName} className="device-tag-md" />
          ) : (
            <span className="dl-dim">-</span>
          )}
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Typ:</span>
          <span className="dl-dim">{log.deviceType}</span>
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Przeglądarka:</span>
          <span className="dl-dim">{log.browserName}</span>
        </span>
        <span className="flex align-items-center g-05">
          <span className="log-meta-label">Session ID:</span>
          <span className="dl-dim">{log.sessionId}</span>
        </span>
      </section>

      <div className="log-diff-grid width-max">
        <div
          className={`log-diff-pane old ${log.action === AuditAction.CREATE || log.action === AuditAction.SECURITY ? "disabled" : ""} ${isCompact && collapsed.old ? "collapsed" : ""}`}
        >
          {renderPaneTitle("old", "Przed zmianą")}
          <div className="log-diff-rows">
            {allKeys.map((key) => {
              const isRemoved = isFieldRemoved(key);
              const isChanged =
                log.action === AuditAction.UPDATE && isFieldChanged(key);
              return (
                <div key={key} className="log-diff-row">
                  <span className="log-diff-field">{key}:</span>
                  <span
                    className={`log-diff-val ${isRemoved ? "removed" : ""} ${isChanged ? "changed-old" : ""}`}
                  >
                    {key in oldData
                      ? formatValue(oldData[key], key, "old")
                      : ""}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
        <div
          className={`log-diff-pane new ${log.action === AuditAction.DELETE ? "disabled" : ""} ${isCompact && collapsed.new ? "collapsed" : ""}`}
        >
          {renderPaneTitle("new", "Po zmianie")}
          <div className="log-diff-rows">
            {allKeys.map((key) => {
              const isAdded = isFieldAdded(key);
              const isChanged =
                log.action === AuditAction.UPDATE && isFieldChanged(key);
              return (
                <div key={key} className="log-diff-row">
                  <span className="log-diff-field">{key}:</span>
                  <span
                    className={`log-diff-val ${isAdded ? "added" : ""} ${isChanged ? "changed-new" : ""}`}
                  >
                    {key in newData
                      ? formatValue(newData[key], key, "new")
                      : ""}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

export default LogContent;
