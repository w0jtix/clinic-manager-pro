package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.projection.EmployeeAccountRoleProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import com.clinic.clinicmanager.utils.AvatarCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepoTest extends AbstractPostgresTest {

    private static final String DEDUPLICATION_CHANGESET_ID = "74";

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeRepo employeeRepo;

    @Autowired
    private RoleRepo roleRepo;

    @BeforeEach
    void clearExistingAvatars() {
        jdbcTemplate.update("UPDATE users SET avatar = NULL");
    }

    private User saveUser(String username, String avatar) {
        return userRepo.saveAndFlush(User.builder()
                .username(username)
                .password("hash")
                .avatar(avatar)
                .build());
    }

    private String avatarOf(User user) {
        return jdbcTemplate.queryForObject("SELECT avatar FROM users WHERE id = ?", String.class, user.getId());
    }

    private static String deduplicationSql() throws Exception {
        try (InputStream changelog = new ClassPathResource("db/changelog/db.changelog-3.xml").getInputStream()) {
            NodeList changeSets = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(changelog)
                    .getElementsByTagName("changeSet");
            for (int i = 0; i < changeSets.getLength(); i++) {
                Element changeSet = (Element) changeSets.item(i);
                if (DEDUPLICATION_CHANGESET_ID.equals(changeSet.getAttribute("id"))) {
                    return changeSet.getElementsByTagName("sql").item(0).getTextContent();
                }
            }
        }
        throw new IllegalStateException("Changeset " + DEDUPLICATION_CHANGESET_ID + " not found");
    }

    @Test
    void findAvatarsTakenByOthers_shouldExcludeGivenUserAndNullAvatars() {
        User me = saveUser("avatar-me", "avatar1.png");
        saveUser("avatar-anna", "avatar2.png");
        saveUser("avatar-ewa", "avatar7.png");
        saveUser("avatar-none", null);

        List<String> taken = userRepo.findAvatarsTakenByOthers(me.getId());

        assertThat(taken).containsExactlyInAnyOrder("avatar2.png", "avatar7.png");
    }

    @Test
    void findAllTakenAvatars_shouldReturnOnlyNonNullAvatars() {
        saveUser("avatar-anna", "avatar2.png");
        saveUser("avatar-ewa", "avatar7.png");
        saveUser("avatar-none", null);

        assertThat(userRepo.findAllTakenAvatars()).containsExactlyInAnyOrder("avatar2.png", "avatar7.png");
    }

    @Test
    void existsByAvatarAndIdNot_shouldIgnoreOwnAvatar() {
        User me = saveUser("avatar-me", "avatar1.png");
        User anna = saveUser("avatar-anna", "avatar2.png");

        assertThat(userRepo.existsByAvatarAndIdNot("avatar1.png", me.getId())).isFalse();
        assertThat(userRepo.existsByAvatarAndIdNot("avatar1.png", anna.getId())).isTrue();
        assertThat(userRepo.existsByAvatarAndIdNot("avatar3.png", anna.getId())).isFalse();
    }

    @Test
    void uniqueIndex_shouldRejectDuplicateAvatar() {
        saveUser("avatar-anna", "avatar2.png");

        assertThatThrownBy(() -> saveUser("avatar-ewa", "avatar2.png"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains(AvatarCatalog.UNIQUE_INDEX));
    }

    @Test
    void uniqueIndex_shouldAllowManyNullAvatars() {
        saveUser("avatar-none-1", null);
        saveUser("avatar-none-2", null);

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE avatar IS NULL AND username LIKE 'avatar-none-%'", Integer.class);
        assertThat(nullCount).isEqualTo(2);
    }

    @Test
    void migration_shouldKeepAvatarForLowestIdAndReassignDuplicatesAndInvalidToFreeAvatars() throws Exception {
        jdbcTemplate.execute("DROP INDEX uq_users_avatar");
        User first = saveUser("avatar-first", "avatar1.png");
        User duplicate = saveUser("avatar-duplicate", "avatar1.png");
        User owner2 = saveUser("avatar-owner2", "avatar2.png");
        User invalid = saveUser("avatar-invalid", "legacy.png");
        User owner4 = saveUser("avatar-owner4", "avatar4.png");
        User secondDuplicate = saveUser("avatar-duplicate2", "avatar2.png");
        User none = saveUser("avatar-none", null);

        jdbcTemplate.execute(deduplicationSql());

        assertThat(avatarOf(first)).isEqualTo("avatar1.png");
        assertThat(avatarOf(owner2)).isEqualTo("avatar2.png");
        assertThat(avatarOf(owner4)).isEqualTo("avatar4.png");
        assertThat(avatarOf(duplicate)).isEqualTo("avatar3.png");
        assertThat(avatarOf(invalid)).isEqualTo("avatar5.png");
        assertThat(avatarOf(secondDuplicate)).isEqualTo("avatar6.png");
        assertThat(avatarOf(none)).isNull();
    }

    @Test
    void migration_shouldSetNull_whenNoFreeAvatarsLeft() throws Exception {
        jdbcTemplate.execute("DROP INDEX uq_users_avatar");
        List<User> users = new ArrayList<>();
        for (int i = 0; i < AvatarCatalog.AVATAR_COUNT + 2; i++) {
            users.add(saveUser("avatar-dup-" + i, "avatar1.png"));
        }

        jdbcTemplate.execute(deduplicationSql());

        assertThat(avatarOf(users.getFirst())).isEqualTo("avatar1.png");
        for (int i = 1; i < AvatarCatalog.AVATAR_COUNT; i++) {
            assertThat(avatarOf(users.get(i))).isEqualTo("avatar" + (i + 1) + ".png");
        }
        assertThat(avatarOf(users.get(AvatarCatalog.AVATAR_COUNT))).isNull();
        assertThat(avatarOf(users.get(AvatarCatalog.AVATAR_COUNT + 1))).isNull();
        Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT avatar FROM users WHERE avatar IS NOT NULL GROUP BY avatar HAVING COUNT(*) > 1) d",
                Integer.class);
        assertThat(duplicates).isZero();
    }

    @Test
    void migration_shouldNotChangeAnything_whenDataAlreadyClean() throws Exception {
        User anna = saveUser("avatar-anna", "avatar2.png");
        User ewa = saveUser("avatar-ewa", "avatar7.png");
        User none = saveUser("avatar-none", null);

        int updated = jdbcTemplate.update(deduplicationSql());

        assertThat(updated).isZero();
        assertThat(avatarOf(anna)).isEqualTo("avatar2.png");
        assertThat(avatarOf(ewa)).isEqualTo("avatar7.png");
        assertThat(avatarOf(none)).isNull();
    }

    @Test
    void migration_shouldMakeAvatarColumnNullable() {
        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'avatar'",
                String.class);

        assertThat(nullable).isEqualTo("YES");
    }

    private Employee saveEmployee(String name) {
        return employeeRepo.saveAndFlush(Employee.builder()
                .name(name)
                .lastName("Testowa")
                .drawingColor("#2563eb")
                .build());
    }

    private Role role(RoleType name) {
        return roleRepo.findByName(name)
                .orElseGet(() -> roleRepo.saveAndFlush(Role.builder().name(name).build()));
    }

    @Test
    void findAccountRolesByEmployeeIdIn_shouldReturnUserIdAvatarAndEveryRole_andSkipEmployeeWithoutAccount() {
        Employee withAccount = saveEmployee("Anna");
        Employee withoutAccount = saveEmployee("Ewa");
        User user = userRepo.saveAndFlush(User.builder()
                .username("avatar-employee")
                .password("hash")
                .avatar("avatar3.png")
                .employee(withAccount)
                .roles(new HashSet<>(Set.of(
                        role(RoleType.ROLE_USER),
                        role(RoleType.ROLE_ADMIN))))
                .build());

        List<EmployeeAccountRoleProjection> rows = userRepo.findAccountRolesByEmployeeIdIn(
                List.of(withAccount.getId(), withoutAccount.getId()));

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getEmployeeId()).isEqualTo(withAccount.getId());
            assertThat(row.getUserId()).isEqualTo(user.getId());
            assertThat(row.getAvatar()).isEqualTo("avatar3.png");
        });
        assertThat(rows).extracting(EmployeeAccountRoleProjection::getRoleName)
                .containsExactlyInAnyOrder(RoleType.ROLE_USER, RoleType.ROLE_ADMIN);
    }
}
