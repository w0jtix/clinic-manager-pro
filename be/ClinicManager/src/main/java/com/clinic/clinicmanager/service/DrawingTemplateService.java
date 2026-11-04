package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DrawingTemplateService {

    List<DrawingTemplateSettingsDTO> getTemplates();

    DrawingTemplateSettingsDTO createTemplate(String name, MultipartFile file);

    DrawingTemplateSettingsDTO updateTemplate(Long id, String name, MultipartFile file, boolean removeImage);

    DrawingTemplateDeleteResult deleteTemplate(Long id);

    DrawingTemplateSettingsDTO restoreTemplate(Long id);

    List<DrawingTemplateSettingsDTO> reorderTemplates(List<Long> ids);

    byte[] getTemplateImage(Long imageId);
}
