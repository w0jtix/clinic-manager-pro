package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ProductCategoryService;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class ProductCategoryRestoreConcurrencyIntegrationTest extends AbstractPostgresTest {

    private static final List<String> PALETTE = List.of(
            "239,68,68", "249,115,22", "234,179,8", "34,197,94", "20,184,166",
            "59,130,246", "99,102,241", "168,85,247", "236,72,153", "120,113,108");
    private static final long TIMEOUT_SECONDS = 20;

    @Autowired private ProductCategoryService productCategoryService;
    @Autowired private ProductCategoryRepo productCategoryRepo;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean
    private AuditLogService auditLogService;

    private final CountDownLatch createFlushed = new CountDownLatch(1);
    private final CountDownLatch releaseCreator = new CountDownLatch(1);
    private TransactionTemplate transactionTemplate;
    private ExecutorService executor;
    private String suffix;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        suffix = " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        releaseCreator.countDown();
        executor.shutdown();
        executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        productCategoryRepo.findAll().stream()
                .filter(category -> category.getName().endsWith(suffix))
                .forEach(productCategoryRepo::delete);
    }

    @Test
    void restoreIfHidden_shouldWaitForConcurrentCreateAndPickAnotherFreeColor_whenPaletteColorTakenMeanwhile() throws Exception {
        String sharedColor = unusedNonPaletteColor();
        productCategoryRepo.save(ProductCategory.builder().name("Aktywna" + suffix).color(sharedColor).build());
        ProductCategory hidden = productCategoryRepo.save(ProductCategory.builder()
                .name("Ukryta" + suffix).color(sharedColor).isDeleted(true).build());
        List<String> freePalette = freePaletteColors();
        String contestedColor = freePalette.get(0);
        String expectedRestoredColor = freePalette.get(1);

        Future<Long> creator = executor.submit(() -> transactionTemplate.execute(status -> {
            ProductCategoryDTO created = productCategoryService.createCategory(
                    new ProductCategoryDTO(null, "Nowa" + suffix, contestedColor));
            createFlushed.countDown();
            await(releaseCreator);
            return created.getId();
        }));
        await(createFlushed);

        Future<?> restorer = executor.submit(() -> transactionTemplate.executeWithoutResult(status ->
                productCategoryService.restoreIfHidden(productCategoryRepo.findById(hidden.getId()).orElseThrow())));
        awaitTransactionWaitingForLock();
        releaseCreator.countDown();

        Long createdId = creator.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertDoesNotThrow(() -> restorer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        ProductCategory created = productCategoryRepo.findById(createdId).orElseThrow();
        ProductCategory restored = productCategoryRepo.findById(hidden.getId()).orElseThrow();
        assertThat(created.getColor()).isEqualTo(contestedColor);
        assertThat(created.getIsDeleted()).isFalse();
        assertThat(restored.getIsDeleted()).isFalse();
        assertThat(restored.getColor()).isEqualTo(expectedRestoredColor);
        assertThat(restored.getName()).isEqualTo("Ukryta" + suffix);
        assertThat(productCategoryRepo.findActiveColors()).doesNotHaveDuplicates();
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(hidden.getId()), eq("Ukryta" + suffix), any(), any());
    }

    private String unusedNonPaletteColor() {
        Set<String> taken = new HashSet<>(productCategoryRepo.findActiveColors());
        return IntStream.rangeClosed(1, 255)
                .mapToObj(value -> value + ",3,7")
                .filter(color -> !taken.contains(color) && !PALETTE.contains(color))
                .findFirst()
                .orElseThrow();
    }

    private List<String> freePaletteColors() {
        Set<String> taken = new HashSet<>(productCategoryRepo.findActiveColors());
        List<String> free = PALETTE.stream().filter(color -> !taken.contains(color)).toList();
        assertThat(free).hasSizeGreaterThanOrEqualTo(2);
        return free;
    }

    private void awaitTransactionWaitingForLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE NOT granted", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Restoring transaction never waited for a lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the concurrent transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
