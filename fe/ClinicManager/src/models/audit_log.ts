export enum AuditAction {
    CREATE = "CREATE",
    UPDATE = "UPDATE",
    DELETE = "DELETE",
    SEND = "SEND",
    SECURITY = "SECURITY"
}

export interface AuditLog {
    id: number;
    entityType: string;
    entityId: number | null;
    entityKeyTrait: string;
    action : AuditAction;
    performedBy: string;
    timestamp: string;
    oldValue: string | null;
    newValue: string | null;
    changedFields: string;
    ipAddress: string;
    sessionId: string;
    deviceType: string;
    browserName: string;
    clinicDeviceName: string | null;
}

export interface AuditLogFilterDTO {
    entityType: string | null;
    keyword: string | null;
    action : AuditAction | null;
    performedBy: string | null;
    dateFrom: string | null;
    dateTo: string | null;
}