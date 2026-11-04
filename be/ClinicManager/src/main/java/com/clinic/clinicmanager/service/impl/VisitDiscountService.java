package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.DTO.VisitDiscountDTO;
import com.clinic.clinicmanager.DTO.VisitItemDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.model.constants.VisitDiscountType;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.repo.DiscountRepo;
import com.clinic.clinicmanager.repo.ReviewRepo;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitDiscountService {

    private final DiscountRepo discountRepo;
    private final ReviewRepo reviewRepo;
    private final VisitPricingCalculator pricingCalculator;

    public void processVisitDiscounts(VisitDTO visit, Client client, AppSettings settings, boolean preview) {
        if (visit.getServiceDiscounts() == null || visit.getServiceDiscounts().isEmpty() || (visit.getAbsence() && visit.getIsVip())) return;

        List<VisitDiscountDTO> validDiscounts = visit.getServiceDiscounts().stream()
                .filter(discount -> {
                    if (discount.getType() == VisitDiscountType.CUSTOM) {
                        return !visit.getAbsence() && !visit.getIsVip();
                    }
                    if (discount.getType() == VisitDiscountType.HAPPY_HOURS) {
                        return !visit.getAbsence() && !visit.getIsVip();
                    }
                    if (discount.getType() == VisitDiscountType.GOOGLE_REVIEW) {
                        return !visit.getAbsence();
                    }
                    if (discount.getType() == VisitDiscountType.CLIENT_DISCOUNT) {
                        return !visit.getIsVip();
                    }
                    return true;
                })
                .collect(Collectors.toList());

        visit.setServiceDiscounts(validDiscounts);

        for (VisitDiscountDTO discount : visit.getServiceDiscounts()) {
            switch (discount.getType()) {
                case CLIENT_DISCOUNT -> {

                    if (client.getDiscount().getId() != null) {
                        discount.setClientDiscountId(client.getDiscount().getId());
                        applyClientDiscount(discount, client);
                    } else {
                        throw new IllegalStateException("ClientDiscount Id missing!");
                    }

                }
                case HAPPY_HOURS -> {
                    discount.setPercentageValue(settings.getBooksyHappyHours());
                }
                case GOOGLE_REVIEW -> {
                    applyGoogleReviewDiscount(discount, client, settings, preview);
                }
                // case CUSTOM should have percentageValue already set
            }
        }
    }
    public void applyDiscountsToVisitItems(VisitDTO visit) {
        List<VisitDiscountDTO> sortedDiscounts = visit.getServiceDiscounts().stream()
                .sorted(Comparator.comparingInt(this::getDiscountPriority))
                .collect(Collectors.toList());

        sanitizeFinalPrices(visit);

        for (VisitItemDTO visitItem : visit.getItems()) {
            BigDecimal discountedPrice = MoneyUtils.nz(visitItem.getPrice());
            if(visitItem.getFinalPrice() == null) {
                for (VisitDiscountDTO discount : sortedDiscounts) {
                    if (discount.getPercentageValue() == null) continue;

                    switch (discount.getType()) {

                        case CLIENT_DISCOUNT:
                            if(!visit.getIsVip()){
                                discountedPrice = applyPercentageDiscount(discountedPrice, discount.getPercentageValue());
                            }
                            break;

                        case GOOGLE_REVIEW:
                            if(!visit.getAbsence()){
                                discountedPrice = applyPercentageDiscount(discountedPrice, discount.getPercentageValue());
                            }
                            break;
                        case HAPPY_HOURS, CUSTOM:
                            if(!visit.getAbsence() && !visit.getIsVip()){
                                discountedPrice = applyPercentageDiscount(discountedPrice, discount.getPercentageValue());
                            }
                            break;
                    }
                }
                visitItem.setFinalPrice(pricingCalculator.roundPrice(discountedPrice));
            }

        }
    }

    private BigDecimal applyPercentageDiscount(BigDecimal price, int percentage) {
        BigDecimal factor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(percentage).divide(BigDecimal.valueOf(100), 6, MoneyUtils.ROUNDING));
        return price.multiply(factor);
    }
    public void undoGoogleReviewAssignment(Visit visit) {
        for(VisitDiscount discount : visit.getServiceDiscounts()) {
            if(discount.getType().equals(VisitDiscountType.GOOGLE_REVIEW) && discount.getReviewId() != null) {
                Review review = reviewRepo.findOneById(discount.getReviewId())
                        .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + discount.getReviewId()));
                if (review.getSource() == ReviewSource.GOOGLE
                        && Objects.equals(review.getClient().getId(), visit.getClient().getId())) {
                    review.setIsUsed(false);
                    reviewRepo.save(review);
                }
            }
        }
    }


    private void applyClientDiscount(VisitDiscountDTO discount, Client client) {
        Discount clientDiscount = discountRepo.findOneById(discount.getClientDiscountId())
                .orElseThrow(() -> new ResourceNotFoundException("ClientDiscount not found with id: " + discount.getClientDiscountId()));

        discount.setPercentageValue(clientDiscount.getPercentageValue());
        discount.setName(clientDiscount.getName());
    }
    private void applyGoogleReviewDiscount(VisitDiscountDTO discount, Client client, AppSettings settings, boolean preview) {
        Review review = reviewRepo.findFirstByClientIdAndSourceAndIsUsedFalse(client.getId(), ReviewSource.GOOGLE)
                .orElseThrow(() -> new ResourceNotFoundException("No Active Review found with Client id: " + client.getId()));
        if(!preview) {
            review.setIsUsed(true);
            reviewRepo.save(review);
        }
        discount.setReviewId(review.getId());
        discount.setPercentageValue(settings.getGoogleReviewDiscount());
    }
    private int getDiscountPriority(VisitDiscountDTO discount) {
        return switch (discount.getType()) {
            case CLIENT_DISCOUNT -> 1;
            case HAPPY_HOURS -> 2;
            case GOOGLE_REVIEW -> 3;
            case CUSTOM -> 4;
        };
    }
    private void sanitizeFinalPrices(VisitDTO visit) {

        boolean forbidden = visit.getAbsence() || visit.getIsVip();

        for (VisitItemDTO item : visit.getItems()) {
            if (forbidden) {
                item.setFinalPrice(null);
            }
        }
    }

}
