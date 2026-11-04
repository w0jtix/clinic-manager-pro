package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ExpenseCategoryMigrationTest extends AbstractPostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migration_seedsThirteenCategoriesInDisplayOrder() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, name, color, is_system, is_internal FROM expense_category ORDER BY id");

        assertThat(rows)
                .extracting(r -> r.get("code"), r -> r.get("name"), r -> r.get("color"),
                        r -> r.get("is_system"), r -> r.get("is_internal"))
                .containsExactly(
                        tuple("RENT", "Czynsz", "255,70,162", true, false),
                        tuple("TAXES", "Podatki", "220,20,60", true, false),
                        tuple("ZUS", "ZUS", "30,144,255", true, false),
                        tuple("PRODUCTS", "Produkty", "255,215,0", true, false),
                        tuple("EQUIPMENT", "Gabinetowe", "89,10,252", false, false),
                        tuple("SALARY", "Pensje", "2,189,2", true, false),
                        tuple("SERVICES", "Usługi", "0,206,209", false, false),
                        tuple("CAR", "Auto", "252,85,5", false, false),
                        tuple("TRAINING", "Szkolenia", "15,252,150", false, false),
                        tuple("SUPPLIES", "Zaopatrzenie", "248,131,252", false, false),
                        tuple("UNRELATED", "Niezwiązane", "225,225,225", true, false),
                        tuple("OTHER", "Inne", "255,140,0", false, false),
                        tuple("MIXED", "Mieszana", "150,150,150", true, true));
    }

    @Test
    void migration_dropsLegacyCategoryCheckConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'company_expense_category_check'", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    void foreignKey_rejectsUnknownCodeOnCompanyExpense() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO company_expense (source, expense_date, category, total_net, total_vat, total_value) "
                        + "VALUES ('Test', CURRENT_DATE, 'XYZ', 0, 0, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKey_rejectsUnknownCodeOnKsefSellerDefault() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ksef_seller (nip, auto_mark_products, default_expense_category) VALUES ('9999999999', false, 'XYZ')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKey_acceptsSeededCode() {
        int inserted = jdbcTemplate.update(
                "INSERT INTO company_expense (source, expense_date, category, total_net, total_vat, total_value) "
                        + "VALUES ('Test', CURRENT_DATE, 'CAR', 0, 0, 0)");

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void nameIndex_rejectsNameDifferingOnlyByCaseAndSpaces() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO expense_category (code, name, color) VALUES ('CTEST1', ' czynsz ', '1,1,1')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("\"uq_expense_category_name_ci\""));
    }

    @Test
    void colorIndex_rejectsDuplicateColor() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO expense_category (code, name, color) VALUES ('CTEST2', 'Nowa', '255,70,162')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("\"uq_expense_category_color\""));
    }

    @Test
    void newCategory_defaultsToNonSystemAndNonInternal() {
        jdbcTemplate.update("INSERT INTO expense_category (code, name, color) VALUES ('CTEST3', 'Leasing', '99,102,241')");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT is_system, is_internal FROM expense_category WHERE code = 'CTEST3'");
        assertThat(row.get("is_system")).isEqualTo(false);
        assertThat(row.get("is_internal")).isEqualTo(false);
    }
}
