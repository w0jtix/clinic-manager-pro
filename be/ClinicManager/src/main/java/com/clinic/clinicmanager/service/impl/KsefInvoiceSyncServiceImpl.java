package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.KsefInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefMonthMismatchDTO;
import com.clinic.clinicmanager.DTO.KsefSyncStatusDTO;
import com.clinic.clinicmanager.DTO.KsefSyncSummaryDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSyncPeriod;
import com.clinic.clinicmanager.model.KsefSyncStatus;
import com.clinic.clinicmanager.model.constants.KsefSyncStatusType;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.repo.KsefInvoiceItemRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.KsefSyncPeriodRepo;
import com.clinic.clinicmanager.repo.KsefSyncStatusRepo;
import com.clinic.clinicmanager.service.KsefInvoiceSyncService;
import com.clinic.clinicmanager.utils.KsefCorrectionUtils;
import com.clinic.clinicmanager.utils.SecureXml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.invoices.InvoiceQueryFiltersBuilder;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceMetadata;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQueryDateRange;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQueryDateType;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQueryFilters;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceQuerySubjectType;
import pl.akmf.ksef.sdk.client.model.invoice.QueryInvoiceMetadataResponse;
import pl.akmf.ksef.sdk.client.model.util.SortOrder;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class KsefInvoiceSyncServiceImpl implements KsefInvoiceSyncService {

    private static final int PAGE_SIZE = 50;
    private static final long SYNC_STATUS_ID = 1L;
    private static final long QUERY_DELAY_MS = 150L;
    private static final long GET_INVOICE_DELAY_MS = 4000L;
    private static final int VALUE_SCALE = 2;
    private static final int UNIT_PRICE_SCALE = 4;
    private static final int LINE_UUID_MAX_LENGTH = 100;
    private static final OffsetDateTime KSEF_EPOCH =
            OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final KsefAuthService ksefAuthService;
    private final DefaultKsefClient ksefClient;
    private final KsefInvoiceRepo invoiceRepo;
    private final KsefInvoiceItemRepo invoiceItemRepo;
    private final KsefIssuedInvoiceRepo issuedInvoiceRepo;
    private final KsefSellerRepo sellerRepo;
    private final KsefSyncStatusRepo syncStatusRepo;
    private final KsefSyncPeriodRepo syncPeriodRepo;

    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    @Override
    public void syncInvoices() {
        if (!syncRunning.compareAndSet(false, true)) {
            throw new ConflictException("Synchronizacja KSeF już trwa. Poczekaj na jej zakończenie.");
        }
        try {
            runSync();
        } finally {
            syncRunning.set(false);
        }
    }

    private void runSync() {
        KsefSyncStatus syncStatus = syncStatusRepo.findById(SYNC_STATUS_ID)
                .orElseGet(this::createInitialStatus);

        syncStatus.setLastAttemptAt(LocalDateTime.now());

        SyncCounters received = new SyncCounters();

        try {
            String accessToken = ksefAuthService.getAccessToken();

            OffsetDateTime dateFrom = KSEF_EPOCH;
            OffsetDateTime dateTo = OffsetDateTime.now(ZoneOffset.UTC);

            Map<YearMonth, Set<String>> ksefRefsByMonth = new HashMap<>();

            runWindowedQuery(InvoiceQuerySubjectType.SUBJECT2, accessToken, dateFrom, dateTo, received,
                    metadata -> handleReceivedInvoice(metadata, accessToken, ksefRefsByMonth));

            // Backfill: relink standalone corrections because their original invoice was not
            // in the DB when they were first built (original fetched in a separate run, parse-error
            // recovery, or a re-sync of older months). Category inheritance is pushed event-driven
            // by the parent, not here - this only re-establishes the link. Pure DB, no API.
            linkPendingCorrections();

            syncStatus.setLastNewCount(received.newCount);
            syncStatus.setLastFailedCount(received.failedCount);
            syncStatus.setRateLimitHit(received.rateLimitHit);

            if (!received.rateLimitHit && received.failedCount == 0) {
                syncStatus.setLastSyncedAt(LocalDateTime.now());
                syncStatus.setStatus(KsefSyncStatusType.SUCCESS);
            } else {
                syncStatus.setStatus(KsefSyncStatusType.PARTIAL_FAILURE);
            }

            // Skip issued invoices when the received pass already exhausted the hourly quota - the
            // SUBJECT1 query would just hit 429 immediately. The next scheduled sync picks them up.
            SyncCounters issued = received.rateLimitHit
                    ? new SyncCounters()
                    : syncIssuedInvoices(accessToken, dateFrom, dateTo);
            syncStatus.setLastIssuedNewCount(issued.newCount);
            syncStatus.setLastIssuedFailedCount(issued.failedCount);

            syncStatusRepo.save(syncStatus);
            if (!received.rateLimitHit) {
                updateSyncPeriods(dateFrom.toLocalDate(), dateTo.toLocalDate(), ksefRefsByMonth);
            }
            log.info("KSeF sync complete - received: {} new, {} failed; issued: {} new, {} failed",
                    received.newCount, received.failedCount, issued.newCount, issued.failedCount);

        } catch (Exception e) {
            syncStatus.setLastNewCount(received.newCount);
            syncStatus.setLastFailedCount(received.failedCount);
            syncStatus.setStatus(KsefSyncStatusType.PARTIAL_FAILURE);
            syncStatusRepo.save(syncStatus);
            throw new RuntimeException("KSeF invoice sync failed", e);
        }
    }

    /**
     * Issued invoices are a read-only preview feature - any failure here must leave the received
     * invoice pipeline (already finished at this point) and the job itself untouched.
     */
    private SyncCounters syncIssuedInvoices(String accessToken, OffsetDateTime dateFrom, OffsetDateTime dateTo) {
        SyncCounters counters = new SyncCounters();
        try {
            runWindowedQuery(InvoiceQuerySubjectType.SUBJECT1, accessToken, dateFrom, dateTo, counters,
                    metadata -> handleIssuedInvoice(metadata, accessToken));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("KSeF issued invoice sync interrupted - {} saved so far", counters.newCount);
        } catch (Exception e) {
            log.error("KSeF issued invoice sync failed - received invoices are unaffected", e);
        }
        return counters;
    }

    // Silent SUBJECT1-only sync for one month: best-effort, never touches KsefSyncStatus, never
    // claims the syncRunning guard, swallows every failure. Numbering correctness is enforced by
    // the create-time validation, not here.
    @Async("ksefTaskExecutor")
    @Override
    public void syncIssuedInvoicesForMonth(YearMonth month) {
        if (syncRunning.get()) {
            log.debug("Skipping narrow issued-invoice sync for {} - full sync in progress", month);
            return;
        }
        try {
            String accessToken = ksefAuthService.getAccessToken();
            OffsetDateTime dateFrom = OffsetDateTime.of(month.atDay(1), LocalTime.MIN, ZoneOffset.UTC);
            OffsetDateTime dateTo = OffsetDateTime.of(month.atEndOfMonth(), LocalTime.of(23, 59, 59), ZoneOffset.UTC);
            SyncCounters counters = syncIssuedInvoices(accessToken, dateFrom, dateTo);
            log.info("Narrow issued-invoice sync for {} done - {} new, {} failed",
                    month, counters.newCount, counters.failedCount);
        } catch (Exception e) {
            log.warn("Narrow issued-invoice sync for {} failed - ignored", month, e);
        }
    }

    private boolean handleReceivedInvoice(InvoiceMetadata metadata, String accessToken,
                                          Map<YearMonth, Set<String>> ksefRefsByMonth) throws Exception {
        String ksefRef = metadata.getKsefNumber();
        LocalDate issueDate = metadata.getIssueDate();
        if (ksefRef != null && issueDate != null) {
            ksefRefsByMonth.computeIfAbsent(YearMonth.from(issueDate), k -> new HashSet<>())
                    .add(ksefRef);
        }
        if (invoiceRepo.existsByKsefReferenceNumber(ksefRef)) return false;

        Thread.sleep(GET_INVOICE_DELAY_MS);
        byte[] xmlBytes = ksefClient.getInvoice(ksefRef, accessToken);
        KsefInvoice invoice = buildInvoice(ksefRef, xmlBytes);
        invoiceRepo.save(invoice);
        return true;
    }

    private boolean handleIssuedInvoice(InvoiceMetadata metadata, String accessToken) throws Exception {
        String ksefRef = metadata.getKsefNumber();
        if (ksefRef == null || issuedInvoiceRepo.existsByKsefReferenceNumber(ksefRef)) return false;

        Thread.sleep(GET_INVOICE_DELAY_MS);
        byte[] xmlBytes = ksefClient.getInvoice(ksefRef, accessToken);
        issuedInvoiceRepo.save(buildIssuedInvoice(ksefRef, xmlBytes));
        return true;
    }

    /**
     * Shared KSeF metadata pagination: 3-month windows with a cursor over permanentStorageDate and
     * a fixed offset of 0. Both invoice directions go through this single implementation - the
     * direction-specific work happens in the handler.
     */
    private void runWindowedQuery(InvoiceQuerySubjectType subjectType,
                                  String accessToken,
                                  OffsetDateTime dateFrom,
                                  OffsetDateTime dateTo,
                                  SyncCounters counters,
                                  InvoiceHandler handler) throws Exception {
        OffsetDateTime windowStart = dateFrom;
        outer:
        while (windowStart.isBefore(dateTo)) {
            OffsetDateTime windowEnd = windowStart.plusMonths(3);
            if (windowEnd.isAfter(dateTo)) windowEnd = dateTo;

            OffsetDateTime cursor = windowStart;
            while (true) {
                InvoiceQueryFilters filters = new InvoiceQueryFiltersBuilder()
                        .withSubjectType(subjectType)
                        .withDateRange(new InvoiceQueryDateRange(
                                InvoiceQueryDateType.PERMANENTSTORAGE,
                                cursor,
                                windowEnd,
                                true))
                        .build();

                QueryInvoiceMetadataResponse response = ksefClient.queryInvoiceMetadata(
                        0, PAGE_SIZE, SortOrder.ASC, filters, accessToken);
                Thread.sleep(QUERY_DELAY_MS);

                List<InvoiceMetadata> invoices = response.getInvoices();
                if (invoices == null || invoices.isEmpty()) break;

                OffsetDateTime lastPermanentStorageDate = null;
                for (InvoiceMetadata metadata : invoices) {
                    OffsetDateTime storedAt = metadata.getPermanentStorageDate();
                    if (storedAt != null && (lastPermanentStorageDate == null
                            || storedAt.isAfter(lastPermanentStorageDate))) {
                        lastPermanentStorageDate = storedAt;
                    }

                    try {
                        if (handler.handle(metadata)) counters.newCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception e) {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("429")) {
                            log.warn("KSeF hourly rate limit reached for {} - stopping sync. Saved: {}, pending in next run.",
                                    subjectType, counters.newCount);
                            counters.rateLimitHit = true;
                            break outer;
                        }
                        log.warn("Failed to fetch/parse invoice {} ({}): {}",
                                metadata.getKsefNumber(), subjectType, msg);
                        counters.failedCount++;
                    }
                }

                if (!Boolean.TRUE.equals(response.getHasMore())) break;
                if (lastPermanentStorageDate == null || !lastPermanentStorageDate.isAfter(cursor)) {
                    log.warn("KSeF window=[{} .. {}] hasMore but cursor cannot advance (cursor={}, lastPsd={}) - stopping window",
                            windowStart, windowEnd, cursor, lastPermanentStorageDate);
                    break;
                }
                cursor = lastPermanentStorageDate;
            }

            windowStart = windowEnd;
        }
    }

    @FunctionalInterface
    private interface InvoiceHandler {
        boolean handle(InvoiceMetadata metadata) throws Exception;
    }

    private static class SyncCounters {
        private int newCount;
        private int failedCount;
        private boolean rateLimitHit;
    }

    @Override
    public KsefSyncStatusDTO getSyncStatus() {
        KsefSyncStatus status = syncStatusRepo.findById(SYNC_STATUS_ID)
                .orElseGet(this::createInitialStatus);
        KsefSyncStatusDTO dto = new KsefSyncStatusDTO(status);
        dto.setSummary(buildSummary());
        return dto;
    }

    private KsefSyncSummaryDTO buildSummary() {
        YearMonth cursor = YearMonth.from(KSEF_EPOCH.toLocalDate());
        YearMonth end = YearMonth.from(LocalDate.now());

        long totalLocal = 0L;
        Long totalKsef = null;
        boolean anySyncedMonth = false;
        boolean anyMismatchOrUnsynced = false;
        List<KsefMonthMismatchDTO> mismatches = new ArrayList<>();

        while (!cursor.isAfter(end)) {
            LocalDate monthStart = cursor.atDay(1);
            LocalDate monthEnd = cursor.atEndOfMonth();
            long localCount = invoiceRepo.countByIssueDateBetween(monthStart, monthEnd);
            totalLocal += localCount;

            Long ksefCount = syncPeriodRepo.findByYearAndMonth(cursor.getYear(), cursor.getMonthValue())
                    .map(KsefSyncPeriod::getKsefCount)
                    .orElse(null);

            if (ksefCount == null) {
                anyMismatchOrUnsynced = true;
                if (localCount > 0) {
                    mismatches.add(new KsefMonthMismatchDTO(
                            cursor.getYear(), cursor.getMonthValue(), localCount, null, 0L));
                }
            } else {
                if (!anySyncedMonth) {
                    anySyncedMonth = true;
                    totalKsef = 0L;
                }
                totalKsef += ksefCount;
                if (localCount != ksefCount) {
                    anyMismatchOrUnsynced = true;
                    long missing = Math.max(0L, ksefCount - localCount);
                    mismatches.add(new KsefMonthMismatchDTO(
                            cursor.getYear(), cursor.getMonthValue(), localCount, ksefCount, missing));
                }
            }

            cursor = cursor.plusMonths(1);
        }

        boolean fullySynced = anySyncedMonth && !anyMismatchOrUnsynced;
        return new KsefSyncSummaryDTO(fullySynced, totalLocal, totalKsef, mismatches);
    }

    @Override
    public KsefInvoiceCountDTO getInvoiceCount(KsefInvoiceFilterDTO filter) {
        int year = filter.getYear() != null ? filter.getYear() : LocalDate.now().getYear();
        int month = filter.getMonth() != null ? filter.getMonth() : LocalDate.now().getMonthValue();
        LocalDate dateFrom = LocalDate.of(year, month, 1);
        LocalDate dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());

        long localCount = invoiceRepo.countByIssueDateBetween(dateFrom, dateTo);
        Long ksefCount = syncPeriodRepo.findByYearAndMonth(year, month)
                .map(KsefSyncPeriod::getKsefCount)
                .orElse(null);

        return new KsefInvoiceCountDTO(localCount, ksefCount);
    }

    private void updateSyncPeriods(LocalDate syncFrom, LocalDate syncTo,
                                   Map<YearMonth, Set<String>> ksefRefsByMonth) {
        LocalDateTime now = LocalDateTime.now();
        YearMonth cursor = YearMonth.from(syncFrom);
        YearMonth end = YearMonth.from(syncTo);
        while (!cursor.isAfter(end)) {
            Set<String> refs = ksefRefsByMonth.get(cursor);
            long count = refs == null ? 0L : refs.size();
            YearMonth ym = cursor;
            KsefSyncPeriod period = syncPeriodRepo.findByYearAndMonth(ym.getYear(), ym.getMonthValue())
                    .orElse(KsefSyncPeriod.builder().year(ym.getYear()).month(ym.getMonthValue()).build());
            period.setKsefCount(count);
            period.setSyncedAt(now);
            syncPeriodRepo.save(period);
            cursor = cursor.plusMonths(1);
        }
    }

    private KsefSyncStatus createInitialStatus() {
        return syncStatusRepo.save(KsefSyncStatus.builder()
                .id(SYNC_STATUS_ID)
                .status(KsefSyncStatusType.NEVER_SYNCED)
                .build());
    }

    @Override
    public void backfillItemLines() {
        List<Long> invoiceIds = invoiceItemRepo.findInvoiceIdsWithMissingLineNumber();
        if (invoiceIds.isEmpty()) return;

        int updated = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                updated += backfillInvoiceItemLines(invoiceId);
            } catch (Exception e) {
                log.warn("KSeF item line backfill skipped for invoice {}: {}", invoiceId, e.getMessage());
            }
        }
        log.info("KSeF item line backfill done - {} invoices scanned, {} items updated", invoiceIds.size(), updated);
    }

    private int backfillInvoiceItemLines(Long invoiceId) throws Exception {
        String rawXml = invoiceRepo.findById(invoiceId).map(KsefInvoice::getRawXml).orElse(null);
        if (rawXml == null || rawXml.isBlank()) {
            log.warn("KSeF item line backfill skipped for invoice {}: no raw XML", invoiceId);
            return 0;
        }

        Element fa = findFirst(parseDocument(rawXml.getBytes(StandardCharsets.UTF_8)).getDocumentElement(), "Fa");
        List<KsefInvoiceItem> parsed = buildItems(fa, isCorrection(textIn(fa, "RodzajFaktury")));
        List<KsefInvoiceItem> stored = invoiceItemRepo.findByInvoiceIdOrderByIdAsc(invoiceId);
        if (parsed.size() != stored.size()) {
            log.warn("KSeF item line backfill skipped for invoice {}: {} stored items vs {} parsed rows",
                    invoiceId, stored.size(), parsed.size());
            return 0;
        }

        int updated = 0;
        int mismatched = 0;
        for (int i = 0; i < stored.size(); i++) {
            KsefInvoiceItem storedItem = stored.get(i);
            KsefInvoiceItem parsedItem = parsed.get(i);
            if (storedItem.getLineNumber() != null) continue;
            if (!sameParsedLine(storedItem, parsedItem)) {
                mismatched++;
                continue;
            }
            if (parsedItem.getLineNumber() == null && parsedItem.getLineUuid() == null) continue;
            updated += invoiceItemRepo.updateLineIfMissing(
                    storedItem.getId(), parsedItem.getLineNumber(), parsedItem.getLineUuid());
        }
        if (mismatched > 0) {
            log.warn("KSeF item line backfill for invoice {}: {} items do not match their XML row - left empty",
                    invoiceId, mismatched);
        }
        return updated;
    }

    private boolean sameParsedLine(KsefInvoiceItem stored, KsefInvoiceItem parsed) {
        if (!Objects.equals(stored.getKsefItemName(), parsed.getKsefItemName())) return false;
        BigDecimal storedQuantity = stored.getQuantity();
        BigDecimal parsedQuantity = parsed.getQuantity();
        if (storedQuantity == null || parsedQuantity == null) {
            return storedQuantity == null && parsedQuantity == null;
        }
        return storedQuantity.compareTo(parsedQuantity) == 0;
    }

    private Document parseDocument(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = SecureXml.newDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlBytes));
        doc.getDocumentElement().normalize();
        return doc;
    }

    private KsefInvoice buildInvoice(String ksefRef, byte[] xmlBytes) throws Exception {
        String rawXml = new String(xmlBytes, StandardCharsets.UTF_8);
        Document doc = parseDocument(xmlBytes);

        Element podmiot1 = findFirst(doc.getDocumentElement(), "Podmiot1");
        Element podmiot2 = findFirst(doc.getDocumentElement(), "Podmiot2");
        Element fa = findFirst(doc.getDocumentElement(), "Fa");

        String invoiceType = textIn(fa, "RodzajFaktury");
        boolean correction = isCorrection(invoiceType);

        InvoiceAmounts amounts = correction ? correctionAmounts(fa) : regularAmounts(fa);

        KsefSeller seller = findOrCreateSeller(podmiot1);

        boolean autoMarkProducts = !correction && Boolean.TRUE.equals(seller.getAutoMarkProducts());

        String category = (!correction && !autoMarkProducts && seller.getDefaultExpenseCategory() != null)
                ? seller.getDefaultExpenseCategory()
                : null;

        KsefInvoice invoice = KsefInvoice.builder()
                .ksefReferenceNumber(ksefRef)
                .invoiceNumber(textIn(fa, "P_2"))
                .invoiceType(invoiceType)
                .issueDate(parseDate(textIn(fa, "P_1")))
                .paymentDueDate(parsePaymentDueDate(fa))
                .seller(seller)
                .buyerNip(textInDaneIdentyfikacyjne(podmiot2, "NIP"))
                .buyerName(textInDaneIdentyfikacyjne(podmiot2, "Nazwa"))
                .buyerAddress(buildAddress(podmiot2))
                .netAmount(amounts.net())
                .vatAmount(amounts.vat())
                .grossAmount(amounts.gross())
                .currency(defaultIfNull(textIn(fa, "KodWaluty"), "PLN"))
                .isPaid(parseIsPaid(fa))
                .awaitingProductVerification(autoMarkProducts)
                .category(category)
                .correctedKsefNumber(correction ? parseCorrectedKsefNumber(fa) : null)
                .paymentDate(parsePaymentDate(fa))
                .paymentMethod(parsePaymentMethod(fa))
                .rawXml(rawXml)
                .fetchedAt(LocalDateTime.now())
                .build();

        buildItems(fa, correction).forEach(invoice::addItem);

        if (correction) {
            linkCorrection(invoice);
        }
        return invoice;
    }

    /**
     * Parses an issued invoice XML. Mirrors {@link #buildInvoice} but Podmiot1 is us (the seller):
     * its data is read straight from the XML into plain seller columns so the invoice reflects the
     * state at the moment it was issued, even if the company address later changes in settings. We
     * still never call findOrCreateSeller and nothing touches ksef_seller or ksef_invoice - only the
     * data is copied, no supplier row is created. Podmiot2 is the buyer (a clinic client) and is
     * stored as plain columns. Amounts, positions and corrections reuse the exact same parsing rules
     * as received invoices.
     */
    private KsefIssuedInvoice buildIssuedInvoice(String ksefRef, byte[] xmlBytes) throws Exception {
        String rawXml = new String(xmlBytes, StandardCharsets.UTF_8);
        Document doc = parseDocument(xmlBytes);

        Element podmiot1 = findFirst(doc.getDocumentElement(), "Podmiot1");
        Element podmiot2 = findFirst(doc.getDocumentElement(), "Podmiot2");
        Element fa = findFirst(doc.getDocumentElement(), "Fa");

        String invoiceType = textIn(fa, "RodzajFaktury");
        boolean correction = isCorrection(invoiceType);
        InvoiceAmounts amounts = correction ? correctionAmounts(fa) : regularAmounts(fa);

        KsefIssuedInvoice invoice = KsefIssuedInvoice.builder()
                .ksefReferenceNumber(ksefRef)
                .invoiceNumber(textIn(fa, "P_2"))
                .invoiceType(invoiceType)
                .issueDate(parseDate(textIn(fa, "P_1")))
                .paymentDueDate(parsePaymentDueDate(fa))
                .sellerName(textInDaneIdentyfikacyjne(podmiot1, "Nazwa"))
                .sellerNip(textInDaneIdentyfikacyjne(podmiot1, "NIP"))
                .sellerAddress(buildAddress(podmiot1))
                .buyerNip(textInDaneIdentyfikacyjne(podmiot2, "NIP"))
                .buyerName(textInDaneIdentyfikacyjne(podmiot2, "Nazwa"))
                .buyerAddress(buildAddress(podmiot2))
                .netAmount(amounts.net())
                .vatAmount(amounts.vat())
                .grossAmount(amounts.gross())
                .currency(defaultIfNull(textIn(fa, "KodWaluty"), "PLN"))
                .correctedKsefNumber(correction ? parseCorrectedKsefNumber(fa) : null)
                .rawXml(rawXml)
                .fetchedAt(LocalDateTime.now())
                .build();

        for (KsefInvoiceItem src : buildItems(fa, correction)) {
            invoice.addItem(KsefIssuedInvoiceItem.builder()
                    .ksefItemName(src.getKsefItemName())
                    .unit(src.getUnit())
                    .quantity(src.getQuantity())
                    .unitNetPrice(src.getUnitNetPrice())
                    .netValue(src.getNetValue())
                    .unitGrossPrice(src.getUnitGrossPrice())
                    .grossValue(src.getGrossValue())
                    .vatRate(src.getVatRate())
                    .build());
        }
        return invoice;
    }

    private record InvoiceAmounts(BigDecimal net, BigDecimal vat, BigDecimal gross) {
    }

    private InvoiceAmounts regularAmounts(Element fa) {
        BigDecimal gross = decimal(textIn(fa, "P_15"));
        BigDecimal net = sumNetFromLines(fa);
        BigDecimal vat = (gross != null && net != null) ? gross.subtract(net) : null;
        return new InvoiceAmounts(net, vat, gross);
    }

    private InvoiceAmounts correctionAmounts(Element fa) {
        // Correction: header amounts (P_13_* net, P_14_* VAT, P_15 gross) are already signed
        // (negative). Summing StanPrzed+StanPo positions would give a wrong result, so we take
        // them straight from the header.
        BigDecimal gross = decimal(textIn(fa, "P_15"));
        BigDecimal net = signedSumByPrefix(fa, "P_13_");
        BigDecimal vat = signedSumByPrefix(fa, "P_14_");
        if (net == null && gross != null && vat != null) {
            net = gross.subtract(vat);
        }
        if (vat == null && gross != null && net != null) {
            vat = gross.subtract(net);
        }
        return new InvoiceAmounts(net, vat, gross);
    }

    private boolean isCorrection(String invoiceType) {
        return invoiceType != null && invoiceType.startsWith("KOR");
    }

    private String parseCorrectedKsefNumber(Element fa) {
        Element daneKorygowanej = findFirst(fa, "DaneFaKorygowanej");
        return textIn(daneKorygowanej, "NrKSeFFaKorygowanej");
    }

    /**
     * Sums (with sign) all invoice header elements whose local name has the form
     * {@code prefix + <digits>} (e.g. P_13_1, P_14_2). Nested breakdowns like P_13_6_1
     * (which contain an extra '_') are skipped so amounts are not double-counted.
     */
    private BigDecimal signedSumByPrefix(Element fa, String prefix) {
        if (fa == null) return null;
        NodeList all = fa.getElementsByTagNameNS("*", "*");
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            String local = el.getLocalName();
            if (local == null || !local.startsWith(prefix)) continue;
            String suffix = local.substring(prefix.length());
            if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) continue;
            BigDecimal value = decimal(el.getTextContent());
            if (value != null) {
                sum = sum.add(value);
                any = true;
            }
        }
        return any ? sum : null;
    }

    /**
     * Links a correction to its original invoice by KSeF number. Returns true when the parent was
     * found. The link is set as soon as the parent exists - even if the parent has no category yet;
     * in that case the correction simply waits, and category inheritance is pushed later by the
     * parent's own category events (setCategory / verifyProducts).
     */
    private boolean linkCorrection(KsefInvoice correction) {
        String ref = correction.getCorrectedKsefNumber();
        if (ref == null) return false;

        KsefInvoice original = invoiceRepo.findByKsefReferenceNumberWithItems(ref).orElse(null);
        if (original == null) return false;

        correction.setCorrectedInvoiceId(original.getId());
        KsefCorrectionUtils.applyParentCategory(correction, original);
        return true;
    }

    private void linkPendingCorrections() {
        List<KsefInvoice> pending = invoiceRepo.findPendingCorrectionsWithItems();
        for (KsefInvoice correction : pending) {
            if (linkCorrection(correction)) {
                invoiceRepo.save(correction);
            }
        }
    }

    private List<KsefInvoiceItem> buildItems(Element fa, boolean correction) {
        if (fa == null) return List.of();
        NodeList rows = fa.getElementsByTagNameNS("*", "FaWiersz");
        if (correction) {
            return buildCorrectionItems(rows);
        }
        List<KsefInvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < rows.getLength(); i++) {
            KsefInvoiceItem item = buildItem((Element) rows.item(i));
            if (item != null) items.add(item);
        }
        return items;
    }

    private KsefInvoiceItem buildItem(Element row) {
        String ksefItemName = textIn(row, "P_7");
        if (ksefItemName == null) return null;

        String vatRateText = textIn(row, "P_12");
        BigDecimal unitGrossPrice = decimal(textIn(row, "P_9B"));
        BigDecimal unitNetPrice = decimal(textIn(row, "P_9A"));
        if (unitNetPrice == null) {
            unitNetPrice = netFromGross(unitGrossPrice, vatRateText);
        }
        if (unitGrossPrice == null) {
            unitGrossPrice = grossFromNet(unitNetPrice, vatRateText, UNIT_PRICE_SCALE);
        }

        return KsefInvoiceItem.builder()
                .ksefItemName(ksefItemName)
                .ksefGtin(textIn(row, "GTIN"))
                .lineNumber(parseLineNumber(textIn(row, "NrWierszaFa")))
                .lineUuid(parseLineUuid(textIn(row, "UU_ID")))
                .unit(textIn(row, "P_8A"))
                .quantity(decimal(textIn(row, "P_8B")))
                .unitNetPrice(unitNetPrice)
                .netValue(lineNetValue(row))
                .unitGrossPrice(unitGrossPrice)
                .grossValue(lineGrossValue(row))
                .vatRate(parseVatRate(vatRateText))
                .build();
    }

    /**
     * Correction: each corrected position appears twice with the same NrWierszaFa - a row marked
     * StanPrzed=1 (state before) and a row without it (state after). We collapse the pair into a
     * single position holding the delta (StanPo - StanPrzed); quantity and net value are usually
     * negative.
     */
    private List<KsefInvoiceItem> buildCorrectionItems(NodeList rows) {
        Map<String, Element> before = new HashMap<>();
        Map<String, Element> after = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            String nr = textIn(row, "NrWierszaFa");
            String key = nr != null ? nr : "row-" + i;
            if (!order.contains(key)) order.add(key);
            if ("1".equals(textIn(row, "StanPrzed"))) {
                before.put(key, row);
            } else {
                after.put(key, row);
            }
        }

        List<KsefInvoiceItem> items = new ArrayList<>();
        for (String key : order) {
            KsefInvoiceItem item = buildCorrectionItem(before.get(key), after.get(key));
            if (item != null) items.add(item);
        }
        return items;
    }

    private KsefInvoiceItem buildCorrectionItem(Element before, Element after) {
        Element descriptor = after != null ? after : before;
        String ksefItemName = textIn(descriptor, "P_7");
        if (ksefItemName == null) return null;

        String vatRateText = textIn(descriptor, "P_12");
        BigDecimal unitGrossPrice = decimal(textIn(descriptor, "P_9B"));
        BigDecimal unitNetPrice = decimal(textIn(descriptor, "P_9A"));
        if (unitNetPrice == null) {
            unitNetPrice = netFromGross(unitGrossPrice, vatRateText);
        }
        if (unitGrossPrice == null) {
            unitGrossPrice = grossFromNet(unitNetPrice, vatRateText, UNIT_PRICE_SCALE);
        }

        String lineUuid = textIn(after, "UU_ID");
        if (lineUuid == null) {
            lineUuid = textIn(before, "UU_ID");
        }

        return KsefInvoiceItem.builder()
                .ksefItemName(ksefItemName)
                .ksefGtin(textIn(descriptor, "GTIN"))
                .lineNumber(parseLineNumber(textIn(descriptor, "NrWierszaFa")))
                .lineUuid(parseLineUuid(lineUuid))
                .unit(textIn(descriptor, "P_8A"))
                .quantity(subtractNullable(decimal(textIn(after, "P_8B")), decimal(textIn(before, "P_8B"))))
                .unitNetPrice(unitNetPrice)
                .netValue(subtractNullable(lineNetValue(after), lineNetValue(before)))
                .unitGrossPrice(unitGrossPrice)
                .grossValue(subtractNullable(lineGrossValue(after), lineGrossValue(before)))
                .vatRate(parseVatRate(vatRateText))
                .build();
    }

    private Integer parseLineNumber(String value) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseLineUuid(String value) {
        return value != null && value.length() <= LINE_UUID_MAX_LENGTH ? value : null;
    }

    private BigDecimal subtractNullable(BigDecimal after, BigDecimal before) {
        if (after == null && before == null) return null;
        BigDecimal a = after != null ? after : BigDecimal.ZERO;
        BigDecimal b = before != null ? before : BigDecimal.ZERO;
        return a.subtract(b);
    }

    private VatRate parseVatRate(String value) {
        if (value == null) return null;
        try {
            return VatRate.fromValue(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
        }
        try {
            return VatRate.fromValue(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private KsefSeller findOrCreateSeller(Element podmiot1) {
        String nip = textInDaneIdentyfikacyjne(podmiot1, "NIP");
        String name = textInDaneIdentyfikacyjne(podmiot1, "Nazwa");
        String address = buildAddress(podmiot1);
        Element kontakt = findFirst(podmiot1, "DaneKontaktowe");
        String email = textIn(kontakt, "Email");
        String phone = textIn(kontakt, "Telefon");

        KsefSeller seller = sellerRepo.findByNip(nip)
                .orElseGet(() -> KsefSeller.builder().nip(nip).build());
        seller.setName(name);
        seller.setAddress(address);
        seller.setEmail(email);
        seller.setPhone(phone);
        return sellerRepo.save(seller);
    }

    private Element findFirst(Element root, String tag) {
        if (root == null) return null;
        NodeList nodes = root.getElementsByTagNameNS("*", tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private String textIn(Element parent, String tag) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagNameNS("*", tag);
        if (nodes.getLength() == 0) return null;
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String textInDaneIdentyfikacyjne(Element podmiot, String tag) {
        Element dane = findFirst(podmiot, "DaneIdentyfikacyjne");
        return textIn(dane, tag);
    }

    private String buildAddress(Element podmiot) {
        Element adres = findFirst(podmiot, "Adres");
        if (adres == null) return null;
        String l1 = textIn(adres, "AdresL1");
        String l2 = textIn(adres, "AdresL2");
        if (l1 == null && l2 == null) return null;
        if (l1 == null) return l2;
        if (l2 == null) return l1;
        return l1 + " " + l2;
    }

    private LocalDate parsePaymentDueDate(Element fa) {
        Element platnosc = findFirst(fa, "Platnosc");
        Element terminPlatnosci = findFirst(platnosc, "TerminPlatnosci");
        return parseDate(textIn(terminPlatnosci, "Termin"));
    }

    private BigDecimal sumNetFromLines(Element fa) {
        if (fa == null) return null;
        NodeList wiersze = fa.getElementsByTagNameNS("*", "FaWiersz");
        if (wiersze.getLength() == 0) return null;
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (int i = 0; i < wiersze.getLength(); i++) {
            Element wiersz = (Element) wiersze.item(i);
            BigDecimal net = lineNetValue(wiersz);
            if (net != null) {
                sum = sum.add(net);
                any = true;
            }
        }
        return any ? sum : null;
    }

    private BigDecimal lineNetValue(Element wiersz) {
        BigDecimal p11 = decimal(textIn(wiersz, "P_11"));
        if (p11 != null) return p11;

        BigDecimal p11A = decimal(textIn(wiersz, "P_11A"));
        if (p11A == null) return null;

        BigDecimal p11Vat = decimal(textIn(wiersz, "P_11Vat"));
        if (p11Vat != null) return p11A.subtract(p11Vat);

        return netFromGross(p11A, textIn(wiersz, "P_12"));
    }

    private BigDecimal lineGrossValue(Element wiersz) {
        BigDecimal p11A = decimal(textIn(wiersz, "P_11A"));
        if (p11A != null) return p11A;

        return grossFromNet(lineNetValue(wiersz), textIn(wiersz, "P_12"), VALUE_SCALE);
    }

    static BigDecimal netFromGross(BigDecimal gross, String vatRate) {
        if (gross == null) return null;
        if (vatRate == null) return gross;
        BigDecimal rate;
        try {
            rate = new BigDecimal(vatRate.trim());
        } catch (NumberFormatException e) {
            return gross;
        }
        if (rate.signum() == 0) return gross;
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(new BigDecimal("100")));
        return gross.divide(divisor, VALUE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal grossFromNet(BigDecimal net, String vatRate, int scale) {
        if (net == null) return null;
        BigDecimal rate = null;
        if (vatRate != null) {
            try {
                rate = new BigDecimal(vatRate.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (rate == null || rate.signum() == 0) {
            return net.setScale(scale, RoundingMode.HALF_UP);
        }
        BigDecimal multiplier = BigDecimal.ONE.add(rate.movePointLeft(2));
        return net.multiply(multiplier).setScale(scale, RoundingMode.HALF_UP);
    }

    private Boolean parseIsPaid(Element fa) {
        Element platnosc = findFirst(fa, "Platnosc");
        if ("1".equals(textIn(platnosc, "Zaplacono"))) return true;

        Element rozliczenie = findFirst(fa, "Rozliczenie");
        BigDecimal doZaplaty = decimal(textIn(rozliczenie, "DoZaplaty"));
        if (doZaplaty != null && doZaplaty.compareTo(BigDecimal.ZERO) == 0) return true;

        return false;
    }

    private LocalDate parsePaymentDate(Element fa) {
        Element platnosc = findFirst(fa, "Platnosc");
        return parseDate(textIn(platnosc, "DataZaplaty"));
    }

    private String parsePaymentMethod(Element fa) {
        Element platnosc = findFirst(fa, "Platnosc");
        if (platnosc == null) return null;

        if ("1".equals(textIn(platnosc, "PlatnoscInna"))) {
            String opis = textIn(platnosc, "OpisPlatnosci");
            if (opis != null) return opis;
        }

        String forma = textIn(platnosc, "FormaPlatnosci");
        return mapFormaPlatnosci(forma);
    }

    private String mapFormaPlatnosci(String code) {
        if (code == null) return null;
        return switch (code) {
            case "1" -> "Gotówka";
            case "2" -> "Karta";
            case "3" -> "Bon";
            case "4" -> "Czek";
            case "5" -> "Kredyt";
            case "6" -> "Przelew";
            case "7" -> "Mobilna";
            default -> null;
        };
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimal(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultIfNull(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
