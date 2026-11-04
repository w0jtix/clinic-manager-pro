package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClientQuestionnaireDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireUpdateDTO;
import com.clinic.clinicmanager.service.ClientQuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class ClientQuestionnaireController {

    private final ClientQuestionnaireService clientQuestionnaireService;

    @GetMapping("/{clientId}/questionnaire")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientQuestionnaireDTO> getQuestionnaire(@PathVariable(value = "clientId") Long clientId) {
        return new ResponseEntity<>(clientQuestionnaireService.getQuestionnaire(clientId), HttpStatus.OK);
    }

    @PutMapping("/{clientId}/questionnaire")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientQuestionnaireDTO> updateQuestionnaire(
            @PathVariable(value = "clientId") Long clientId,
            @RequestBody QuestionnaireUpdateDTO questionnaire) {
        return new ResponseEntity<>(clientQuestionnaireService.updateQuestionnaire(clientId, questionnaire), HttpStatus.OK);
    }
}
