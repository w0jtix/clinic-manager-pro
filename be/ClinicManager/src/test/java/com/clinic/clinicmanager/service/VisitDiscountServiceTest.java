package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.model.constants.VisitDiscountType;
import com.clinic.clinicmanager.repo.DiscountRepo;
import com.clinic.clinicmanager.repo.ReviewRepo;
import com.clinic.clinicmanager.service.impl.VisitDiscountService;
import com.clinic.clinicmanager.service.impl.VisitPricingCalculator;
import com.clinic.clinicmanager.utils.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitDiscountServiceTest {
    @Mock DiscountRepo discountRepo;
    @Mock  ReviewRepo reviewRepo;
    @Mock VisitPricingCalculator pricingCalculator;
    @InjectMocks VisitDiscountService discountService;

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private Client client;
    private AppSettings appSettings;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L)
                .build();

        appSettings = AppSettings.builder()
                .booksyHappyHours(15)
                .googleReviewDiscount(10)
                .build();
    }

    @Test
    void nullDiscounts_doesNothing() {
        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(null);
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        verifyNoInteractions(discountRepo, reviewRepo);
    }
    @Test
    void emptyDiscounts_doesNothing() {
        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>());
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        verifyNoInteractions(discountRepo, reviewRepo);
    }
    @Test
    void absenceAndVip_doesNothing() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CUSTOM);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setAbsence(true);
        visit.setIsVip(true);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        verifyNoInteractions(discountRepo, reviewRepo);
    }
    @Test
    void customDiscount_absence_isFiltered() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CUSTOM);
        discount.setPercentageValue(5);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(true);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        assertTrue(visit.getServiceDiscounts().isEmpty());
    }
    @Test
    void clientDiscount_isVip_isFiltered() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setPercentageValue(15);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(true);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        assertTrue(visit.getServiceDiscounts().isEmpty());
    }
    @Test
    void happyHours_setsPercentageFromSettings() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.HAPPY_HOURS);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        assertEquals(15, visit.getServiceDiscounts().getFirst().getPercentageValue());
    }
    @Test
    void clientDiscount_setsPercentageAndName() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setClientDiscountId(5L);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        Discount discountEntity = new Discount();
        discountEntity.setId(5L);
        discountEntity.setPercentageValue(10);
        discountEntity.setName("TestVal");
        client.setDiscount(discountEntity);

        when(discountRepo.findOneById(5l)).thenReturn(Optional.of(discountEntity));

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        assertEquals(10, visit.getServiceDiscounts().getFirst().getPercentageValue());
        assertEquals("TestVal", visit.getServiceDiscounts().getFirst().getName());
    }
    @Test
    void clientDiscount_discountNotFound_throws() {
        Discount discountEntity = new Discount();
        discountEntity.setId(5L);
        client.setDiscount(discountEntity);

        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setClientDiscountId(5L);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        when(discountRepo.findOneById(5l)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> discountService.processVisitDiscounts(visit, client, appSettings, false));
    }
    @Test
    void googleReview_preview_doesNotSaveReview() {
        Review review = new Review();
        review.setId(10L);
        review.setClient(client);
        review.setSource(ReviewSource.GOOGLE);
        review.setIsUsed(false);

        when(reviewRepo.findFirstByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE))
                .thenReturn(Optional.of(review));

        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, true);

        verify(reviewRepo, never()).save(any());
    }
    @Test
    void googleReview_notPreview_savesReviewAsUsed() {
        Review review = new Review();
        review.setId(10L);
        review.setClient(client);
        review.setSource(ReviewSource.GOOGLE);
        review.setIsUsed(false);

        when(reviewRepo.findFirstByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE))
                .thenReturn(Optional.of(review));

        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.processVisitDiscounts(visit, client, appSettings, false);

        assertTrue(review.getIsUsed());
        verify(reviewRepo).save(review);
    }
    @Test
    void googleReview_notFound_throws() {

        when(reviewRepo.findFirstByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE))
                .thenReturn(Optional.empty());

        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);

        List<VisitDiscountDTO> dsc = new ArrayList<>();
        dsc.add(discount);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(dsc);
        visit.setAbsence(false);
        visit.setIsVip(false);

        assertThrows(ResourceNotFoundException.class,
                () -> discountService.processVisitDiscounts(visit, client, appSettings, false));
    }
    //applyDiscountsToVisitItems()
    @Test
    void noDiscounts_setsRoundedPrice() {
        VisitItemDTO visitItem = new VisitItemDTO();
        visitItem.setPrice(bd(100.005));

        List<VisitItemDTO> items = new ArrayList<>();
        items.add(visitItem);

        VisitDTO visit = new VisitDTO();
        visit.setItems(items);
        visit.setServiceDiscounts(new ArrayList<>());
        visit.setAbsence(false);
        visit.setIsVip(false);

        when(pricingCalculator.roundPrice(any())).thenAnswer(inv -> MoneyUtils.money(inv.getArgument(0)));

        discountService.applyDiscountsToVisitItems(visit);

        assertMoney(100.01, visitItem.getFinalPrice());
    }
    @Test
    void clientDiscount_appliesPercentage() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setPercentageValue(15);

        VisitItemDTO visitItem = new VisitItemDTO();
        visitItem.setPrice(bd(100.00));

        List<VisitItemDTO> items = new ArrayList<>();
        items.add(visitItem);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(items);
        visit.setAbsence(false);
        visit.setIsVip(false);

        when(pricingCalculator.roundPrice(any())).thenAnswer(inv -> MoneyUtils.money(inv.getArgument(0)));

        discountService.applyDiscountsToVisitItems(visit);

        assertMoney(85.00, visitItem.getFinalPrice());
    }
    @Test
    void itemWithFinalPriceAlreadySet_notOverwritten() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setPercentageValue(15);

        VisitItemDTO visitItem = new VisitItemDTO();
        visitItem.setFinalPrice(bd(88.00));

        List<VisitItemDTO> items = new ArrayList<>();
        items.add(visitItem);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(items);
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.applyDiscountsToVisitItems(visit);

        verify(pricingCalculator, never()).roundPrice(any());
        assertMoney(88.00, visitItem.getFinalPrice());
    }
    @Test
    void googleReview_absence_notApplied() {
        VisitDiscountDTO discount = new VisitDiscountDTO();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);
        discount.setPercentageValue(15);

        VisitItemDTO visitItem = new VisitItemDTO();
        visitItem.setPrice(bd(100.00));

        List<VisitItemDTO> items = new ArrayList<>();
        items.add(visitItem);

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(items);
        visit.setAbsence(true);
        visit.setIsVip(false);

        when(pricingCalculator.roundPrice(any())).thenAnswer(inv -> MoneyUtils.money(inv.getArgument(0)));

        discountService.applyDiscountsToVisitItems(visit);

        assertMoney(100.00, visitItem.getFinalPrice());
    }
    @Test
    void isVip_sanitizesClearsFinalPrices() {
        VisitItemDTO visitItem = new VisitItemDTO();
        visitItem.setPrice(bd(100.00));
        visitItem.setFinalPrice(bd(80.00));

        VisitDTO visit = new VisitDTO();
        visit.setServiceDiscounts(new ArrayList<>());
        visit.setItems(new ArrayList<>(List.of(visitItem)));
        visit.setAbsence(false);
        visit.setIsVip(true);

        when(pricingCalculator.roundPrice(any())).thenAnswer(inv -> MoneyUtils.money(inv.getArgument(0)));

        discountService.applyDiscountsToVisitItems(visit);

        assertMoney(100.00, visitItem.getFinalPrice());
    }
    //undoGoogleReviewAssignment()
    @Test
    void noGoogleReviewDiscount_doesNothing() {
        VisitDiscount discount = new VisitDiscount();
        discount.setType(VisitDiscountType.CLIENT_DISCOUNT);
        discount.setPercentageValue(5);

        VisitItem visitItem = new VisitItem();
        visitItem.setPrice(bd(100.00));

        Visit visit = new Visit();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(new ArrayList<>(List.of(visitItem)));
        visit.setAbsence(false);
        visit.setIsVip(false);

        discountService.undoGoogleReviewAssignment(visit);

        verify(reviewRepo, never()).save(any());
    }
    @Test
    void googleReview_setsIsUsedFalse() {
        Review review = new Review();
        review.setId(1L);
        review.setIsUsed(true);
        review.setSource(ReviewSource.GOOGLE);
        review.setClient(client);

        VisitDiscount discount = new VisitDiscount();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);
        discount.setPercentageValue(5);
        discount.setReviewId(1L);

        VisitItem visitItem = new VisitItem();
        visitItem.setPrice(bd(100.00));

        Visit visit = new Visit();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(new ArrayList<>(List.of(visitItem)));
        visit.setAbsence(false);
        visit.setIsVip(false);
        visit.setClient(client);

        when(reviewRepo.findOneById(1L)).thenReturn(Optional.of(review));

        discountService.undoGoogleReviewAssignment(visit);

        assertEquals(false, review.getIsUsed());
    }
    @Test
    void reviewBelongsToDifferentClient_doesNotUnset() {
        Client client2 = Client.builder()
                .id(2L)
                .build();
        Review review = new Review();
        review.setId(1L);
        review.setIsUsed(true);
        review.setSource(ReviewSource.GOOGLE);
        review.setClient(client2);

        VisitDiscount discount = new VisitDiscount();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);
        discount.setPercentageValue(5);
        discount.setReviewId(1L);

        VisitItem visitItem = new VisitItem();
        visitItem.setPrice(bd(100.00));

        when(reviewRepo.findOneById(1L)).thenReturn(Optional.of(review));

        Visit visit = new Visit();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(new ArrayList<>(List.of(visitItem)));
        visit.setAbsence(false);
        visit.setIsVip(false);
        visit.setClient(client);

        discountService.undoGoogleReviewAssignment(visit);

        assertEquals(true, review.getIsUsed());
        verify(reviewRepo, never()).save(any());
    }
    @Test
    void googleReviewWithNullReviewId_doesNothing() {
        Review review = new Review();
        review.setId(1L);
        review.setIsUsed(true);
        review.setSource(ReviewSource.GOOGLE);
        review.setClient(client);

        VisitDiscount discount = new VisitDiscount();
        discount.setType(VisitDiscountType.GOOGLE_REVIEW);
        discount.setPercentageValue(5);
        discount.setReviewId(null);

        VisitItem visitItem = new VisitItem();
        visitItem.setPrice(bd(100.00));

        Visit visit = new Visit();
        visit.setServiceDiscounts(new ArrayList<>(List.of(discount)));
        visit.setItems(new ArrayList<>(List.of(visitItem)));
        visit.setAbsence(false);
        visit.setIsVip(false);
        visit.setClient(client);

        discountService.undoGoogleReviewAssignment(visit);

        verify(reviewRepo, never()).save(any());
    }










}
