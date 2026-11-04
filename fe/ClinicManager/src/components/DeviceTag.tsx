import { Tablet } from "lucide-react";

export interface DeviceTagProps {
  name: string | null | undefined;
  className?: string;
}

export function UnknownDeviceTag({ className = "" }: { className?: string }) {
  return (
    <span className={`device-tag unknown flex align-items-center g-025 ${className}`} title="Nie rozpoznano urządzenia">
      <Tablet className="device-tag-icon" />
      <span className="device-tag-name">???</span>
    </span>
  );
}

export function DeviceTag({ name, className = "" }: DeviceTagProps) {
  if (!name) return null;
  return (
    <span className={`device-tag flex align-items-center g-025 mw-0 ${className}`} title={`Urządzenie gabinetu: ${name}`}>
      <Tablet className="device-tag-icon" />
      <span className="device-tag-name">{name}</span>
    </span>
  );
}

export default DeviceTag;
