package com.clinic.clinicmanager.model.revision;

public record QuestionnaireFieldChange(Long fieldId, String label, String type,
                                       AnswerSnapshot before, AnswerSnapshot after) {

    public static final String TYPE_YES_NO = "YES_NO";
    public static final String TYPE_TEXT = "TEXT";

    public record AnswerSnapshot(Boolean checked, String text) {
    }
}
