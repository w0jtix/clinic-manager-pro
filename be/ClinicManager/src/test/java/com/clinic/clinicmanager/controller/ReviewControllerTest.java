package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ReviewDTO;
import com.clinic.clinicmanager.DTO.request.ReviewFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.service.ReviewService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import(WebSecurityConfig.class)
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ReviewService reviewService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ReviewDTO buildReviewDTO(Long id) {
        ReviewDTO dto = new ReviewDTO();
        dto.setId(id);
        dto.setSource(ReviewSource.GOOGLE);
        dto.setIsUsed(false);
        dto.setIssueDate(LocalDate.of(2025, 1, 1));
        ClientDTO client = new ClientDTO();
        client.setId(1L);
        client.setFirstName("Jan");
        client.setLastName("Kowalski");
        dto.setClient(client);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getReviews_shouldReturn200_whenResultsFound() throws Exception {
        when(reviewService.getReviews(any(ReviewFilterDTO.class)))
                .thenReturn(List.of(buildReviewDTO(1L), buildReviewDTO(2L)));

        mockMvc.perform(post("/api/reviews/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getReviews_shouldReturn204_whenNoResults() throws Exception {
        when(reviewService.getReviews(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/reviews/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getReviewById_shouldReturn200_whenFound() throws Exception {
        when(reviewService.getReviewById(1L)).thenReturn(buildReviewDTO(1L));

        mockMvc.perform(get("/api/reviews/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.source").value("GOOGLE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getReviewById_shouldReturn404_whenNotFound() throws Exception {
        when(reviewService.getReviewById(99L))
                .thenThrow(new ResourceNotFoundException("Review not found with ID: 99"));

        mockMvc.perform(get("/api/reviews/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createReview_shouldReturn201_whenValid() throws Exception {
        when(reviewService.createReview(any(ReviewDTO.class))).thenReturn(buildReviewDTO(1L));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildReviewDTO(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createReview_shouldReturn400_whenCreationFails() throws Exception {
        when(reviewService.createReview(any(ReviewDTO.class)))
                .thenThrow(new CreationException("Failed to create review"));

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildReviewDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateReview_shouldReturn200_whenValid() throws Exception {
        when(reviewService.updateReview(eq(1L), any(ReviewDTO.class))).thenReturn(buildReviewDTO(1L));

        mockMvc.perform(put("/api/reviews/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildReviewDTO(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateReview_shouldReturn404_whenNotFound() throws Exception {
        when(reviewService.updateReview(eq(99L), any(ReviewDTO.class)))
                .thenThrow(new ResourceNotFoundException("Review not found with ID: 99"));

        mockMvc.perform(put("/api/reviews/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildReviewDTO(null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateReview_shouldReturn400_whenUpdateFails() throws Exception {
        when(reviewService.updateReview(eq(1L), any(ReviewDTO.class)))
                .thenThrow(new UpdateException("Failed to update review"));

        mockMvc.perform(put("/api/reviews/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildReviewDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteReview_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/reviews/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteReview_shouldReturn409_whenUsedInVisitDiscount() throws Exception {
        doThrow(new ConflictException("Opinia została użyta przy rabacie wizyty."))
                .when(reviewService).deleteReviewById(1L);

        mockMvc.perform(delete("/api/reviews/1"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteReview_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Review not found with ID: 1"))
                .when(reviewService).deleteReviewById(1L);

        mockMvc.perform(delete("/api/reviews/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteReview_shouldReturn400_whenDeletionFails() throws Exception {
        doThrow(new DeletionException("Failed to delete review"))
                .when(reviewService).deleteReviewById(1L);

        mockMvc.perform(delete("/api/reviews/1"))
                .andExpect(status().isBadRequest());
    }
}
