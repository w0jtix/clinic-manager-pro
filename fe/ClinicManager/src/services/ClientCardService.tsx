import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { environment } from "../environments/environment";
import AuthService from "./AuthService";
import {
  CardHistoryItem,
  ClientCard,
  ClientCardData,
  ClientCardFilterDTO,
  ClientCardListItem,
  NewClientCard,
} from "../models/client-card";
import { Questionnaire, QuestionnaireUpdate } from "../models/questionnaire";
import { ClientDrawing, DrawingMarker, StoredDrawItem } from "../models/drawing";
import {
  TherapyEntryInput,
  TherapyEntryRevision,
  TherapyEntryUpdate,
  TimelineItem,
  TimelinePage,
} from "../models/therapy";

class ClientCardService {

  static async searchCards(
    filter: ClientCardFilterDTO,
    page: number = 0,
    size: number = 25
  ): Promise<{ content: ClientCardListItem[], totalPages: number, totalElements: number, last: boolean }> {
    return await sendApiRequest<{ content: ClientCardListItem[], totalPages: number, totalElements: number, last: boolean }>(
      `cards/search?page=${page}&size=${size}`,
      {
        method: "post",
        body: filter,
        errorMessage: "Error fetching Client Cards.",
      }
    );
  }

  static async getCard(clientId: number): Promise<ClientCard> {
    return await sendApiRequest<ClientCard>(`cards/${clientId}`, {
      method: "get",
      errorMessage: "Error fetching Client Card.",
    });
  }

  static async createCard(card: NewClientCard): Promise<ClientCard> {
    return await sendApiRequest<ClientCard>(`cards`, {
      method: "post",
      body: card,
      errorMessage: "Error creating Client Card.",
    });
  }

  static async updateClientData(clientId: number, data: ClientCardData): Promise<ClientCard> {
    return await sendApiRequest<ClientCard>(`cards/${clientId}/client-data`, {
      method: "put",
      body: data,
      errorMessage: "Error updating Client Card data.",
    });
  }

  static updateClientDataOnPageHide(clientId: number, data: ClientCardData): void {
    ClientCardService.putOnPageHide(`cards/${clientId}/client-data`, data);
  }

  static async getHistory(
    clientId: number,
    page: number = 0,
    size: number = 30
  ): Promise<{ content: CardHistoryItem[]; totalPages: number; totalElements: number; last: boolean }> {
    return await sendApiRequest<{ content: CardHistoryItem[]; totalPages: number; totalElements: number; last: boolean }>(
      `cards/${clientId}/history?page=${page}&size=${size}`,
      {
        method: "get",
        errorMessage: "Error fetching card history.",
      }
    );
  }

  static async getQuestionnaire(clientId: number): Promise<Questionnaire> {
    return await sendApiRequest<Questionnaire>(`cards/${clientId}/questionnaire`, {
      method: "get",
      errorMessage: "Error fetching Questionnaire.",
    });
  }

  static async updateQuestionnaire(clientId: number, data: QuestionnaireUpdate): Promise<Questionnaire> {
    return await sendApiRequest<Questionnaire>(`cards/${clientId}/questionnaire`, {
      method: "put",
      body: data,
      errorMessage: "Error updating Questionnaire.",
    });
  }

  static updateQuestionnaireOnPageHide(clientId: number, data: QuestionnaireUpdate): void {
    ClientCardService.putOnPageHide(`cards/${clientId}/questionnaire`, data);
  }

  static async getDrawing(clientId: number): Promise<ClientDrawing> {
    return await sendApiRequest<ClientDrawing>(`cards/${clientId}/drawing`, {
      method: "get",
      errorMessage: "Error fetching Drawing.",
    });
  }

  static async getDrawingLayerItems(clientId: number, sheetId: number, layerId: number): Promise<StoredDrawItem[]> {
    return await sendApiRequest<StoredDrawItem[]>(`cards/${clientId}/drawing/sheets/${sheetId}/layers/${layerId}/items`, {
      method: "get",
      errorMessage: "Error fetching Drawing layer items.",
    });
  }

