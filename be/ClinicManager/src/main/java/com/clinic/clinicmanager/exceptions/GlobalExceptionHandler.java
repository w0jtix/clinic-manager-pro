package com.clinic.clinicmanager.exceptions;

import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.response.CodedErrorResponse;
import com.clinic.clinicmanager.DTO.response.DrawingConflictResponse;
import com.clinic.clinicmanager.DTO.response.DuplicateClientResponse;
import com.clinic.clinicmanager.DTO.response.QuestionnaireConflictResponse;
import com.clinic.clinicmanager.DTO.response.TherapyEntryConflictResponse;
import com.clinic.clinicmanager.DTO.response.TherapyEntryDayTakenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CreationException.class)
    public ResponseEntity<String> handleCreationException(CreationException ex) {
        log.error("Creation failed: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DeletionException.class)
    public ResponseEntity<String> handleDeletionException(DeletionException ex) {
        log.error("Deletion failed: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UpdateException.class)
    public ResponseEntity<String> handleResourceUpdateException(UpdateException ex) {
        log.error("Resource update failed: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientPaymentException.class)
    public ResponseEntity<String> handleInsufficientPaymentException(InsufficientPaymentException ex) {
        log.error("Insufficient payment: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<String> handleEmailSendException(EmailSendException ex) {
        log.error("Email sending failed: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidationException(ValidationException ex) {
        log.error("Validation failed: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflictException(ConflictException ex) {
        log.error("Conflict: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<String> handleExternalServiceUnavailableException(ExternalServiceUnavailableException ex) {
        log.warn("External service unavailable: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<String> handleTooManyAttemptsException(TooManyAttemptsException ex) {
        log.warn("Too many attempts: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(StepUpException.class)
    public ResponseEntity<CodedErrorResponse> handleStepUpException(StepUpException ex) {
        log.warn("Step-up verification failed: {}", ex.getCode());
        return new ResponseEntity<>(new CodedErrorResponse(ex.getCode(), ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessGateDeniedException.class)
    public ResponseEntity<CodedErrorResponse> handleAccessGateDeniedException(AccessGateDeniedException ex) {
        log.warn("Access gate denied: {}", ex.getCode());
        return new ResponseEntity<>(new CodedErrorResponse(ex.getCode(), ex.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MfaSessionExpiredException.class)
    public ResponseEntity<CodedErrorResponse> handleMfaSessionExpiredException(MfaSessionExpiredException ex) {
        log.warn("MFA session expired");
        return new ResponseEntity<>(new CodedErrorResponse(MfaSessionExpiredException.CODE, ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateClientException.class)
    public ResponseEntity<DuplicateClientResponse> handleDuplicateClientException(DuplicateClientException ex) {
        log.error("Duplicate client: {}", ex.getMessage());
        return new ResponseEntity<>(new DuplicateClientResponse(ex), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(QuestionnaireConflictException.class)
    public ResponseEntity<QuestionnaireConflictResponse> handleQuestionnaireConflictException(QuestionnaireConflictException ex) {
        log.error("Questionnaire conflict: {}", ex.getMessage());
        return new ResponseEntity<>(new QuestionnaireConflictResponse(ex), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DrawingConflictException.class)
    public ResponseEntity<DrawingConflictResponse> handleDrawingConflictException(DrawingConflictException ex) {
        log.error("Drawing conflict: {}", ex.getMessage());
        return new ResponseEntity<>(new DrawingConflictResponse(ex), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TherapyEntryDayTakenException.class)
    public ResponseEntity<TherapyEntryDayTakenResponse> handleTherapyEntryDayTakenException(TherapyEntryDayTakenException ex) {
        log.error("Therapy entry day taken: {}", ex.getMessage());
        return new ResponseEntity<>(new TherapyEntryDayTakenResponse(ex), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(TherapyEntryConflictException.class)
    public ResponseEntity<TherapyEntryConflictResponse> handleTherapyEntryConflictException(TherapyEntryConflictException ex) {
        log.error("Therapy entry conflict: {}", ex.getMessage());
        return new ResponseEntity<>(new TherapyEntryConflictResponse(ex), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.error("Upload too large: {}", ex.getMessage());
        return new ResponseEntity<>("Plik jest za duży (maksymalnie 6 MB).", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StockChangeBlockedException.class)
    public ResponseEntity<StockChangePreviewDTO> handleStockChangeBlockedException(StockChangeBlockedException ex) {
        log.error("Stock change blocked: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getPreview(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<String> handleAuditException(AuditException ex) {
        log.error("Audit failed: {}", ex.getMessage());
        return new ResponseEntity<>("Operation failed due to audit logging error.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return new ResponseEntity<>("Nieprawidłowy format danych.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.error("Invalid request parameter {}: {}", ex.getName(), ex.getValue());
        return new ResponseEntity<>("Invalid value for parameter: " + ex.getName(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("No endpoint: {} /{}", ex.getHttpMethod(), ex.getResourcePath());
        return new ResponseEntity<>("Not found.", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return new ResponseEntity<>("Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
