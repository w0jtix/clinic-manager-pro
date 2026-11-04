package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.DTO.request.VisitFilterDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.InsufficientPaymentException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.*;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.repo.projection.ClientCardCreatedAtProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryCardDateProjection;
import com.clinic.clinicmanager.repo.projection.VisitInvoiceIdProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.VisitService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private static final int MAX_VISIT_RANGE_DAYS = 31;

    private final VisitRepo visitRepo;
    private final ClientRepo clientRepo;
    private final EmployeeRepo employeeRepo;
    private final AppSettingsRepo appSettingsRepo;
    private final BaseServiceRepo baseServiceRepo;
    private final BaseServiceVariantRepo baseServiceVariantRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;
    private final VisitPricingCalculator pricingCalculator;
    private final VisitDiscountService discountService;
    private final VisitDebtService debtService;
    private final VisitSaleService saleService;
    private final VisitPaymentService paymentService;
    private final NamedInvoiceRepo namedInvoiceRepo;
    private final ClientCardRepo clientCardRepo;
    private final TherapyEntryRepo therapyEntryRepo;


    @Override
    public VisitDTO getVisitById(Long id) {
        VisitDTO visit = new VisitDTO(visitRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given id: " + id)));
        fillNamedInvoiceIds(List.of(visit));
        return visit;
    }

    private void requireReceiptPermission(VisitDTO visit) {
        if (Boolean.FALSE.equals(visit.getReceipt()) && !SessionUtils.hasRole(AppRoleHierarchy.MASTER)) {
            throw new AccessDeniedException("Brak uprawnień.");
        }
    }

    private void fillNamedInvoiceIds(List<VisitDTO> visits) {
        List<Long> visitIds = visits.stream()
                .map(VisitDTO::getId)
                .filter(Objects::nonNull)
                .toList();
        if (visitIds.isEmpty()) {
            return;
        }

        Map<Long, Long> invoiceIdByVisitId = new HashMap<>();
        for (VisitInvoiceIdProjection row : namedInvoiceRepo.findInvoiceIdsByVisitIds(visitIds)) {
            if (row.getVisitId() == null || row.getInvoiceId() == null) {
                continue;
            }
            invoiceIdByVisitId.merge(row.getVisitId(), row.getInvoiceId(), Math::max);
        }

        visits.forEach(visit -> visit.setNamedInvoiceId(invoiceIdByVisitId.get(visit.getId())));
    }

    private Page<VisitDTO> toDtoPage(Page<Visit> visits) {
        Page<VisitDTO> page = visits.map(VisitDTO::new);
        fillNamedInvoiceIds(page.getContent());
        return page;
    }

    @Override
    public Page<VisitDTO> getVisits(VisitFilterDTO filter, int page, int size) {
        if(isNull(filter)) {
            filter = new VisitFilterDTO();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));

        LocalDate dateFrom;
        LocalDate dateTo;
        if (filter.getDateFrom() != null || filter.getDateTo() != null) {
            validateDateRange(filter.getDateFrom(), filter.getDateTo());
            dateFrom = filter.getDateFrom();
            dateTo = filter.getDateTo();
        } else {
            dateFrom = LocalDate.of(filter.getYear(), filter.getMonth(), 1);
            dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());
        }

        Page<Visit> visits = visitRepo.findAllWithFilters(
                filter.getClientIds(),
                filter.getServiceIds(),
                filter.getEmployeeIds(),
                filter.getIsBoost(),
                filter.getIsVip(),
                filter.getDelayed(),
                filter.getAbsence(),
                filter.getHasDiscount(),
                filter.getHasSale(),
                dateFrom,
                dateTo,
                filter.getPaymentStatus(),
                filter.getTotalValueFrom(),
                filter.getTotalValueTo(),
                        pageable);
        Page<VisitDTO> result = toDtoPage(visits);
        fillMissingTherapyEntries(result.getContent());
        return result;
    }

    private void fillMissingTherapyEntries(List<VisitDTO> visits) {
        if (visits.isEmpty()) {
            return;
        }
        visits.forEach(visit -> visit.setMissingTherapyEntry(false));

        Set<Long> clientIds = visits.stream()
                .filter(this::isTherapyCandidate)
                .map(visit -> visit.getClient().getId())
                .collect(Collectors.toSet());
        if (clientIds.isEmpty()) {
            return;
        }

        Map<Long, ClientCardCreatedAtProjection> cardByClientId = new HashMap<>();
        for (ClientCardCreatedAtProjection card : clientCardRepo.findCreatedAtByClientIdIn(clientIds)) {
            cardByClientId.put(card.getClientId(), card);
        }

        Map<VisitDTO, Long> cardIdByVisit = new IdentityHashMap<>();
        for (VisitDTO visit : visits) {
            if (!isTherapyCandidate(visit)) {
                continue;
            }
            ClientCardCreatedAtProjection card = cardByClientId.get(visit.getClient().getId());
            if (card == null || card.getCreatedAt() == null
                    || visit.getDate().isBefore(card.getCreatedAt().toLocalDate())) {
                continue;
            }
            cardIdByVisit.put(visit, card.getId());
        }
        if (cardIdByVisit.isEmpty()) {
            return;
        }

        LocalDate dateFrom = cardIdByVisit.keySet().stream()
                .map(VisitDTO::getDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate dateTo = cardIdByVisit.keySet().stream()
                .map(VisitDTO::getDate)
                .max(LocalDate::compareTo)
                .orElseThrow();

        Map<Long, Set<LocalDate>> entryDatesByCardId = new HashMap<>();
        for (TherapyEntryCardDateProjection row : therapyEntryRepo.findValidEntryDatesByCardIdInAndDateBetween(
                new HashSet<>(cardIdByVisit.values()), dateFrom, dateTo)) {
            entryDatesByCardId.computeIfAbsent(row.getCardId(), id -> new HashSet<>()).add(row.getEntryDate());
        }

        cardIdByVisit.forEach((visit, cardId) -> visit.setMissingTherapyEntry(
                !entryDatesByCardId.getOrDefault(cardId, Set.of()).contains(visit.getDate())));
    }

    private boolean isTherapyCandidate(VisitDTO visit) {
        return !Boolean.TRUE.equals(visit.getAbsence())
                && visit.getDate() != null
                && visit.getClient() != null
                && visit.getClient().getId() != null;
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new ValidationException("Zakres dat wymaga podania obu dat: od i do.");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new ValidationException("Data początkowa nie może być późniejsza niż data końcowa.");
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) + 1 > MAX_VISIT_RANGE_DAYS) {
            throw new ValidationException("Zakres dat nie może przekraczać " + MAX_VISIT_RANGE_DAYS + " dni.");
        }
    }

    @Override
    public Page<VisitDTO> getVisitsByClientId(Long clientId, Pageable pageable) {
        Client client = clientRepo.findOneById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + clientId));

        Page<Visit> visits = visitRepo.findAllByClientId(clientId, pageable);

        return toDtoPage(visits);
    }

    @Override
    public VisitDTO getVisitPreview(VisitDTO visit) {
        requireReceiptPermission(visit);

        Client client = new Client();
        if(visit.getClient() != null) {
            client = clientRepo.findOneById(visit.getClient().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + visit.getClient().getId()));
        }

        AppSettings appSettings = appSettingsRepo.getSettings();

        discountService.processVisitDiscounts(visit, client, appSettings, true);
        debtService.processDebtRedemptions(visit, client, true);
        processVisitItems(visit);
        saleService.processSale(visit, appSettings, true);

        pricingCalculator.calculateVisitTotals(visit, appSettings, true);
        pricingCalculator.previewPaymentStatus(visit);
        
        return visit;
    }

    @Override
    @Transactional
     public VisitDTO createVisit(VisitDTO visit) {
        requireReceiptPermission(visit);
        try{
            if (visit.getDate() != null && visit.getDate().isAfter(LocalDate.now())) {
                throw new CreationException("Visit date cannot be in the future.");
            }

            Client client = clientRepo.findOneById(visit.getClient().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + visit.getClient().getId()));

            if (Boolean.TRUE.equals(client.getIsDeleted())) {
                throw new ConflictException("Client is deleted. Restore the client before adding a visit.");
            }

            AppSettings appSettings = appSettingsRepo.getSettings();

            Visit savedVisit = visitRepo.save(createBaseEntity(visit,  client));

            visit.setId(savedVisit.getId());

            discountService.processVisitDiscounts(visit, client, appSettings, false);
            processBoostFlag(visit, client);
            debtService.processDebtRedemptions(visit, client, false);
            processVisitItems(visit);
            saleService.processSale(visit, appSettings, false);
            paymentService.processPayments(visit);

            validatePaymentsCoverSaleAndDebts(visit);

            pricingCalculator.calculateVisitTotals(visit, appSettings, false);


            Visit entityToUpdate = visit.toEntity();
            entityToUpdate.setCreatedByUserId(savedVisit.getCreatedByUserId());
            Visit updatedVisit = visitRepo.save(entityToUpdate);

            paymentService.processPaymentStatus(savedVisit, appSettings);

            VisitDTO savedDTO = new VisitDTO(updatedVisit);
            auditLogService.logCreate("Visit", savedDTO.getId(), "Wizyta Klienta: " + savedDTO.getClient().getFirstName() + savedDTO.getClient().getLastName(), savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | IllegalStateException | CreationException
                 | InsufficientPaymentException | ConflictException | ValidationException | AccessDeniedException
                 | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Visit.", e);
        }
    }

    private void validatePaymentsCoverSaleAndDebts(VisitDTO visit) {
        BigDecimal saleValue = visit.getSale() == null || visit.getSale().getItems() == null
                ? BigDecimal.ZERO
                : visit.getSale().getItems().stream()
                    .map(i -> i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal redeemedDebts = visit.getDebtRedemptions() == null
                ? BigDecimal.ZERO
                : visit.getDebtRedemptions().stream()
                    .map(r -> r.getDebtSource() != null && r.getDebtSource().getValue() != null
                            ? r.getDebtSource().getValue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentsSum = visit.getPayments() == null
                ? BigDecimal.ZERO
                : visit.getPayments().stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paymentsSum.compareTo(saleValue.add(redeemedDebts)) < 0) {
            throw new InsufficientPaymentException(
                "Payments cannot be lower than the value of sold products and redeemed debts.");
        }
    }

    @Override
    @Transactional
    public void deleteVisitById(Long id) {
        try {
            Visit existingVisit = visitRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given id: " + id));

            ownershipService.checkOwnershipOrAdmin(existingVisit.getCreatedByUserId());
            if (namedInvoiceRepo.existsBySourceVisitId(id)) {
                throw new ConflictException("Nie można usunąć wizyty z wystawioną fakturą imienną. Najpierw usuń fakturę.");
            }
            VisitDTO visitSnapshot = new VisitDTO(existingVisit);

            saleService.undoSale(existingVisit);
            paymentService.undoVoucherPayment(existingVisit);
            debtService.undoClientDebtRedemption(existingVisit);
            discountService.undoGoogleReviewAssignment(existingVisit);
            undoBoostFlag(existingVisit);
            if(existingVisit.getAbsence() || !paymentService.isFullyPaid(existingVisit)) {
                debtService.checkIfDebtIsPaid(existingVisit);
                debtService.undoAbsenceFeeOrPartialPaymentFee(existingVisit);
            }
            paymentService.removePayments(existingVisit);
            visitRepo.deleteById(id);
            auditLogService.logDelete("Visit", id, "Wizyta Klienta: " + visitSnapshot.getClient().getFirstName() + visitSnapshot.getClient().getLastName(), visitSnapshot);
        } catch (ResourceNotFoundException | IllegalStateException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Visit.", e);
        }
    }

    @Override
    public VisitDTO findVisitPaidByVoucher(Long voucherId) {
        return new VisitDTO(visitRepo.findByVoucherId(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given voucherId: " + voucherId)));
    }

    @Override
    public VisitDTO findByDebtSourceVisitId(Long visitId) {
        return new VisitDTO(visitRepo.findByDebtSourceVisitId(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given visitId: " + visitId)));
    }

    @Override
    public VisitDTO getVisitByDebtSourceId(Long debtId) {
        return new VisitDTO(visitRepo.findByDebtSourceId(debtId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given debtId: " + debtId)));
    }

    @Override
    public VisitDTO findByReviewId(Long reviewId) {
        return new VisitDTO(visitRepo.findByReviewId(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given reviewId: " + reviewId)));
    }

    @Override
    public long countVisitsByClientId(Long clientId) {
        return visitRepo.countVisitsByClientId(clientId);
    }

    @Override
    public List<VisitDTO> findAllByDateWithCashPayment(LocalDate date) {
        return visitRepo.findAllByDateWithCashPayment(date)
                .stream()
                .map(VisitDTO::new)
                .collect(Collectors.toList());
    }


    private void processBoostFlag(VisitDTO visit, Client client) {
        if (Boolean.TRUE.equals(visit.getIsBoost()) && !client.getBoostClient()) {
            client.setBoostClient(true);
            clientRepo.save(client);
        }
    }
    private void processVisitItems(VisitDTO visit) {
        if (visit.getItems() == null) return;

        for (VisitItemDTO item : visit.getItems()) {
            BaseService service = baseServiceRepo.findOneById(item.getService().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("BaseService not found with given id: " + item.getService().getId()));

            BigDecimal price = service.getPrice();
            String name = service.getName();
            int duration = service.getDuration();

            if(item.getServiceVariant() != null) {
                BaseServiceVariant serviceVariant = baseServiceVariantRepo.findOneById(item.getServiceVariant().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("BaseService not found with given id: " + item.getServiceVariant().getId()));

                if(!baseServiceRepo.serviceHasVariant(service.getId(), serviceVariant.getId())) {
                    throw new IllegalStateException("Variant does not belong to the given service");
                }

                price = serviceVariant.getPrice();
                name = name + " " + serviceVariant.getName();
                duration = serviceVariant.getDuration();
            }

            item.setPrice(price);
            item.setName(name);
            item.setDuration(duration);
        }

        discountService.applyDiscountsToVisitItems(visit);
    }
    private void undoBoostFlag(Visit visit) {
        if (Boolean.TRUE.equals(visit.getIsBoost()) && visit.getClient().getBoostClient()) {
            Client client = clientRepo.findOneById(visit.getClient().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + visit.getClient().getId()));
            client.setBoostClient(false);
            clientRepo.save(client);
        }
    }
    private Visit createBaseEntity(VisitDTO visit, Client client) {
        Employee employee = employeeRepo.findOneById(visit.getEmployee().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with given id: " + visit.getEmployee().getId()));

        return Visit.builder()
                .client(client)
                .employee(employee)
                .notes(visit.getNotes())
                .receipt(visit.getReceipt() != null ? visit.getReceipt() : true)
                .isBoost(visit.getIsBoost())
                .isVip(visit.getIsVip())
                .delayTime(visit.getDelayTime())
                .absence(visit.getAbsence())
                .date(visit.getDate())
                .paymentStatus(PaymentStatus.UNPAID)
                .serviceDiscounts(new ArrayList<>())
                .items(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .payments(new ArrayList<>())
                .createdByUserId(SessionUtils.getUserIdFromSession())
                .build();
    }
}
