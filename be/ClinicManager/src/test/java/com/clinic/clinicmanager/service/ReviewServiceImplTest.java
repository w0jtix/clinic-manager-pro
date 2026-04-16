package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ReviewDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Review;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.repo.ReviewRepo;
import com.clinic.clinicmanager.repo.VisitDiscountRepo;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.service.impl.ReviewServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock ReviewRepo reviewRepo;
    @Mock VisitDiscountRepo visitDiscountRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks
    ReviewServiceImpl reviewService;

    private Client client;
    private Review review;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski").isDeleted(false).build();

        review = Review.builder()
                .id(10L)
                .client(client)
                .isUsed(false)
                .source(ReviewSource.GOOGLE)
                .issueDate(LocalDate.now())
                .createdByUserId(5L)
                .build();
    }

    @Test
    void getReviewById_shouldReturnDTO_whenReviewExists() {
        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));

        ReviewDTO result = reviewService.getReviewById(10L);

        assertEquals(10L, result.getId());
        assertEquals(ReviewSource.GOOGLE, result.getSource());
    }

    @Test
    void getReviewById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(reviewRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getReviewById(99L));
    }

    @Test
    void getReviews_shouldHandleNullFilter() {
        when(reviewRepo.findAllWithFilters(isNull(), isNull(), isNull())).thenReturn(List.of(review));

        List<ReviewDTO> result = reviewService.getReviews(null);

        assertEquals(1, result.size());
    }

    @Test
    void createReview_shouldMarkOtherGoogleReviewsAsUsed_whenSourceIsGoogle() {
        Review otherActiveReview = Review.builder()
                .id(2L).client(client).isUsed(false)
                .source(ReviewSource.GOOGLE).issueDate(LocalDate.now().minusDays(10)).build();

        ReviewDTO inputDTO = new ReviewDTO();
        inputDTO.setSource(ReviewSource.GOOGLE);
        ClientDTO clientDTO = new ClientDTO(client);
        inputDTO.setClient(clientDTO);
        inputDTO.setIsUsed(false);
        inputDTO.setIssueDate(LocalDate.now());

        when(reviewRepo.findAllByClientId(1L)).thenReturn(List.of(otherActiveReview));
        when(reviewRepo.save(otherActiveReview)).thenReturn(otherActiveReview);
        when(reviewRepo.save(argThat(r -> r != otherActiveReview))).thenReturn(review);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(5L);

            reviewService.createReview(inputDTO);

            assertTrue(otherActiveReview.getIsUsed());
            verify(reviewRepo).save(otherActiveReview);
        }
    }

    @Test
    void createReview_shouldNotMarkOtherReviews_whenSourceIsNotGoogle() {
        ReviewDTO inputDTO = new ReviewDTO();
        inputDTO.setSource(ReviewSource.BOOKSY);
        inputDTO.setClient(new ClientDTO(client));
        inputDTO.setIsUsed(false);
        inputDTO.setIssueDate(LocalDate.now());

        when(reviewRepo.save(any(Review.class))).thenReturn(review);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(5L);

            reviewService.createReview(inputDTO);

            verify(reviewRepo, never()).findAllByClientId(any());
        }
    }

    @Test
    void createReview_shouldSetCreatedByUserIdFromSession() {
        ReviewDTO inputDTO = new ReviewDTO();
        inputDTO.setSource(ReviewSource.BOOKSY);
        inputDTO.setClient(new ClientDTO(client));
        inputDTO.setIsUsed(false);
        inputDTO.setIssueDate(LocalDate.now());

        when(reviewRepo.save(any(Review.class))).thenReturn(review);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(7L);

            reviewService.createReview(inputDTO);

            verify(reviewRepo).save(argThat(r -> Long.valueOf(7L).equals(r.getCreatedByUserId())));
        }
    }

    @Test
    void updateReview_shouldPreserveCreatedByUserId() {
        ReviewDTO inputDTO = new ReviewDTO();
        inputDTO.setSource(ReviewSource.GOOGLE);
        inputDTO.setClient(new ClientDTO(client));
        inputDTO.setIsUsed(false);
        inputDTO.setIssueDate(LocalDate.now());

        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));
        when(reviewRepo.save(any(Review.class))).thenReturn(review);

        reviewService.updateReview(10L, inputDTO);

        verify(reviewRepo).save(argThat(r -> Long.valueOf(5L).equals(r.getCreatedByUserId())));
    }

    @Test
    void updateReview_shouldThrowResourceNotFoundException_whenNotFound() {
        when(reviewRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.updateReview(99L, new ReviewDTO()));
    }

    @Test
    void updateReview_shouldRethrowAccessDeniedException_whenNotOwner() {
        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));
        doThrow(new AccessDeniedException("denied")).when(ownershipService).checkOwnershipOrAdmin(5L);

        assertThrows(Exception.class, () -> reviewService.updateReview(10L, new ReviewDTO()));
    }

    @Test
    void deleteReviewById_shouldDeleteAndLogAudit_whenOwnerAndNoVisitDiscount() {
        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));
        when(visitDiscountRepo.existsByReviewId(10L)).thenReturn(false);

        reviewService.deleteReviewById(10L);

        verify(reviewRepo).deleteById(10L);
        verify(auditLogService).logDelete(eq("Review"), eq(10L), anyString(), any());
    }

    @Test
    void deleteReviewById_shouldThrowConflictException_whenUsedInVisitDiscount() {
        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));
        when(visitDiscountRepo.existsByReviewId(10L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> reviewService.deleteReviewById(10L));
        verify(reviewRepo, never()).deleteById(any());
    }

    @Test
    void deleteReviewById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(reviewRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.deleteReviewById(99L));
    }

    @Test
    void deleteReviewById_shouldRethrowAccessDeniedException_whenNotOwner() {
        when(reviewRepo.findOneById(10L)).thenReturn(Optional.of(review));
        doThrow(new AccessDeniedException("denied")).when(ownershipService).checkOwnershipOrAdmin(5L);

        assertThrows(Exception.class, () -> reviewService.deleteReviewById(10L));
        verify(reviewRepo, never()).deleteById(any());
    }
}
