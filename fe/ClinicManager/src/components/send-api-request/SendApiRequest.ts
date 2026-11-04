import axios, { AxiosRequestConfig, Method } from "axios";
import { environment } from "../../environments/environment";
import AuthService from "../../services/AuthService";
import { ACCESS_DENIED_FLAG_KEY, DEVICE_NOT_ALLOWED } from "../../models/access-control";



type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

let forcedLogoutStarted = false;

const forceLogout = (options?: { rememberLocation?: boolean }) => {
  if (forcedLogoutStarted) return;
  forcedLogoutStarted = true;
  AuthService.logout(options);
};

interface ApiRequestOptions {
  method: HttpMethod | Lowercase<HttpMethod>;
  body?: any;
  skipAuthHeader?: boolean;
  errorMessage?: string;
  responseType?: "json" | "blob";
}

export const sendApiRequest = async <T>(
  endpoint: string,
  { method, body, skipAuthHeader, errorMessage, responseType = "json" }: ApiRequestOptions
): Promise<T> => {
  try {
    // FormData must not carry an explicit Content-Type - axios sets it together
    // with the multipart boundary.
    const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

    const config: AxiosRequestConfig = {
      url: `${window.location.protocol}//${window.location.hostname}${environment.apiUrl}${endpoint}`,
      method: method.toUpperCase() as Method,
      data: body,
      headers: isFormData ? {} : { "Content-Type": "application/json" },
      responseType: responseType,
      withCredentials: true,
    };

    if (!skipAuthHeader) {
      const authHeader = AuthService.getAuthHeader();
      config.headers![authHeader.key] = authHeader.value;
    }

    const response = await axios.request<T>(config);

    if (response.status === 204) {
      return [] as unknown as T;
    }
    
    return response.data;
  } catch (error: any) {
    console.error("API error:", error);

    if (error.response?.status === 401 && AuthService.getCurrentUser()) {
      forceLogout({ rememberLocation: true });
      return undefined as unknown as T;
    }

    if (error.response?.status === 403 && error.response?.data?.code === DEVICE_NOT_ALLOWED && AuthService.getCurrentUser()) {
      try {
        sessionStorage.setItem(ACCESS_DENIED_FLAG_KEY, "1");
      } catch {}
      forceLogout();
      return undefined as unknown as T;
    }

    if (error.response?.status === 403 && endpoint !== "auth/me" && AuthService.getCurrentUser()) {
      AuthService.syncCurrentUser();
    }

    // If there's a backend response, preserve the original axios error
    if (error.response) {
      throw error;
    }

    // Otherwise create a new Error with custom message
    throw new Error(errorMessage || "Unexpected API error occurred.");
  }
};