import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { User, UserAvatar } from "../models/login";
import { MessageResponse } from "../models/register";
import { UserPreferences } from "../models/preferences";

class UserService {

    static async getCurrentUser(): Promise<User> {
            return await sendApiRequest<User>(`user/me`, {
                method: "get",
                errorMessage: "Error fetching User."
            })
        }

    static async getUserById(userId: number | string): Promise<User> {
            return await sendApiRequest<User>(`user/${userId}`, {
                method: "get",
                errorMessage: "Error fetching User."
            })
        }

    static async getTakenAvatars(): Promise<string[]> {
        return await sendApiRequest<string[]>('user/avatars/taken', {
            method: "get",
            errorMessage: "Error fetching taken avatars."
        })
    }

    static async getAllUsers(): Promise<User[]> {
        return await sendApiRequest<User[]> ('user/all', {
            method: "get",
            errorMessage: "Error fetching users."
        })
    }


    static async getUserByEmployeeId(employeeId: string | number): Promise<UserAvatar> {
        return await sendApiRequest<UserAvatar> (`user/employee/${employeeId}`, {
            method: "get",
            errorMessage: "Error fetching users."
        })
    }

    static async setCompanyPin(userId: number, newPin: string): Promise<MessageResponse> {
        return await sendApiRequest<MessageResponse>(`user/${userId}/company-pin`, {
            method: "put",
            body: { newPin },
            errorMessage: "Error setting company PIN.",
        });
    }

    static async changeOwnCompanyPin(currentPin: string, newPin: string): Promise<MessageResponse> {
        return await sendApiRequest<MessageResponse>(`user/me/company-pin`, {
            method: "put",
            body: { currentPin, newPin },
            errorMessage: "Error changing company PIN.",
        });
    }

    static async getMyPreferences(): Promise<UserPreferences> {
        return await sendApiRequest<UserPreferences>(`user/me/preferences`, {
            method: "get",
            errorMessage: "Error fetching User preferences.",
        });
    }

    static async updateMyPreferences(preferences: UserPreferences): Promise<UserPreferences> {
        return await sendApiRequest<UserPreferences>(`user/me/preferences`, {
            method: "put",
            body: preferences,
            errorMessage: "Error updating User preferences.",
        });
    }

    static async updateUser(id: number, user: User): Promise<User | undefined> {
        return await sendApiRequest<User>(`user/${id}`, {
            method: "put",
            body: user,
            errorMessage: "Error updating User.",
        });
    }

}

export default UserService;