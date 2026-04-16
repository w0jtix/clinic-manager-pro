package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ClientDebtDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;
import com.clinic.clinicmanager.DTO.request.DebtFilterDTO;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ClientDebtService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class ClientDebtServiceImpl implements ClientDebtService {

    private final ClientDebtRepo debtRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;

    @Override
    public ClientDebtDTO getDebtById(Long id) {
        return new ClientDebtDTO(debtRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Debt not found with given id: " + id)));
    }

    @Override
    public ClientDebtDTO getDebtBySourceVisitId(Long sourceVisitId) {
        return debtRepo.findOneBySourceVisitId(sourceVisitId)
                .map(ClientDebtDTO::new)
                .orElse(null);
    }

    @Override
    public List<ClientDebtDTO> getDebts(DebtFilterDTO filter) {
        if(isNull(filter)) {
            filter = new DebtFilterDTO();
        }

        return debtRepo.findAllWithFilters(
                filter.getPaymentStatus(),
                filter.getKeyword()
                )
                .stream()
                .map(ClientDebtDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClientDebtDTO> getUnpaidDebtsByClientId(Long id) {
        return debtRepo.findAllByClientIdAndPaymentStatus(id, PaymentStatus.UNPAID).stream()
                .map(ClientDebtDTO:: new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClientDebtDTO createDebt(ClientDebtDTO debt) {
        try{
            ClientDebt debtEntity = debt.toEntity();
            debtEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            ClientDebtDTO savedDebt = new ClientDebtDTO(debtRepo.save(debtEntity));
            auditLogService.logCreate("ClientDebt", savedDebt.getId(), "Dług Klienta: " + savedDebt.getClient().getFirstName() + savedDebt.getClient().getLastName(), savedDebt);
            return savedDebt;
        } catch (Exception e) {
            throw new CreationException("Failed to create ClientDebt: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ClientDebtDTO updateDebt(Long id, ClientDebtDTO debt) {
        try{
            ClientDebt oldDebt = debtRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Debt not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(oldDebt.getCreatedByUserId());
            ClientDebtDTO oldDebtSnapshot = new ClientDebtDTO(oldDebt);

            debt.setId(id);
            ClientDebt entityToSave = debt.toEntity();
            entityToSave.setCreatedByUserId(oldDebt.getCreatedByUserId());
            ClientDebtDTO savedDebt = new ClientDebtDTO(debtRepo.save(entityToSave));

            auditLogService.logUpdate("ClientDebt", id,"Dług Klienta: " + oldDebtSnapshot.getClient().getFirstName() + oldDebtSnapshot.getClient().getLastName(), oldDebtSnapshot, savedDebt);
            return savedDebt;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update ClientDebt, Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteDebtById(Long id) {
        try{
            ClientDebt existingDebt = debtRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Debt not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(existingDebt.getCreatedByUserId());
            ClientDebtDTO debtSnapshot = new ClientDebtDTO(existingDebt);
            debtRepo.deleteById(id);
            auditLogService.logDelete("ClientDebt", id,"Dług Klienta: " + debtSnapshot.getClient().getFirstName() + debtSnapshot.getClient().getLastName(),  debtSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete ClientDebt, Reason: " + e.getMessage(), e);
        }
    }
}
