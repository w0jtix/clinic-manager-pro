export type ClinicDeviceStatus = "ACTIVE" | "SUSPECTED_COPY" | "REVOKED";

export interface ClinicDevice {
  id: number;
  name: string;
  label: string | null;
  lastIp: string | null;
  createdAt: string;
  registeredBy: string | null;
  lastSeenAt: string | null;
  lastSeenUsername: string | null;
  status: ClinicDeviceStatus;
  replacedByName: string | null;
  revokedAt: string | null;
}

export interface RegistrationCode {
  id: number;
  code: string;
  name: string | null;
  replaceDeviceId: number | null;
  replaceDeviceName: string | null;
  createdBy: string;
  createdAt: string;
  expiresAt: string;
}

export interface NewRegistrationCode {
  name?: string;
  replaceDeviceId?: number;
}

export interface DeviceRegistrationResponse {
  message: string;
  deviceName: string;
}

export const DEVICE_CODE_LENGTH = 6;
