package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ReviewDTO;
import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.DTO.request.ReviewFilterDTO;

import java.util.List;

public interface ReviewService {

    ReviewDTO getReviewById(Long id);

    List<ReviewDTO> getReviews(ReviewFilterDTO filter);

    ReviewDTO createReview(ReviewDTO review);

    ReviewDTO updateReview(Long id, ReviewDTO review);

    void deleteReviewById(Long id);
}
