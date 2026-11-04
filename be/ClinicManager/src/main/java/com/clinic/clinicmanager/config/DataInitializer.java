package com.clinic.clinicmanager.config;

import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.service.AvatarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    public static final int ADMIN_PASSWORD_MIN_LENGTH = 12;

    private final RoleRepo roleRepo;
    private final UserRepo userRepo;
    private final ProductCategoryRepo productCategoryRepo;
    private final AppSettingsRepo appSettingsRepo;
    private final StatSettingsRepo statSettingsRepo;
    private final PasswordEncoder passwordEncoder;
    private final AvatarService avatarService;
    private final String initialAdminUsername;
    private final String initialAdminPassword;

    public DataInitializer(RoleRepo roleRepo,
                           UserRepo userRepo,
                           ProductCategoryRepo productCategoryRepo,
                           AppSettingsRepo appSettingsRepo,
                           StatSettingsRepo statSettingsRepo,
                           PasswordEncoder passwordEncoder,
                           AvatarService avatarService,
                           @Value("${app.admin.initial-username:}") String initialAdminUsername,
                           @Value("${app.admin.initial-password:}") String initialAdminPassword) {
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.productCategoryRepo = productCategoryRepo;
        this.appSettingsRepo = appSettingsRepo;
        this.statSettingsRepo = statSettingsRepo;
        this.passwordEncoder = passwordEncoder;
        this.avatarService = avatarService;
        this.initialAdminUsername = initialAdminUsername;
        this.initialAdminPassword = initialAdminPassword;
    }

    @Override
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
        seedProductCategories();
        seedAppSettings();
        seedStatSettings();
    }

    private void seedRoles() {
        for (RoleType roleType : RoleType.values()) {
            if (roleRepo.findByName(roleType).isEmpty()) {
                roleRepo.save(Role.builder().name(roleType).build());
            }
        }
    }

    private void seedAdminUser() {
        if (userRepo.count() > 0) {
            return;
        }
        String username = initialAdminUsername != null ? initialAdminUsername.trim() : "";
        if (!StringUtils.hasText(username) || !StringUtils.hasText(initialAdminPassword)) {
            throw new IllegalStateException("Brak użytkowników w bazie. Uruchom init-ssl.sh/update.sh, które zapytają "
                    + "o pierwszego administratora, albo ustaw ADMIN_INITIAL_USERNAME i ADMIN_INITIAL_PASSWORD.");
        }
        if (initialAdminPassword.length() < ADMIN_PASSWORD_MIN_LENGTH) {
            throw new IllegalStateException("Hasło pierwszego administratora (ADMIN_INITIAL_PASSWORD) musi mieć co najmniej "
                    + ADMIN_PASSWORD_MIN_LENGTH + " znaków.");
        }
        Role adminRole = roleRepo.findByName(RoleType.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));
        Role userRole = roleRepo.findByName(RoleType.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("User role not found"));
        userRepo.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(initialAdminPassword))
                .avatar(avatarService.findFirstFreeAvatar())
                .roles(Set.of(adminRole, userRole))
                .build());
    }

    private void seedProductCategories() {
        if (productCategoryRepo.count() == 0) {
            productCategoryRepo.save(ProductCategory.builder().name("Produkty").color("0,255,4").isSystem(true).build());
            productCategoryRepo.save(ProductCategory.builder().name("Narzędzia").color("255,200,0").build());
            productCategoryRepo.save(ProductCategory.builder().name("Użytkowe").color("0,163,245").build());
        }
    }

    private void seedAppSettings() {
        if (appSettingsRepo.count() == 0) {
            appSettingsRepo.save(AppSettings.builder().build());
        }
    }

    private void seedStatSettings() {
        if (statSettingsRepo.count() == 0) {
            statSettingsRepo.save(StatSettings.builder().build());
        }
    }
}
