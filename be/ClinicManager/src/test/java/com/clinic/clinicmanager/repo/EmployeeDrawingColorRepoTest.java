package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import com.clinic.clinicmanager.utils.DrawingColors;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeDrawingColorRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EmployeeRepo employeeRepo;

    private static String backfillSql() throws Exception {
        try (InputStream input = new ClassPathResource("db/changelog/db.changelog-3.xml").getInputStream()) {
            NodeList changeSets = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                    .getElementsByTagName("changeSet");
            for (int i = 0; i < changeSets.getLength(); i++) {
                Element changeSet = (Element) changeSets.item(i);
                if ("41".equals(changeSet.getAttribute("id"))) {
                    return changeSet.getElementsByTagName("sql").item(0).getTextContent();
                }
            }
        }
        throw new IllegalStateException("Changeset 41 not found");
    }

    private void execute(String sql) {
        em.getEntityManager().createNativeQuery(sql).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<String> colorsById() {
        return em.getEntityManager()
                .createNativeQuery("SELECT drawing_color FROM employee ORDER BY id")
                .getResultList();
    }

    @Test
    void migration41_backfillsExistingEmployees_withPaletteColorsCyclically() throws Exception {
        execute("ALTER TABLE employee DROP CONSTRAINT ck_employee_drawing_color");
        execute("ALTER TABLE employee ALTER COLUMN drawing_color DROP NOT NULL");
        for (int i = 0; i < 12; i++) {
            execute("INSERT INTO employee (name, last_name, is_deleted, employment_type, bonus_percent, sale_bonus_percent) "
                    + "VALUES ('E" + i + "', 'Test', " + (i == 1) + ", 'FULL', 0, 0)");
        }

        execute(backfillSql());
        execute("ALTER TABLE employee ALTER COLUMN drawing_color SET NOT NULL");

        List<String> colors = colorsById();
        assertThat(colors).hasSizeGreaterThanOrEqualTo(12).doesNotContainNull();
        for (int i = 0; i < colors.size(); i++) {
            assertThat(colors.get(i)).isEqualTo(DrawingColors.PALETTE.get(i % DrawingColors.PALETTE.size()));
        }
        assertThat(colors.subList(0, 10)).doesNotHaveDuplicates();
    }

    @Test
    void drawingColor_isNotNull() {
        assertThatThrownBy(() -> execute(
                "INSERT INTO employee (name, last_name, is_deleted, employment_type, bonus_percent, sale_bonus_percent) "
                        + "VALUES ('Bez', 'Koloru', false, 'FULL', 0, 0)"))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void drawingColor_mustBeHexFormat() {
        assertThatThrownBy(() -> execute(
                "INSERT INTO employee (name, last_name, is_deleted, employment_type, bonus_percent, sale_bonus_percent, drawing_color) "
                        + "VALUES ('Zly', 'Kolor', false, 'FULL', 0, 0, 'blue')"))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void findAllDrawingColors_includesDeletedEmployees() {
        em.persistAndFlush(Employee.builder().name("Anna").lastName("Nowak").drawingColor("#2563eb").build());
        Employee deleted = Employee.builder().name("Ola").lastName("Lis").drawingColor("#dc2626").build();
        deleted.softDelete();
        em.persistAndFlush(deleted);

        assertThat(employeeRepo.findAllDrawingColors()).contains("#2563eb", "#dc2626");
        assertThat(employeeRepo.findAllActive()).extracting(Employee::getName).doesNotContain("Ola");
    }
}
