import { sendApiRequest } from "../components/send-api-request/SendApiRequest";
import { QuestionnaireSettings, QuestionnaireSettingsInput } from "../models/questionnaire";
import {
  DrawingMarker,
  DrawingMarkerInput,
  DrawingTemplateDeleteResult,
  DrawingTemplateSettings,
} from "../models/drawing";

class CardSettingsService {

  static async getQuestionnaireSettings(): Promise<QuestionnaireSettings> {
    return await sendApiRequest<QuestionnaireSettings>(`card-settings/questionnaire`, {
      method: "get",
      errorMessage: "Error fetching Questionnaire settings.",
    });
  }

  static async updateQuestionnaireSettings(
    settings: QuestionnaireSettingsInput
  ): Promise<QuestionnaireSettings> {
    return await sendApiRequest<QuestionnaireSettings>(`card-settings/questionnaire`, {
      method: "put",
      body: settings,
      errorMessage: "Error updating Questionnaire settings.",
    });
  }

  static async getDrawingTemplates(): Promise<DrawingTemplateSettings[]> {
    return await sendApiRequest<DrawingTemplateSettings[]>(`card-settings/drawing-templates`, {
      method: "get",
      errorMessage: "Error fetching Drawing templates.",
    });
  }

  static async createDrawingTemplate(name: string, file: File | null): Promise<DrawingTemplateSettings> {
    const formData = new FormData();
    formData.append("name", name);
    if (file) formData.append("file", file);
    return await sendApiRequest<DrawingTemplateSettings>(`card-settings/drawing-templates`, {
      method: "post",
      body: formData,
      errorMessage: "Error creating Drawing template.",
    });
  }

  static async updateDrawingTemplate(
    id: number,
    name: string,
    file: File | null,
    removeImage: boolean
  ): Promise<DrawingTemplateSettings> {
    const formData = new FormData();
    formData.append("name", name);
    if (file) formData.append("file", file);
    if (removeImage) formData.append("removeImage", "true");
    return await sendApiRequest<DrawingTemplateSettings>(`card-settings/drawing-templates/${id}`, {
      method: "put",
      body: formData,
      errorMessage: "Error updating Drawing template.",
    });
  }

  static async deleteDrawingTemplate(id: number): Promise<DrawingTemplateDeleteResult> {
    const response = await sendApiRequest<{ result: DrawingTemplateDeleteResult }>(
      `card-settings/drawing-templates/${id}`,
      {
        method: "delete",
        errorMessage: "Error deleting Drawing template.",
      }
    );
    return response.result;
  }

  static async restoreDrawingTemplate(id: number): Promise<DrawingTemplateSettings> {
    return await sendApiRequest<DrawingTemplateSettings>(`card-settings/drawing-templates/${id}/restore`, {
      method: "put",
      errorMessage: "Error restoring Drawing template.",
    });
  }

  static async reorderDrawingTemplates(ids: number[]): Promise<DrawingTemplateSettings[]> {
    return await sendApiRequest<DrawingTemplateSettings[]>(`card-settings/drawing-templates/order`, {
      method: "put",
      body: { ids },
      errorMessage: "Error reordering Drawing templates.",
    });
  }

  static async getDrawingMarkers(): Promise<DrawingMarker[]> {
    return await sendApiRequest<DrawingMarker[]>(`card-settings/drawing-markers`, {
      method: "get",
      errorMessage: "Error fetching Drawing markers.",
    });
  }

  static async createDrawingMarker(marker: DrawingMarkerInput): Promise<DrawingMarker> {
    return await sendApiRequest<DrawingMarker>(`card-settings/drawing-markers`, {
      method: "post",
      body: marker,
      errorMessage: "Error creating Drawing marker.",
    });
  }

  static async updateDrawingMarker(id: number, marker: DrawingMarkerInput): Promise<DrawingMarker> {
    return await sendApiRequest<DrawingMarker>(`card-settings/drawing-markers/${id}`, {
      method: "put",
      body: marker,
      errorMessage: "Error updating Drawing marker.",
    });
  }

  static async deleteDrawingMarker(id: number): Promise<void> {
    return await sendApiRequest<void>(`card-settings/drawing-markers/${id}`, {
      method: "delete",
      errorMessage: "Error deleting Drawing marker.",
    });
  }

  static async reorderDrawingMarkers(ids: number[]): Promise<DrawingMarker[]> {
    return await sendApiRequest<DrawingMarker[]>(`card-settings/drawing-markers/order`, {
      method: "put",
      body: { ids },
      errorMessage: "Error reordering Drawing markers.",
    });
  }
}

export default CardSettingsService;
