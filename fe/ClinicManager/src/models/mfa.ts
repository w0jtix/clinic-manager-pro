export interface MfaChallenge {
  mfaRequired: true;
  mfaToken: string;
}

export interface TrustedDevice {
  id: number;
  label: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  lastIp: string | null;
  current: boolean;
}

export interface MfaStatus {
  enabled: boolean;
  enabledAt: string | null;
  remainingBackupCodes: number;
  trustedDevices: TrustedDevice[];
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
  qrDataUri: string;
}

export interface BackupCodesResponse {
  backupCodes: string[];
}

export const MFA_CODE_LENGTH = 6;

export const isMfaChallenge = (value: unknown): value is MfaChallenge =>
  typeof value === "object" && value !== null && (value as MfaChallenge).mfaRequired === true;
