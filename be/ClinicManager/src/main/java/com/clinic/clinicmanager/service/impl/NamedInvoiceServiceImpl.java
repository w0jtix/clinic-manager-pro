package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceDraftDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceItemDTO;
import com.clinic.clinicmanager.DTO.request.NamedInvoiceCreateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.NamedInvoiceItem;
import com.clinic.clinicmanager.model.SaleItem;
import com.clinic.clinicmanager.model.Payment;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.VisitItem;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.repo.NamedInvoiceRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.KsefInvoiceSyncService;
import com.clinic.clinicmanager.service.NamedInvoiceService;
import com.clinic.clinicmanager.utils.MoneyUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class NamedInvoiceServiceImpl implements NamedInvoiceService {

    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");
    private static final VatRate SERVICE_VAT = VatRate.VAT_8;

    private final NamedInvoiceRepo namedInvoiceRepo;
    private final KsefIssuedInvoiceRepo issuedInvoiceRepo;
    private final ClientRepo clientRepo;
    private final VisitRepo visitRepo;
    private final KsefInvoiceSyncService ksefInvoiceSyncService;

    @Override
    @Transactional(readOnly = true)
    public NamedInvoiceDraftDTO buildDraft(Long visitId) {
        Visit visit = visitRepo.findOneById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found with given id: " + visitId));

        YearMonth ym = YearMonth.from(visit.getDate());
        ksefInvoiceSyncService.syncIssuedInvoicesForMonth(ym);

        List<NamedInvoiceItemDTO> items = new ArrayList<>();
        for (VisitItem visitItem : visit.getItems()) {
            items.add(serviceLine(visitItem));
        }
        if (visit.getSale() != null) {
            for (SaleItem saleItem : visit.getSale().getItems()) {
                if (saleItem.getProduct() != null) {
                    items.add(productLine(saleItem));
                } else if (saleItem.getVoucher() != null) {
                    items.add(voucherLine(saleItem));
                }
            }
        }

        int next = maxSequenceForMonth(ym) + 1;
        Client client = visit.getClient();

        NamedInvoiceDraftDTO draft = new NamedInvoiceDraftDTO();
        draft.setSuggestedInvoiceNumber(next + "/" + String.format("%02d", ym.getMonthValue()) + "/" + ym.getYear());
        draft.setSuggestedSequenceInMonth(next);
        draft.setYear(ym.getYear());
        draft.setMonth(ym.getMonthValue());
        draft.setIssueDate(LocalDate.now());
        draft.setSaleDate(visit.getDate() != null ? visit.getDate() : LocalDate.now());
        draft.setPaymentMethods(visitPaymentMethods(visit));
        draft.setNotes(null);
        draft.setClientId(client.getId());
        draft.setSourceVisitId(visit.getId());
        draft.setBuyerName(client.getFirstName() + " " + client.getLastName());
        draft.setBuyerAddress(orEmpty(client.getAddress()));
        draft.setBuyerPostalCode(orEmpty(client.getPostalCode()));
        draft.setBuyerCity(orEmpty(client.getCity()));
        draft.setClientEmail(orEmpty(client.getEmail()));
        draft.setItems(items);
        return draft;
    }

    @Override
    @Transactional
    public NamedInvoiceDTO create(NamedInvoiceCreateDTO request) {
        if (request.getSourceVisitId() != null) {
            namedInvoiceRepo.findFirstBySourceVisitIdOrderByIdDesc(request.getSourceVisitId())
                    .ifPresent(existing -> {
                        throw new ConflictException("Dla tej wizyty wystawiono już fakturę imienną nr "
                                + existing.getInvoiceNumber() + ".");
                    });
        }

        YearMonth ym = YearMonth.of(request.getYear(), request.getMonth());
        int max = maxSequenceForMonth(ym);
        if (request.getSequenceInMonth() <= max) {
            throw new ConflictException("Numer faktury jest nieaktualny - w tym miesiącu istnieje już faktura "
                    + "z numerem " + max + " lub wyższym. Odśwież numer i spróbuj ponownie.");
        }

        Client client = clientRepo.findOneById(request.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + request.getClientId()));

        NamedInvoice invoice = new NamedInvoice();
        applyRequest(invoice, request);
        invoice.setClient(client);
        invoice.setCreatedByUserId(SessionUtils.getUserIdFromSession());
        replaceItems(invoice, request.getItems());
        recalculateTotals(invoice);

        NamedInvoice saved = namedInvoiceRepo.save(invoice);
        learnClientAddress(client, request);
        return new NamedInvoiceDTO(saved, true);
    }

    @Override
    @Transactional
    public NamedInvoiceDTO update(Long id, NamedInvoiceCreateDTO request) {
        NamedInvoice invoice = namedInvoiceRepo.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("NamedInvoice not found with given id: " + id));

        boolean numberChanged = !request.getSequenceInMonth().equals(invoice.getSequenceInMonth())
                || !request.getYear().equals(invoice.getYear())
                || !request.getMonth().equals(invoice.getMonth());
        if (numberChanged && namedInvoiceRepo.existsByYearAndMonthAndSequenceInMonthAndIdNot(
                request.getYear(), request.getMonth(), request.getSequenceInMonth(), id)) {
            throw new ConflictException("Faktura z numerem " + request.getSequenceInMonth()
                    + " w tym miesiącu już istnieje. Wybierz inny numer.");
        }

        applyRequest(invoice, request);
        replaceItems(invoice, request.getItems());
        recalculateTotals(invoice);

        return new NamedInvoiceDTO(namedInvoiceRepo.save(invoice), true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        NamedInvoice invoice = namedInvoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NamedInvoice not found with given id: " + id));
        namedInvoiceRepo.delete(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public NamedInvoiceDTO getById(Long id) {
        NamedInvoice invoice = namedInvoiceRepo.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("NamedInvoice not found with given id: " + id));
        return new NamedInvoiceDTO(invoice, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NamedInvoiceDTO> getByClient(Long clientId, int page, int size) {
        return namedInvoiceRepo.findAllByClientId(clientId, PageRequest.of(page, size))
                .map(invoice -> new NamedInvoiceDTO(invoice, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NamedInvoiceDTO> getByPeriod(int year, int month) {
        return namedInvoiceRepo.findAllByYearAndMonth(year, month).stream()
                .map(invoice -> new NamedInvoiceDTO(invoice, false))
                .toList();
    }

    int maxSequenceForMonth(YearMonth ym) {
        int fromNamed = namedInvoiceRepo.findMaxSequenceInMonth(ym.getYear(), ym.getMonthValue());

        int fromKsef = 0;
        List<String> ksefNumbers = issuedInvoiceRepo.findInvoiceNumbersByIssueDateBetween(
                ym.atDay(1), ym.atEndOfMonth());
        for (String number : ksefNumbers) {
            Integer parsed = leadingInt(number);
            if (parsed == null) {
                log.debug("Ignoring KSeF issued invoice number '{}' - no leading integer", number);
                continue;
            }
            fromKsef = Math.max(fromKsef, parsed);
        }

        return Math.max(fromNamed, fromKsef);
    }

    private Set<PaymentMethod> visitPaymentMethods(Visit visit) {
        List<Payment> payments = visit.getPayments();
        if (payments == null || payments.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Map<PaymentMethod, BigDecimal> totalByMethod = new LinkedHashMap<>();
        for (Payment payment : payments) {
            if (payment.getMethod() == null) {
                continue;
            }
            totalByMethod.merge(payment.getMethod(), MoneyUtils.nz(payment.getAmount()), BigDecimal::add);
        }

        return totalByMethod.entrySet().stream()
                .sorted(Map.Entry.<PaymentMethod, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Integer leadingInt(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = LEADING_DIGITS.matcher(value.trim());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private void applyRequest(NamedInvoice invoice, NamedInvoiceCreateDTO request) {
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setSequenceInMonth(request.getSequenceInMonth());
        invoice.setYear(request.getYear());
        invoice.setMonth(request.getMonth());
        invoice.setIssueDate(request.getIssueDate());
        invoice.setSaleDate(request.getSaleDate());
        replacePaymentMethods(invoice, request.getPaymentMethods());
        invoice.setNotes(trimToNull(request.getNotes()));
        invoice.setSourceVisitId(request.getSourceVisitId());
        invoice.setBuyerName(request.getBuyerName());
        invoice.setBuyerAddress(trimToNull(request.getBuyerAddress()));
        invoice.setBuyerPostalCode(trimToNull(request.getBuyerPostalCode()));
        invoice.setBuyerCity(trimToNull(request.getBuyerCity()));
    }

    private void replacePaymentMethods(NamedInvoice invoice, Set<PaymentMethod> methods) {
        if (invoice.getPaymentMethods() == null) {
            invoice.setPaymentMethods(new LinkedHashSet<>());
        }
        invoice.getPaymentMethods().clear();
        if (methods != null) {
            invoice.getPaymentMethods().addAll(methods);
        }
    }

    private void replaceItems(NamedInvoice invoice, List<NamedInvoiceItemDTO> items) {
        invoice.getItems().clear();
        if (items == null) {
            return;
        }
        for (NamedInvoiceItemDTO dto : items) {
            invoice.addItem(NamedInvoiceItem.builder()
                    .name(dto.getName())
                    .quantity(dto.getQuantity() != null ? dto.getQuantity() : BigDecimal.ONE)
                    .unitNetPrice(MoneyUtils.price(dto.getUnitNetPrice()))
                    .netValue(MoneyUtils.money(dto.getNetValue()))
                    .unitGrossPrice(MoneyUtils.price(dto.getUnitGrossPrice()))
                    .grossValue(MoneyUtils.money(dto.getGrossValue()))
                    .vatRate(dto.getVatRate())
                    .sourceType(dto.getSourceType())
                    .build());
        }
    }

    private void recalculateTotals(NamedInvoice invoice) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        for (NamedInvoiceItem item : invoice.getItems()) {
            net = net.add(MoneyUtils.nz(item.getNetValue()));
            gross = gross.add(MoneyUtils.nz(item.getGrossValue()));
        }
        invoice.setNetAmount(MoneyUtils.money(net));
        invoice.setGrossAmount(MoneyUtils.money(gross));
        invoice.setVatAmount(MoneyUtils.money(gross.subtract(net)));
    }

    private void learnClientAddress(Client client, NamedInvoiceCreateDTO request) {
        boolean clientHasAddress = isNotBlank(client.getAddress())
                || isNotBlank(client.getPostalCode())
                || isNotBlank(client.getCity());
        if (clientHasAddress) {
            return;
        }
        boolean formHasAddress = isNotBlank(request.getBuyerAddress())
                || isNotBlank(request.getBuyerPostalCode())
                || isNotBlank(request.getBuyerCity());
        if (!formHasAddress) {
            return;
        }
        client.setAddress(trimToNull(request.getBuyerAddress()));
        client.setPostalCode(trimToNull(request.getBuyerPostalCode()));
        client.setCity(trimToNull(request.getBuyerCity()));
        clientRepo.save(client);
    }

    private NamedInvoiceItemDTO serviceLine(VisitItem visitItem) {
        BigDecimal gross = MoneyUtils.money(visitItem.getFinalPrice());
        BigDecimal vat = MoneyUtils.vatFromGross(gross, SERVICE_VAT);
        BigDecimal net = MoneyUtils.money(gross.subtract(vat));

        NamedInvoiceItemDTO line = new NamedInvoiceItemDTO();
        line.setName(visitItem.getName());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitNetPrice(MoneyUtils.price(net));
        line.setNetValue(net);
        line.setUnitGrossPrice(MoneyUtils.price(gross));
        line.setGrossValue(gross);
        line.setVatRate(SERVICE_VAT);
        line.setSourceType("SERVICE");
        return line;
    }

    private NamedInvoiceItemDTO voucherLine(SaleItem saleItem) {
        BigDecimal gross = MoneyUtils.money(saleItem.getPrice());
        BigDecimal vat = MoneyUtils.vatFromGross(gross, SERVICE_VAT);
        BigDecimal net = MoneyUtils.money(gross.subtract(vat));

        NamedInvoiceItemDTO line = new NamedInvoiceItemDTO();
        line.setName(saleItem.getName());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitNetPrice(MoneyUtils.price(net));
        line.setNetValue(net);
        line.setUnitGrossPrice(MoneyUtils.price(gross));
        line.setGrossValue(gross);
        line.setVatRate(SERVICE_VAT);
        line.setSourceType("VOUCHER");
        return line;
    }

    private NamedInvoiceItemDTO productLine(SaleItem saleItem) {
        BigDecimal net = MoneyUtils.money(saleItem.getNetValue());
        BigDecimal vat = MoneyUtils.money(saleItem.getVatValue());
        BigDecimal gross = MoneyUtils.money(net.add(vat));

        NamedInvoiceItemDTO line = new NamedInvoiceItemDTO();
        line.setName(saleItem.getName());
        line.setQuantity(BigDecimal.ONE);
        line.setUnitNetPrice(MoneyUtils.price(net));
        line.setNetValue(net);
        line.setUnitGrossPrice(MoneyUtils.price(gross));
        line.setGrossValue(gross);
        line.setVatRate(saleItem.getProduct().getVatRate());
        line.setSourceType("PRODUCT");
        return line;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
