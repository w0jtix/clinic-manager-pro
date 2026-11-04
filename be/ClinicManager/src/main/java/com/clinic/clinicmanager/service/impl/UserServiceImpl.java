package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.RoleDTO;
import com.clinic.clinicmanager.DTO.UserAssignmentDTO;
import com.clinic.clinicmanager.DTO.UserAvatarDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.DTO.UserPreferencesDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyModeService;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.service.TrustedDeviceService;
import com.clinic.clinicmanager.service.UserService;
import com.clinic.clinicmanager.utils.AvatarCatalog;
import com.clinic.clinicmanager.utils.RoleAssignmentRules;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final EmployeeRepo employeeRepo;
    private final AuditLogService auditLogService;
    private final RoleRepo roleRepo;
    private final CompanyModeService companyModeService;
    private final MfaService mfaService;
    private final TrustedDeviceService trustedDeviceService;

    @Override
    public List<UserDTO> getAllUsers() {
        Map<Long, Integer> deviceCounts = trustedDeviceService.countActiveRememberedByUser();
        return userRepo.findAll().stream()
                .map(user -> withDeviceCount(new UserDTO(user), deviceCounts.getOrDefault(user.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserAssignmentDTO> getAllUserAssignments() {
        return userRepo.findAll().stream()
                .map(UserAssignmentDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Long id){
        UserDTO user = new UserDTO(userRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + id)));
        return withDeviceCount(user, trustedDeviceService.countActiveRemembered(id));
    }

    @Override
    public UserAssignmentDTO getUserAssignmentById(Long id) {
        return new UserAssignmentDTO(findUser(id));
    }

    @Override
    public UserAvatarDTO getUserAvatarByEmployeeId(Long employeeId){
        return new UserAvatarDTO(userRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given employee id: " + employeeId)));
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO user) {
        try {
            User existingUser = findUser(id);

            UserDTO oldUserSnapshot = new UserDTO(existingUser);
            boolean isAdmin = SessionUtils.hasRole(AppRoleHierarchy.ADMIN);
            boolean canAssignEmployee = SessionUtils.hasRole(AppRoleHierarchy.COMPANY);
            boolean self = id.equals(SessionUtils.getUserIdFromSession());
            if (!isAdmin && !self && RoleAssignmentRules.hasRole(existingUser.getRoles(), RoleType.ROLE_ADMIN)) {
                throw new AccessDeniedException(RoleAssignmentRules.ADMIN_EDIT_FORBIDDEN);
            }

            Employee employee = existingUser.getEmployee();
            if (canAssignEmployee) {
                employee = resolveEmployee(id, user, employee);
            }
            User entity = user.toEntity();
            entity.setId(id);
            entity.setPassword(existingUser.getPassword());
            entity.setEmployee(employee);
            String requestedAvatar = self || isAdmin ? user.getAvatar() : null;
            entity.setAvatar(resolveAvatar(id, existingUser.getAvatar(), requestedAvatar));
            entity.setCompanyPinHash(existingUser.getCompanyPinHash());
            entity.setMfaSecretEnc(existingUser.getMfaSecretEnc());
            entity.setMfaEnabledAt(existingUser.getMfaEnabledAt());
            entity.setCardTheme(existingUser.getCardTheme());
            if (isAdmin) {
                entity.setRoles(resolveTargetRoles(existingUser, user.getRoles()));
                applyCompanyPin(entity, user.getCompanyPin());
            } else {
                entity.setRoles(existingUser.getRoles());
                entity.setUsername(existingUser.getUsername());
            }
            boolean mfaEligibilityLost = isMfaEligible(existingUser.getRoles()) && !isMfaEligible(entity.getRoles());
            if (mfaEligibilityLost) {
                entity.setMfaSecretEnc(null);
                entity.setMfaEnabledAt(null);
            }

            saveUser(entity);
            if (mfaEligibilityLost) {
                mfaService.purgeMfaArtifacts(id);
            }
            UserDTO savedDTO = new UserDTO(findUser(id));
            auditLogService.logUpdate("User", id, oldUserSnapshot.getUsername(), oldUserSnapshot, savedDTO);
            return withDeviceCount(savedDTO, trustedDeviceService.countActiveRemembered(id));
        } catch (ConflictException | ResourceNotFoundException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update User.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesDTO getMyPreferences(Long userId) {
        return new UserPreferencesDTO(findUser(userId));
    }

    @Override
    public UserPreferencesDTO updateMyPreferences(Long userId, UserPreferencesDTO preferences) {
        if (preferences == null || preferences.getCardTheme() == null) {
            throw new ValidationException("Nie wybrano wyglądu karty.");
        }
        User user = findUser(userId);
        user.setCardTheme(preferences.getCardTheme());
        return new UserPreferencesDTO(userRepo.save(user));
    }

    private String resolveAvatar(Long id, String currentAvatar, String requestedAvatar) {
        if (requestedAvatar == null || requestedAvatar.equals(currentAvatar)) {
            return currentAvatar;
        }
        if (!AvatarCatalog.isValid(requestedAvatar)) {
            throw new ValidationException(AvatarCatalog.AVATAR_INVALID);
        }
        if (userRepo.existsByAvatarAndIdNot(requestedAvatar, id)) {
            throw new ConflictException(AvatarCatalog.AVATAR_TAKEN);
        }
        return requestedAvatar;
    }

    private void saveUser(User entity) {
        try {
            userRepo.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains(AvatarCatalog.UNIQUE_INDEX)) {
                throw new ConflictException(AvatarCatalog.AVATAR_TAKEN, e);
            }
            throw e;
        }
    }

    private Employee resolveEmployee(Long id, UserDTO user, Employee currentEmployee) {
        if (user.getEmployee() == null) {
            return null;
        }
        if (user.getEmployee().getId() == null) {
            return currentEmployee;
        }
        Long employeeId = user.getEmployee().getId();
        userRepo.findByEmployeeId(employeeId).ifPresent(assignedUser -> {
            if (!assignedUser.getId().equals(id)) {
                throw new ConflictException("Pracownik jest już przypisany do użytkownika " + assignedUser.getUsername() + ".");
            }
        });
        return employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with given id: " + employeeId));
    }

    private Set<Role> resolveTargetRoles(User existingUser, List<RoleDTO> requestedRoles) {
        Set<Role> requested = resolveRoles(requestedRoles);
        boolean hadAdmin = RoleAssignmentRules.hasRole(existingUser.getRoles(), RoleType.ROLE_ADMIN);
        boolean wantsAdmin = RoleAssignmentRules.hasRole(requested, RoleType.ROLE_ADMIN);
        if (!hadAdmin && wantsAdmin) {
            throw new ValidationException(RoleAssignmentRules.ADMIN_GRANT_FORBIDDEN);
        }
        if (hadAdmin && !wantsAdmin) {
            throw new ValidationException(RoleAssignmentRules.ADMIN_REVOKE_FORBIDDEN);
        }
        if (hadAdmin) {
            return new HashSet<>(existingUser.getRoles());
        }
        return RoleAssignmentRules.singleBusinessRole(requested);
    }

    private Set<Role> resolveRoles(List<RoleDTO> roles) {
        if (roles == null) {
            return new HashSet<>();
        }
        return roles.stream()
                .map(role -> (role.getId() != null ? roleRepo.findById(role.getId()) : roleRepo.findByName(role.getName()))
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found.")))
                .collect(Collectors.toSet());
    }

    private void applyCompanyPin(User entity, String companyPin) {
        boolean master = entity.getRoles().stream().anyMatch(role -> role.getName() == RoleType.ROLE_MASTER);
        if (!master) {
            entity.setCompanyPinHash(null);
            return;
        }
        if (StringUtils.hasText(companyPin)) {
            entity.setCompanyPinHash(companyModeService.encodeCompanyPin(companyPin));
        } else if (entity.getCompanyPinHash() == null) {
            throw new ValidationException("PIN trybu firmowego jest wymagany przy nadawaniu roli MASTER.");
        }
    }

    private static UserDTO withDeviceCount(UserDTO user, int trustedDevicesCount) {
        user.setTrustedDevicesCount(trustedDevicesCount);
        return user;
    }

    private static boolean isMfaEligible(Set<Role> roles) {
        return RoleAssignmentRules.hasRole(roles, RoleType.ROLE_ADMIN) || RoleAssignmentRules.hasRole(roles, RoleType.ROLE_MASTER);
    }

    private User findUser(Long id) {
        return userRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + id));
    }
}
