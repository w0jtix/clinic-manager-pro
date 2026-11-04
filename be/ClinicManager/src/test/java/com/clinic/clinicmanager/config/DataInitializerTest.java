package com.clinic.clinicmanager.config;

import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AvatarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    private static final Role ADMIN_ROLE = Role.builder().id(1L).name(RoleType.ROLE_ADMIN).build();
    private static final Role USER_ROLE = Role.builder().id(2L).name(RoleType.ROLE_USER).build();

    @Mock RoleRepo roleRepo;
    @Mock UserRepo userRepo;
    @Mock ProductCategoryRepo productCategoryRepo;
    @Mock AppSettingsRepo appSettingsRepo;
    @Mock StatSettingsRepo statSettingsRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AvatarService avatarService;

    private DataInitializer initializer(String username, String password) {
        return new DataInitializer(roleRepo, userRepo, productCategoryRepo, appSettingsRepo, statSettingsRepo,
                passwordEncoder, avatarService, username, password);
    }

    private void stubExistingRoles() {
        lenient().when(roleRepo.findByName(any())).thenAnswer(invocation -> {
            RoleType type = invocation.getArgument(0);
            if (type == RoleType.ROLE_ADMIN) {
                return Optional.of(ADMIN_ROLE);
            }
            if (type == RoleType.ROLE_USER) {
                return Optional.of(USER_ROLE);
            }
            return Optional.of(Role.builder().name(type).build());
        });
    }

    @Test
    void run_shouldCreateAdminWithEncodedPasswordAndRoles_whenNoUsersAndCredentialsProvided() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(0L);
        when(passwordEncoder.encode("very-strong-password")).thenReturn("bcrypt-hash");
        when(avatarService.findFirstFreeAvatar()).thenReturn("avatar1.png");

        initializer("  owner  ", "very-strong-password").run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        User admin = saved.getValue();
        assertEquals("owner", admin.getUsername());
        assertEquals("bcrypt-hash", admin.getPassword());
        assertEquals(Set.of(ADMIN_ROLE, USER_ROLE), admin.getRoles());
        assertEquals("avatar1.png", admin.getAvatar());
    }

    @Test
    void run_shouldFailStartup_whenNoUsersAndCredentialsMissing() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer("", " ").run());

        assertTrue(ex.getMessage().contains("ADMIN_INITIAL_USERNAME"));
        verify(userRepo, never()).save(any());
    }

    @Test
    void run_shouldFailStartup_whenNoUsersAndUsernameBlank() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> initializer("   ", "very-strong-password").run());
        verify(userRepo, never()).save(any());
    }

    @Test
    void run_shouldFailStartupWithoutLeakingPassword_whenPasswordTooShort() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer("owner", "short-pass1").run());

        assertTrue(ex.getMessage().contains("12"));
        assertFalse(ex.getMessage().contains("short-pass1"));
        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void run_shouldNotCreateAdmin_whenUsersExistEvenWithoutAdminAccount() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(3L);

        initializer("", "").run();

        verify(userRepo, never()).save(any());
        verify(userRepo, never()).existsByUsername(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void run_shouldSeedProductCategoriesWithOnlyProduktyAsSystem_whenNoCategories() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(3L);
        when(productCategoryRepo.count()).thenReturn(0L);

        initializer("", "").run();

        ArgumentCaptor<ProductCategory> saved = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepo, times(3)).save(saved.capture());
        Map<String, Boolean> systemByName = saved.getAllValues().stream()
                .collect(Collectors.toMap(ProductCategory::getName, ProductCategory::getIsSystem));
        assertEquals(Map.of("Produkty", true, "Narzędzia", false, "Użytkowe", false), systemByName);
        List<Boolean> deletedFlags = saved.getAllValues().stream().map(ProductCategory::getIsDeleted).toList();
        assertEquals(List.of(false, false, false), deletedFlags);
    }

    @Test
    void run_shouldNotSeedProductCategories_whenCategoriesExist() {
        stubExistingRoles();
        when(userRepo.count()).thenReturn(3L);
        when(productCategoryRepo.count()).thenReturn(2L);

        initializer("", "").run();

        verify(productCategoryRepo, never()).save(any());
    }
}
