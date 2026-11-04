package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.DuplicateClientException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.Sale;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.repo.projection.ClientMatchProjection;
import com.clinic.clinicmanager.repo.projection.CompanyRevenueProjection;
import com.clinic.clinicmanager.repo.projection.NameQuantityProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ClientCardContentChecker;
import com.clinic.clinicmanager.service.ClientService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private static final int MONTHS_IN_YEAR = 12;

    private final ClientRepo clientRepo;
    private final VisitRepo visitRepo;
    private final ClientDebtRepo clientDebtRepo;
    private final VoucherRepo voucherRepo;
    private final ReviewRepo reviewRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;
    private final ClientCardContentChecker clientCardContentChecker;

    @Override
    public ClientDTO getClientById(Long id) {
        return maskAddressForNonMaster(new ClientDTO(clientRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + id))));
    }

    private ClientDTO maskAddressForNonMaster(ClientDTO clientDTO) {
        if (clientDTO != null && !SessionUtils.hasRole(AppRoleHierarchy.MASTER)) {
            clientDTO.setAddress(null);
            clientDTO.setPostalCode(null);
            clientDTO.setCity(null);
        }
        return clientDTO;
    }

    @Override
    public ClientSummaryDTO getClientSummary(Long id) {
        requireClient(id);

        List<ClientDebt> unpaidDebts = clientDebtRepo.findAllByClientIdAndPaymentStatus(id, PaymentStatus.UNPAID);
        BigDecimal debtTotal = unpaidDebts.stream()
                .map(ClientDebt::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int activeVouchersCount = (int) voucherRepo.countByClientIdAndStatus(id, VoucherStatus.ACTIVE);

        BigDecimal totalSpent = null;
        if (SessionUtils.hasRole(AppRoleHierarchy.COMPANY)) {
            BigDecimal sum = visitRepo.sumTotalValueByClientId(id);
            totalSpent = sum != null ? sum : BigDecimal.ZERO;
        }

        return new ClientSummaryDTO(
                visitRepo.countByClientIdAndAbsenceFalse(id),
                totalSpent,
                visitRepo.countByClientIdAndAbsenceTrue(id),
                visitRepo.countLatenessByClientId(id),
                topByQuantity(visitRepo.findServiceQuantitiesByClientId(id)),
                topByQuantity(visitRepo.findProductQuantitiesByClientId(id)),
                !unpaidDebts.isEmpty(),
                debtTotal,
                activeVouchersCount,
                reviewInfo(id, ReviewSource.GOOGLE),
                reviewInfo(id, ReviewSource.BOOKSY));
    }

    private ClientReviewInfoDTO reviewInfo(Long clientId, ReviewSource source) {
        return ClientReviewInfoDTO.of(
                reviewRepo.existsByClientIdAndSource(clientId, source),
                reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(clientId, source));
    }

    @Override
    public ClientSpendChartDTO getSpendChart(Long id, Integer year) {
        requireClient(id);

        int targetYear = year != null ? year : LocalDate.now().getYear();

        List<BigDecimal> months = new ArrayList<>(Collections.nCopies(MONTHS_IN_YEAR, BigDecimal.ZERO));
        List<Integer> visitCounts = new ArrayList<>(Collections.nCopies(MONTHS_IN_YEAR, 0));
        for (CompanyRevenueProjection row : visitRepo.findMonthlySpendByClientId(id, targetYear)) {
            Integer month = row.getPeriod();
            if (month == null || month < 1 || month > MONTHS_IN_YEAR) continue;
            months.set(month - 1, row.getRevenue() != null ? row.getRevenue() : BigDecimal.ZERO);
            visitCounts.set(month - 1, row.getVisitCount() != null ? row.getVisitCount() : 0);
        }

        LocalDate first = visitRepo.findFirstVisitDateByClientId(id);
        LocalDate last = visitRepo.findLastVisitDateByClientId(id);

        return new ClientSpendChartDTO(
                targetYear,
                first != null ? first.getYear() : targetYear,
                last != null ? last.getYear() : targetYear,
                SessionUtils.hasRole(AppRoleHierarchy.COMPANY) ? months : null,
                visitCounts);
    }

    private ClientTopItemDTO topByQuantity(List<NameQuantityProjection> rows) {
        return rows.stream()
                .filter(row -> row.getName() != null && row.getQuantity() != null)
                .max(Comparator.comparingLong(NameQuantityProjection::getQuantity))
                .map(row -> new ClientTopItemDTO(row.getName(), row.getQuantity()))
                .orElse(null);
    }

    private void requireClient(Long id) {
        if (!clientRepo.existsById(id)) {
            throw new ResourceNotFoundException("Client not found with given id: " + id);
        }
    }

    @Override
    public List<ClientDTO> getClients(ClientFilterDTO filter) {
        if(isNull(filter)) {
            filter = new ClientFilterDTO();
        }
        List<ClientDTO> clients = clientRepo.findAllWithFilters(
                filter.getKeyword(),
                filter.getBoostClient(),
                filter.getSignedRegulations(),
                filter.getHasDebts(),
                filter.getDiscountId());
        clients.forEach(this::maskAddressForNonMaster);
        return clients;
    }

    @Override
    @Transactional
    public ClientDTO createClient(ClientDTO clientDTO) {
        boolean isMaster = SessionUtils.hasRole(AppRoleHierarchy.MASTER);
        ensureNoExactDuplicate(clientDTO.getFirstName(), clientDTO.getLastName(), clientDTO.getPhoneNumber(), null);
        try{
            Client clientEntity = clientDTO.toEntity();
            if (!isMaster) {
                clientEntity.setAddress(null);
                clientEntity.setPostalCode(null);
                clientEntity.setCity(null);
                clientEntity.setDiscount(null);
            }
            clientEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            Client saved = clientRepo.save(clientEntity);
            auditLogService.logCreate("Client", saved.getId(), saved.getFirstName() + saved.getLastName(), saved);
            return maskAddressForNonMaster(new ClientDTO(saved));
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch(Exception e) {
            throw new CreationException("Failed to create Client.", e);
        }
    }

    @Override
    @Transactional
    public ClientDTO updateClient(Long id, ClientDTO clientDTO) {
        boolean isMaster = SessionUtils.hasRole(AppRoleHierarchy.MASTER);
        try{
            Client oldClient = clientRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + id));
            if (Boolean.TRUE.equals(oldClient.getIsDeleted())) {
                throw new ConflictException("Client is deleted. Restore the client before editing.");
            }
            ensureNoExactDuplicate(clientDTO.getFirstName(), clientDTO.getLastName(), clientDTO.getPhoneNumber(), id);

            ClientDTO oldClientSnapshot = new ClientDTO(oldClient);

            clientDTO.setId(id);
            Client entityToSave = clientDTO.toEntity();
            if (!isMaster) {
                entityToSave.setAddress(oldClient.getAddress());
                entityToSave.setPostalCode(oldClient.getPostalCode());
                entityToSave.setCity(oldClient.getCity());
                entityToSave.setDiscount(oldClient.getDiscount());
            }
            entityToSave.setCreatedByUserId(oldClient.getCreatedByUserId());
            entityToSave.setIsDeleted(oldClient.getIsDeleted());
            entityToSave.setRemarks(oldClient.getRemarks());
            Client saved = clientRepo.save(entityToSave);
            auditLogService.logUpdate("Client", id, oldClientSnapshot.getFirstName() + oldClientSnapshot.getLastName(), oldClientSnapshot, new ClientDTO(saved));
            return maskAddressForNonMaster(new ClientDTO(saved));
        } catch (ResourceNotFoundException | DuplicateClientException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch(Exception e) {
            throw new UpdateException("Failed to update Client.", e);
        }
    }

    @Override
    @Transactional
    public void deleteClientById(Long id) {
        try {
            Client client = clientRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));

            ownershipService.checkOwnershipOrAdmin(client.getCreatedByUserId());
            if (client.getIsDeleted()) {
                throw new DeletionException("Client is already soft-deleted.");
            }

            client.setDiscount(null);

            if (hasVisitReferences(id) || hasClientDebtReferences(id) ||
                hasVoucherReferences(id) || hasReviewReferences(id) ||
                clientCardContentChecker.hasProtectedContent(id)) {
                client.softDelete();
                clientRepo.save(client);
            } else {
                clientRepo.deleteById(id);
            }

            auditLogService.logDelete("Client", id, client.getFirstName() + client.getLastName(), client);
        } catch (ResourceNotFoundException | DeletionException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Client.", e);
        }
    }

    @Override
    public ClientDuplicateCheckDTO checkDuplicates(String firstName, String lastName, String phoneNumber, Long excludeId) {
        ClientMatchDTO exactMatch = clientRepo.findExactMatch(firstName, lastName, phoneNumber, excludeId, false)
                .map(ClientMatchDTO::new)
                .orElse(null);
        List<ClientMatchDTO> similar = clientRepo.findSimilar(firstName, lastName, phoneNumber, excludeId).stream()
                .map(ClientMatchDTO::new)
                .toList();
        return new ClientDuplicateCheckDTO(exactMatch, similar);
    }

    @Override
    @Transactional
    public ClientDTO updateFromCard(Long id, ClientCardDataDTO data) {
        Client client = clientRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + id));
        if (Boolean.TRUE.equals(client.getIsDeleted())) {
            throw new ConflictException("Client is deleted. Restore the client before editing the card.");
        }
        ensureNoExactDuplicate(data.getFirstName(), data.getLastName(), data.getPhoneNumber(), id);

        ClientDTO oldClientSnapshot = new ClientDTO(client);

        client.setFirstName(data.getFirstName());
        client.setLastName(data.getLastName());
        client.setBirthDate(data.getBirthDate());
        client.setPhoneNumber(data.getPhoneNumber());
        if (data.getRedFlag() != null) {
            client.setRedFlag(data.getRedFlag());
        }
        if (Boolean.TRUE.equals(data.getSignedRegulations())) {
            client.setSignedRegulations(true);
        }
        client.setRemarks(trimToNull(data.getRemarks()));

        Client saved = clientRepo.save(client);
        auditLogService.logUpdate("Client", id, oldClientSnapshot.getFirstName() + oldClientSnapshot.getLastName(), oldClientSnapshot, new ClientDTO(saved));
        return maskAddressForNonMaster(new ClientDTO(saved));
    }

    @Override
    @Transactional
    public ClientDTO restoreClient(Long id, ClientIdentityDTO identity) {
        Client client = clientRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + id));
        if (!Boolean.TRUE.equals(client.getIsDeleted())) {
            throw new ConflictException("Client is not deleted.");
        }
        if (!SessionUtils.hasRole(AppRoleHierarchy.MASTER) && !identityMatches(id, identity)) {
            throw new AccessDeniedException("Provided client data does not match the deleted client.");
        }
        clientRepo.findExactMatch(client.getFirstName(), client.getLastName(), client.getPhoneNumber(), id, true)
                .ifPresent(match -> {
                    throw duplicateOf(match);
                });

        ClientDTO oldClientSnapshot = new ClientDTO(client);
        client.restore();
        Client saved = clientRepo.save(client);
        auditLogService.logUpdate("Client", id, oldClientSnapshot.getFirstName() + oldClientSnapshot.getLastName(), oldClientSnapshot, new ClientDTO(saved));
        return maskAddressForNonMaster(new ClientDTO(saved));
    }

    private boolean identityMatches(Long id, ClientIdentityDTO identity) {
        return identity != null
                && clientRepo.matchesIdentity(id, identity.getFirstName(), identity.getLastName(), identity.getPhoneNumber());
    }

    private void ensureNoExactDuplicate(String firstName, String lastName, String phoneNumber, Long excludeId) {
        clientRepo.findExactMatch(firstName, lastName, phoneNumber, excludeId, false)
                .ifPresent(match -> {
                    throw duplicateOf(match);
                });
    }

    private DuplicateClientException duplicateOf(ClientMatchProjection match) {
        return new DuplicateClientException(
                match.getId(),
                Boolean.TRUE.equals(match.getIsDeleted()),
                !hasDigits(match.getPhoneNumber()),
                match.getFirstName(),
                match.getLastName());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasDigits(String phoneNumber) {
        return phoneNumber != null && phoneNumber.chars().anyMatch(Character::isDigit);
    }

    private boolean hasVisitReferences(Long clientId) {
        return visitRepo.existsByClientId(clientId);
    }

    private boolean hasClientDebtReferences(Long clientId) {
        return clientDebtRepo.existsByClientId(clientId);
    }

    private boolean hasVoucherReferences(Long clientId) {
        return voucherRepo.existsByClientId(clientId);
    }

    private boolean hasReviewReferences(Long clientId) {
        return reviewRepo.existsByClientId(clientId);
    }

}
