package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.projection.SupplySourceTotalProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductSupplyHistoryRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProductSupplyHistoryRepo supplyHistoryRepo;

    @Autowired
    private ProductRepo productRepo;

    private ProductCategory category;
    private Brand brand;
    private Product product;

    @BeforeEach
    void setUp() {
        category = em.persistAndFlush(ProductCategory.builder().name("Kremy").color("#FF0000").build());
        brand = em.persistAndFlush(Brand.builder().name("Nike").build());
        product = em.persistAndFlush(Product.builder()
                .name("Krem").category(category).brand(brand).supply(10).build());
    }

    private ProductSupplyHistory entry(Product product, int delta, int supplyAfter,
                                        SupplyChangeSource source, LocalDateTime occurredAt) {
        return em.persistAndFlush(ProductSupplyHistory.builder()
                .product(product).delta(delta).supplyAfter(supplyAfter)
                .source(source).sourceLabel("label")
                .occurredAt(occurredAt).performedBy("SYSTEM")
                .build());
    }

    @Test
    void findAllByProductId_returnsOnlyEntriesForGivenProduct() {
        Product other = em.persistAndFlush(Product.builder()
                .name("Serum").category(category).brand(brand).supply(5).build());

        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(other, 5, 10, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());

        Page<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductId(product.getId(), null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void findAllByProductId_returnsOnlyEntriesOfGivenSource_whenSingleSourceFilterApplied() {
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(product, -2, 13, SupplyChangeSource.USAGE_RECORD, LocalDateTime.now());
        entry(product, -1, 12, SupplyChangeSource.VISIT_SALE, LocalDateTime.now());

        Page<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductId(
                product.getId(), List.of(SupplyChangeSource.USAGE_RECORD), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSource()).isEqualTo(SupplyChangeSource.USAGE_RECORD);
    }

    @Test
    void findAllByProductId_returnsUnionOfSources_whenMultipleSourcesFilterApplied() {
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(product, -1, 14, SupplyChangeSource.KSEF_CORRECTION, LocalDateTime.now());
        entry(product, -2, 12, SupplyChangeSource.USAGE_RECORD, LocalDateTime.now());

        Page<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductId(
                product.getId(),
                List.of(SupplyChangeSource.KSEF_INVOICE, SupplyChangeSource.KSEF_CORRECTION),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(ProductSupplyHistory::getSource)
                .containsExactlyInAnyOrder(SupplyChangeSource.KSEF_INVOICE, SupplyChangeSource.KSEF_CORRECTION);
    }

    @Test
    void findAllByProductId_returnsFullHistory_whenSourceFilterIsNull() {
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(product, -2, 13, SupplyChangeSource.USAGE_RECORD, LocalDateTime.now());
        entry(product, -1, 12, SupplyChangeSource.VISIT_SALE, LocalDateTime.now());

        Page<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductId(
                product.getId(), null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findAllByProductId_paginatesWithinFilter_whenMoreFilteredEntriesThanPageSize() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 8, 0);
        for (int i = 0; i < 40; i++) {
            entry(product, 1, 11 + i, SupplyChangeSource.KSEF_INVOICE, base.plusMinutes(i));
        }
        entry(product, -1, 50, SupplyChangeSource.USAGE_RECORD, base.plusHours(2));

        List<SupplyChangeSource> filter = List.of(SupplyChangeSource.KSEF_INVOICE);

        Page<ProductSupplyHistory> firstPage = supplyHistoryRepo.findAllByProductId(
                product.getId(), filter, PageRequest.of(0, 30));
        Page<ProductSupplyHistory> secondPage = supplyHistoryRepo.findAllByProductId(
                product.getId(), filter, PageRequest.of(1, 30));

        assertThat(firstPage.getTotalElements()).isEqualTo(40);
        assertThat(firstPage.getContent()).hasSize(30);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.getContent()).hasSize(10);
        assertThat(secondPage.isLast()).isTrue();
        assertThat(secondPage.getContent()).extracting(ProductSupplyHistory::getSource)
                .containsOnly(SupplyChangeSource.KSEF_INVOICE);
    }

    @Test
    void findAllByProductId_returnsNewestFirst() {
        LocalDateTime older = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 5, 1, 8, 0);
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, older);
        entry(product, -2, 13, SupplyChangeSource.USAGE_RECORD, newer);

        Page<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductId(
                product.getId(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ProductSupplyHistory::getOccurredAt)
                .containsExactly(newer, older);
    }

    @Test
    void findAllByProductIdInRange_returnsOnlyEntriesWithinGivenWindow() {
        LocalDateTime insideRange = LocalDateTime.of(2026, 3, 15, 10, 0);
        LocalDateTime outsideRange = LocalDateTime.of(2025, 12, 31, 10, 0);

        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, insideRange);
        entry(product, -2, 13, SupplyChangeSource.USAGE_RECORD, outsideRange);

        List<ProductSupplyHistory> result = supplyHistoryRepo.findAllByProductIdInRange(
                product.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2027, 1, 1, 0, 0));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOccurredAt()).isEqualTo(insideRange);
    }

    @Test
    void findSupplyBefore_returnsMostRecentSupplyAfterBeforeGivenTimestamp() {
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.of(2026, 1, 10, 9, 0));
        entry(product, -3, 12, SupplyChangeSource.USAGE_RECORD, LocalDateTime.of(2026, 1, 20, 9, 0));

        List<Integer> result = supplyHistoryRepo.findSupplyBefore(
                product.getId(), LocalDateTime.of(2026, 2, 1, 0, 0), PageRequest.of(0, 1));

        assertThat(result).containsExactly(12);
    }

    @Test
    void findSupplyBefore_returnsEmpty_whenNoEntriesBeforeGivenTimestamp() {
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.of(2026, 6, 1, 9, 0));

        List<Integer> result = supplyHistoryRepo.findSupplyBefore(
                product.getId(), LocalDateTime.of(2026, 1, 1, 0, 0), PageRequest.of(0, 1));

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstOccurredAt_andFindLastOccurredAt_returnMinAndMaxTimestamps() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime last = LocalDateTime.of(2026, 6, 1, 8, 0);
        entry(product, 5, 15, SupplyChangeSource.KSEF_INVOICE, first);
        entry(product, -2, 13, SupplyChangeSource.USAGE_RECORD, last);

        assertThat(supplyHistoryRepo.findFirstOccurredAt(product.getId())).isEqualTo(first);
        assertThat(supplyHistoryRepo.findLastOccurredAt(product.getId())).isEqualTo(last);
    }

    @Test
    void findFirstOccurredAt_returnsNull_whenNoEntries() {
        assertThat(supplyHistoryRepo.findFirstOccurredAt(product.getId())).isNull();
        assertThat(supplyHistoryRepo.findLastOccurredAt(product.getId())).isNull();
    }

    @Test
    void sumDeltasBySource_groupsAndSumsDeltasPerSource() {
        entry(product, 10, 20, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(product, 5, 25, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        entry(product, -3, 22, SupplyChangeSource.USAGE_RECORD, LocalDateTime.now());

        List<SupplySourceTotalProjection> result = supplyHistoryRepo.sumDeltasBySource(product.getId());
        Map<SupplyChangeSource, Long> totals = result.stream()
                .collect(Collectors.toMap(SupplySourceTotalProjection::getSource, SupplySourceTotalProjection::getTotal));

        assertThat(totals.get(SupplyChangeSource.KSEF_INVOICE)).isEqualTo(15L);
        assertThat(totals.get(SupplyChangeSource.USAGE_RECORD)).isEqualTo(-3L);
    }

    /**
     * The actual Liquibase backfill (db.changelog-2.xml, changeSet id="11") runs against PostgreSQL and is
     * disabled for the H2 test profile (spring.liquibase.enabled=false, see application-test.properties).
     * This test executes the equivalent SQL directly against the H2 schema generated by
     * ddl-auto=create-drop, to verify the backfill keeps SUM(delta) == product.supply for every product,
     * and that products with zero supply are skipped (matching the migration's WHERE supply <> 0).
     */
    @Test
    void migrationEquivalentBackfill_keepsSumOfDeltasEqualToSupply_forEveryProduct() {
        Product zeroSupplyProduct = em.persistAndFlush(Product.builder()
                .name("Pusty").category(category).brand(brand).supply(0).build());

        em.getEntityManager().createNativeQuery("""
                INSERT INTO product_supply_history
                    (product_id, delta, supply_after, source, source_label, occurred_at, performed_by)
                SELECT id, supply, supply, 'INITIAL_BALANCE', 'Stan poczatkowy', CURRENT_TIMESTAMP, 'SYSTEM'
                FROM product WHERE supply <> 0
                """).executeUpdate();
        em.getEntityManager().flush();
        em.clear();

        List<ProductSupplyHistory> productEntries = supplyHistoryRepo
                .findAllByProductId(product.getId(), null, PageRequest.of(0, 10)).getContent();
        assertThat(productEntries).hasSize(1);
        assertThat(productEntries.get(0).getSource()).isEqualTo(SupplyChangeSource.INITIAL_BALANCE);

        long sumOfDeltas = productEntries.stream().mapToLong(ProductSupplyHistory::getDelta).sum();
        Product reloadedProduct = productRepo.findByIdIncludingDeleted(product.getId()).orElseThrow();
        assertThat(sumOfDeltas).isEqualTo(reloadedProduct.getSupply().longValue());

        List<ProductSupplyHistory> zeroSupplyEntries = supplyHistoryRepo
                .findAllByProductId(zeroSupplyProduct.getId(), null, PageRequest.of(0, 10)).getContent();
        assertThat(zeroSupplyEntries).isEmpty();

        entry(reloadedProduct, 5, reloadedProduct.getSupply() + 5, SupplyChangeSource.KSEF_INVOICE, LocalDateTime.now());
        reloadedProduct.setSupply(reloadedProduct.getSupply() + 5);
        em.persistAndFlush(reloadedProduct);

        List<ProductSupplyHistory> afterFollowUpOperation = supplyHistoryRepo
                .findAllByProductId(product.getId(), null, PageRequest.of(0, 10)).getContent();
        long sumAfterFollowUp = afterFollowUpOperation.stream().mapToLong(ProductSupplyHistory::getDelta).sum();

        assertThat(sumAfterFollowUp).isEqualTo(reloadedProduct.getSupply().longValue());
    }
}
