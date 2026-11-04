package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StatSettingsRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private StatSettingsRepo settingsRepo;

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private StatSettings persistSettings() {
        StatSettings settings = StatSettings.builder()
                .companyName("ClinicManager")
                .saleBonusPayoutMonths(new LinkedHashSet<>(Set.of(1, 4, 7, 10)))
                .build();
        return settingsRepo.saveAndFlush(settings);
    }

    @Test
    void storesAndReadsCompanyLogoBytes() throws IOException {
        StatSettings settings = persistSettings();
        Long id = settings.getId();
        byte[] logo = pngBytes();
        settings.setCompanyLogo(logo);
        settings.setCompanyLogoContentType("image/png");
        settingsRepo.saveAndFlush(settings);
        em.flush();
        em.clear();

        StatSettings reloaded = settingsRepo.findById(id).orElseThrow();

        assertThat(reloaded.getCompanyLogo()).isEqualTo(logo);
        assertThat(reloaded.getCompanyLogoContentType()).isEqualTo("image/png");
        assertThat(settingsRepo.hasCompanyLogo(id)).isTrue();
    }

    @Test
    void hasCompanyLogoIsFalse_whenLogoIsNotSet() {
        Long id = persistSettings().getId();
        em.clear();

        assertThat(settingsRepo.hasCompanyLogo(id)).isFalse();
    }

    @Test
    void storesAndReadsEmailFooterLogoBytes_independentlyOfCompanyLogo() throws IOException {
        StatSettings settings = persistSettings();
        Long id = settings.getId();
        byte[] footerLogo = pngBytes();
        settings.setEmailFooterLogo(footerLogo);
        settings.setEmailFooterLogoContentType("image/png");
        settingsRepo.saveAndFlush(settings);
        em.flush();
        em.clear();

        StatSettings reloaded = settingsRepo.findById(id).orElseThrow();

        assertThat(reloaded.getEmailFooterLogo()).isEqualTo(footerLogo);
        assertThat(reloaded.getEmailFooterLogoContentType()).isEqualTo("image/png");
        assertThat(settingsRepo.hasEmailFooterLogo(id)).isTrue();
        assertThat(settingsRepo.hasCompanyLogo(id)).isFalse();
    }

    @Test
    void hasEmailFooterLogoIsFalse_whenLogoIsNotSet() {
        Long id = persistSettings().getId();
        em.clear();

        assertThat(settingsRepo.hasEmailFooterLogo(id)).isFalse();
    }

    @Test
    void storesFooterAndContactFields() {
        StatSettings settings = persistSettings();
        Long id = settings.getId();
        settings.setFooterCompanyName("ClinicManager Maria Kowalska");
        settings.setFooterAddress("ul. Testowa 12/3");
        settings.setFooterPostalCode("61-001");
        settings.setFooterCity("Lublin");
        settings.setCompanyPhone("+48 61 000 00 00");
        settings.setCompanyWebsite("https://clinic.example.com");
        settings.setFacebookUrl("https://facebook.com/clinicmanager");
        settings.setInstagramUrl("https://instagram.com/clinicmanager");
        settings.setBooksyUrl("https://booksy.com/clinicmanager");
        settingsRepo.saveAndFlush(settings);
        em.flush();
        em.clear();

        StatSettings reloaded = settingsRepo.findById(id).orElseThrow();

        assertThat(reloaded.getFooterCompanyName()).isEqualTo("ClinicManager Maria Kowalska");
        assertThat(reloaded.getFooterAddress()).isEqualTo("ul. Testowa 12/3");
        assertThat(reloaded.getFooterPostalCode()).isEqualTo("61-001");
        assertThat(reloaded.getFooterCity()).isEqualTo("Lublin");
        assertThat(reloaded.getCompanyPhone()).isEqualTo("+48 61 000 00 00");
        assertThat(reloaded.getCompanyWebsite()).isEqualTo("https://clinic.example.com");
        assertThat(reloaded.getFacebookUrl()).isEqualTo("https://facebook.com/clinicmanager");
        assertThat(reloaded.getInstagramUrl()).isEqualTo("https://instagram.com/clinicmanager");
        assertThat(reloaded.getBooksyUrl()).isEqualTo("https://booksy.com/clinicmanager");
    }
}