  static async logDrawingSheetView(clientId: number, sheetId: number): Promise<void> {
    return await sendApiRequest<void>(`cards/${clientId}/drawing/sheets/${sheetId}/view`, {
      method: "post",
      errorMessage: "Error logging Drawing sheet view.",
    });
  }

  static async getDrawingMarkers(): Promise<DrawingMarker[]> {
    return await sendApiRequest<DrawingMarker[]>(`cards/drawing-markers`, {
      method: "get",
      errorMessage: "Error fetching Drawing markers.",
    });
  }

  static async getDrawingTemplateImage(imageId: number): Promise<Blob> {
    return await sendApiRequest<Blob>(`cards/drawing-template-images/${imageId}/image`, {
      method: "get",
      responseType: "blob",
      errorMessage: "Error fetching Drawing template image.",
    });
  }

  static async createDrawingSheet(
    clientId: number,
    templateId: number,
    items: StoredDrawItem[] | null,
    closeSheetId: number | null
  ): Promise<ClientDrawing> {
    return await sendApiRequest<ClientDrawing>(`cards/${clientId}/drawing/sheets`, {
      method: "post",
      body: { templateId, closeSheetId, items },
      errorMessage: "Error creating Drawing sheet.",
    });
  }

  static async saveDrawingLayerItems(
    clientId: number,
    sheetId: number,
    authorUserId: number,
    items: StoredDrawItem[],
    expectedVersion: number
  ): Promise<ClientDrawing> {
    return await sendApiRequest<ClientDrawing>(
      `cards/${clientId}/drawing/sheets/${sheetId}/layers/${authorUserId}`,
      {
        method: "put",
        body: { items, expectedVersion },
        errorMessage: "Error saving Drawing layer.",
      }
    );
  }

  static async getTimeline(clientId: number, beforeDate: string | null, days: number = 20): Promise<TimelinePage> {
    const params = new URLSearchParams({ days: String(days) });
    if (beforeDate) params.set("beforeDate", beforeDate);
    return await sendApiRequest<TimelinePage>(`cards/${clientId}/timeline?${params.toString()}`, {
      method: "get",
      errorMessage: "Error fetching Therapy timeline.",
    });
  }

  static async createTherapyEntry(clientId: number, entry: TherapyEntryInput): Promise<TimelineItem> {
    return await sendApiRequest<TimelineItem>(`cards/${clientId}/therapy-entries`, {
      method: "post",
      body: entry,
      errorMessage: "Error creating Therapy entry.",
    });
  }

  static async updateTherapyEntry(clientId: number, entryId: number, entry: TherapyEntryUpdate): Promise<TimelineItem> {
    return await sendApiRequest<TimelineItem>(`cards/${clientId}/therapy-entries/${entryId}`, {
      method: "put",
      body: entry,
      errorMessage: "Error updating Therapy entry.",
    });
  }

  static async invalidateTherapyEntry(clientId: number, entryId: number): Promise<TimelineItem> {
    return await sendApiRequest<TimelineItem>(`cards/${clientId}/therapy-entries/${entryId}/invalidate`, {
      method: "post",
      errorMessage: "Error invalidating Therapy entry.",
    });
  }

  static async getTherapyEntryRevisions(clientId: number, entryId: number): Promise<TherapyEntryRevision[]> {
    return await sendApiRequest<TherapyEntryRevision[]>(`cards/${clientId}/therapy-entries/${entryId}/revisions`, {
      method: "get",
      errorMessage: "Error fetching Therapy entry history.",
    });
  }

  private static putOnPageHide(endpoint: string, body: unknown): void {
    const authHeader = AuthService.getAuthHeader();
    fetch(`${window.location.protocol}//${window.location.hostname}${environment.apiUrl}${endpoint}`, {
      method: "PUT",
      keepalive: true,
      headers: {
        "Content-Type": "application/json",
        [authHeader.key]: authHeader.value,
      },
      body: JSON.stringify(body),
    }).catch(() => undefined);
  }
}

export default ClientCardService;
