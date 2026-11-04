export type GatePauseDuration = "HOUR_1" | "HOUR_4" | "END_OF_DAY";

export interface WorkingDay {
  dayOfWeek: number;
  open: boolean;
  startTime: string | null;
  endTime: string | null;
}

export interface AccessControl {
  gateEnabled: boolean;
  gateActive: boolean;
  pausedUntil: string | null;
  pausedBy: string | null;
  hoursEnabled: boolean;
  hours: WorkingDay[];
  activeDeviceCount: number;
}

export interface WorkingHoursUpdate {
  hoursEnabled: boolean;
  hours: WorkingDay[];
}

export interface StepUp {
  code?: string;
  password?: string;
}

export const ACCESS_DENIED = "ACCESS_DENIED";
export const STEP_UP_INVALID = "STEP_UP_INVALID";
export const STEP_UP_REQUIRED = "STEP_UP_REQUIRED";
export const DEVICE_NOT_ALLOWED = "DEVICE_NOT_ALLOWED";
export const ACCESS_DENIED_MESSAGE = "Brak dostępu";
export const ACCESS_DENIED_FLAG_KEY = "pc_access_denied";

export const WEEKDAY_LABELS: Record<number, string> = {
  1: "Poniedziałek",
  2: "Wtorek",
  3: "Środa",
  4: "Czwartek",
  5: "Piątek",
  6: "Sobota",
  7: "Niedziela",
};
