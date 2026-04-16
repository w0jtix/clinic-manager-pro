package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ReviewDTO;
import com.clinic.clinicmanager.DTO.request.ReviewFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Review;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.repo.ReviewRepo;
import com.clinic.clinicmanager.repo.VisitDiscountRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ReviewService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepo reviewRepo;
    private final VisitDiscountRepo visitDiscountRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;

    @Override
    public ReviewDTO getReviewById(Long id) {
        return new ReviewDTO(reviewRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with given id: " + id)));
    }

    @Override
    public List<ReviewDTO> getReviews(ReviewFilterDTO filter) {
        if(isNull(filter)) {
            filter = new ReviewFilterDTO();
        }

        return reviewRepo.findAllWithFilters(
                        filter.getKeyword(),
                        filter.getSource(),
                        filter.getIsUsed()
                )
                .stream()
                .map(ReviewDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ReviewDTO createReview(ReviewDTO review) {
        try{
            if(review.getSource() == ReviewSource.GOOGLE) {
                markOtherReviewsAsUsed(review.getClient().getId());
            }

            Review reviewEntity = review.toEntity();
            reviewEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            ReviewDTO savedDTO = new ReviewDTO(reviewRepo.save(reviewEntity));
            auditLogService.logCreate("Review", savedDTO.getId(),"Opinia Klienta: "+ savedDTO.getClient().getFirstName() + savedDTO.getClient().getLastName(), savedDTO);
            return savedDTO;
        } catch (Exception e) {
            throw new CreationException("Failed to create Review. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ReviewDTO updateReview(Long id, ReviewDTO review) {
        try{
            Review oldReview = reviewRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Review not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(oldReview.getCreatedByUserId());
            ReviewDTO oldReviewSnapshot = new ReviewDTO(oldReview);
            review.setId(id);
            Review entityToSave = review.toEntity();
            entityToSave.setCreatedByUserId(oldReview.getCreatedByUserId());
            ReviewDTO savedDTO = new ReviewDTO(reviewRepo.save(entityToSave));
            auditLogService.logUpdate("Review", id,"Opinia Klienta: "+ oldReviewSnapshot.getClient().getFirstName() + oldReviewSnapshot.getClient().getLastName(), oldReviewSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Review. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteReviewById(Long id) {
        try{
            Review existingReview = reviewRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Review not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(existingReview.getCreatedByUserId());
            if (visitDiscountRepo.existsByReviewId(id)) {
                throw new ConflictException("Opinia została użyta przy rabacie wizyty.");
            }
            ReviewDTO reviewSnapshot = new ReviewDTO(existingReview);
            reviewRepo.deleteById(id);
            auditLogService.logDelete("Review", id,"Opinia Klienta: "+ reviewSnapshot.getClient().getFirstName() + reviewSnapshot.getClient().getLastName(), reviewSnapshot);
        } catch (ResourceNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Review. Reason: "+ e.getMessage(), e);
        }
    }

    private void markOtherReviewsAsUsed(Long clientId) {
        List<Review> clientReviews = reviewRepo.findAllByClientId(clientId);
        List<Review> activeGoogleReviews = clientReviews.stream()
                .filter(r -> r.getSource() == ReviewSource.GOOGLE)
                .filter(r -> !r.getIsUsed())
                .collect(Collectors.toList());
        if(!activeGoogleReviews.isEmpty()) {
            for(Review rev : activeGoogleReviews) {
                rev.setIsUsed(true);
                reviewRepo.save(rev);
            }
        }
    }
}
