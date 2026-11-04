import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { AccessControl, GatePauseDuration, StepUp, WorkingHoursUpdate } from "../models/access-control";

class AccessControlService {

    static async get(): Promise<AccessControl> {
        return await sendApiRequest<AccessControl>("access-control", {
            method: "get",
            errorMessage: "Error fetching access control settings.",
        });
    }

    static async setGate(enabled: boolean, stepUp: StepUp = {}): Promise<AccessControl> {
        return await sendApiRequest<AccessControl>("access-control/gate", {
            method: "put",
            body: { enabled, ...stepUp },
            errorMessage: "Error updating access gate.",
        });
    }

    static async pause(duration: GatePauseDuration, stepUp: StepUp): Promise<AccessControl> {
        return await sendApiRequest<AccessControl>("access-control/pause", {
            method: "post",
            body: { duration, ...stepUp },
            errorMessage: "Error pausing access gate.",
        });
    }

    static async resume(): Promise<AccessControl> {
        return await sendApiRequest<AccessControl>("access-control/pause", {
            method: "delete",
            errorMessage: "Error resuming access gate.",
        });
    }

    static async updateHours(request: WorkingHoursUpdate): Promise<AccessControl> {
        return await sendApiRequest<AccessControl>("access-control/hours", {
            method: "put",
            body: request,
            errorMessage: "Error updating working hours.",
        });
    }
}

export default AccessControlService;
