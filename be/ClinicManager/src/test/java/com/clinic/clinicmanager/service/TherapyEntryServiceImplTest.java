package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.DTO.NewTherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryRevisionDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryUpdateDTO;
import com.clinic.clinicmanager.DTO.TherapyTimelineDTO;
import com.clinic.clinicmanager.DTO.TimelineItemDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryServiceDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TherapyEntryConflictException;
import com.clinic.clinicmanager.exceptions.TherapyEntryDayTakenException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.VisitItem;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.TimelineItemType;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRevisionRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.projection.VisitProductQuantityProjection;
import com.clinic.clinicmanager.service.impl.TherapyEntryServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TherapyEntryServiceImplTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), WARSAW);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final long CLIENT_ID = 1L;
    private static final long CARD_ID = 10L;
    private static final long ANNA = 7L;
    private static final long RECEPTION = 8L;

    @Mock ClientRepo clientRepo;
    @Mock ClientCardRepo clientCardRepo;
    @Mock ClientCardService clientCardService;
    @Mock TherapyEntryRepo entryRepo;
    @Mock TherapyEntryRevisionRepo revisionRepo;
    @Mock VisitRepo visitRepo;
    @Mock UserRepo userRepo;
    @Mock CardAccessLogService cardAccessLogService;

    TherapyEntryServiceImpl service;

    private MockedStatic<SessionUtils> session;
    private final List<TherapyEntry> entries = new ArrayList<>();
    private final List<Visit> visits = new ArrayList<>();
    private final List<VisitProductQuantityProjection> productRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = serviceAt(CLOCK);
        session = mockStatic(SessionUtils.class);
        loginAs(ANNA, false);
        lenient().when(clientRepo.findOneById(CLIENT_ID)).thenReturn(Optional.of(client(false)));
        lenient().when(clientCardService.getOrCreateCard(CLIENT_ID)).thenReturn(card());
        lenient().when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(card()));
        lenient().when(userRepo.findAllWithEmployeeByIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return users().stream().filter(user -> ids.contains(user.getId())).toList();
        });
        lenient().when(entryRepo.saveAndFlush(any(TherapyEntry.class))).thenAnswer(invocation -> {
            TherapyEntry entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(100L);
                entries.add(entry);
            }
            return entry;
        });
        lenient().when(entryRepo.findValidByCardIdAndEntryDate(eq(CARD_ID), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return entries.stream().filter(e -> !e.isInvalid() && e.getEntryDate().equals(date)).findFirst();
        });
        lenient().when(entryRepo.findByIdAndCardId(anyLong(), eq(CARD_ID))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return entries.stream().filter(e -> e.getId().equals(id)).findFirst();
        });
        lenient().when(entryRepo.findEntryDatesBefore(eq(CARD_ID), any(), any())).thenAnswer(invocation -> {
            LocalDate before = invocation.getArgument(1);
            Pageable page = invocation.getArgument(2);
            return entries.stream().map(TherapyEntry::getEntryDate).filter(d -> d.isBefore(before))
                    .distinct().sorted(Comparator.reverseOrder()).limit(page.getPageSize()).toList();
        });
        lenient().when(entryRepo.findByCardIdAndEntryDateIn(eq(CARD_ID), anyCollection())).thenAnswer(invocation -> {
            Collection<LocalDate> dates = invocation.getArgument(1);
            return entries.stream().filter(e -> dates.contains(e.getEntryDate())).toList();
        });
        lenient().when(visitRepo.findVisitDatesByClientIdBefore(eq(CLIENT_ID), any(), any())).thenAnswer(invocation -> {
            LocalDate before = invocation.getArgument(1);
            Pageable page = invocation.getArgument(2);
            return visits.stream().map(Visit::getDate).filter(d -> d.isBefore(before))
                    .distinct().sorted(Comparator.reverseOrder()).limit(page.getPageSize()).toList();
        });
        lenient().when(visitRepo.findWithEmployeeAndItemsByClientIdAndDateIn(eq(CLIENT_ID), anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<LocalDate> dates = invocation.getArgument(1);
                    return visits.stream().filter(v -> dates.contains(v.getDate())).toList();
                });
        lenient().when(visitRepo.findProductQuantitiesByVisitIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return productRows.stream().filter(row -> ids.contains(row.getVisitId())).toList();
        });
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    private TherapyEntryServiceImpl serviceAt(Clock clock) {
        return new TherapyEntryServiceImpl(clientRepo, clientCardRepo, clientCardService, entryRepo, revisionRepo,
                visitRepo, userRepo, cardAccessLogService, clock);
    }

    private void loginAs(long userId, boolean master) {
        session.when(SessionUtils::getUserIdFromSession).thenReturn(userId);
        session.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(master);
    }

    private static List<User> users() {
        Employee anna = Employee.builder().id(1L).name("Anna").lastName("Nowak").drawingColor("#2563eb").build();
        return List.of(
                User.builder().id(ANNA).username("anna.nowak").employee(anna).build(),
                User.builder().id(RECEPTION).username("recepcja").build());
    }

    private static Client client(boolean deleted) {
        return Client.builder().id(CLIENT_ID).firstName("Jan").lastName("Kowalski").isDeleted(deleted).build();
    }

    private static ClientCard card() {
        return ClientCard.builder().id(CARD_ID).client(client(false)).version(0L).build();
    }

    private void deletedClient() {
        when(clientRepo.findOneById(CLIENT_ID)).thenReturn(Optional.of(client(true)));
    }

    private TherapyEntry entry(long id, LocalDate date, long author, long version, boolean invalid) {
        TherapyEntry entry = TherapyEntry.builder()
                .id(id).card(card()).entryDate(date).content("Wpis " + id).authorUserId(author)
                .createdAt(CREATED_AT.plusMinutes(id)).version(version)
                .invalidatedAt(invalid ? CREATED_AT.plusDays(1) : null)
                .invalidatedByUserId(invalid ? author : null)
                .build();
        entries.add(entry);
        return entry;
    }

    private Visit visit(long id, LocalDate date, boolean absence, PaymentStatus status, String... services) {
        List<VisitItem> items = new ArrayList<>();
        for (int i = 0; i < services.length; i++) {
            items.add(VisitItem.builder().id(id * 10 + services.length - i).name(services[i]).build());
        }
        Visit visit = Visit.builder()
                .id(id).date(date).absence(absence).paymentStatus(status)
                .employee(Employee.builder().id(1L).name("Anna").lastName("Nowak").build())
                .items(items).totalValue(new BigDecimal("150.00"))
                .build();
        visits.add(visit);
        return visit;
    }

    private void product(long visitId, String name, long quantity) {
        product(visitId, name, quantity, null);
    }

    private void product(long visitId, String name, long quantity, String color) {
        productRows.add(new VisitProductQuantityProjection() {
            @Override
            public Long getVisitId() {
                return visitId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Long getQuantity() {
                return quantity;
            }

            @Override
            public String getColor() {
                return color;
            }
        });
    }

    @Test
    void createEntry_today_savesTrimmedContentAndReturnsEntryWithAttendedVisits() {
        visit(1L, TODAY, false, PaymentStatus.PAID, "Pedicure");
        visit(2L, TODAY, true, PaymentStatus.UNPAID);

        TimelineItemDTO item = service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "  Odciski usunięte.  "));

        ArgumentCaptor<TherapyEntry> captor = ArgumentCaptor.forClass(TherapyEntry.class);
        verify(entryRepo).saveAndFlush(captor.capture());
        TherapyEntry saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("Odciski usunięte.");
        assertThat(saved.getEntryDate()).isEqualTo(TODAY);
        assertThat(saved.getAuthorUserId()).isEqualTo(ANNA);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(clientCardRepo).lockCard(CARD_ID);

        assertThat(item.getType()).isEqualTo(TimelineItemType.ENTRY);
        assertThat(item.getDate()).isEqualTo(TODAY);
        assertThat(item.getEntry().getId()).isEqualTo(100L);
        assertThat(item.getEntry().getContent()).isEqualTo("Odciski usunięte.");
        assertThat(item.getEntry().getAuthorName()).isEqualTo("Anna");
        assertThat(item.getEntry().getEdited()).isFalse();
        assertThat(item.getEntry().getInvalid()).isFalse();
        assertThat(item.getEntry().getCanEdit()).isTrue();
        assertThat(item.getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(1L);
        verify(cardAccessLogService).log(CardAccessAction.CREATE_THERAPY_ENTRY, CLIENT_ID, "THERAPY_ENTRY", 100L);
    }

    @Test
    void createEntry_pastDate_isAccepted() {
        TimelineItemDTO item = service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY.minusDays(30), "Kontrola"));

        assertThat(item.getDate()).isEqualTo(TODAY.minusDays(30));
        assertThat(item.getVisits()).isEmpty();
        verify(visitRepo, never()).findProductQuantitiesByVisitIdIn(anyCollection());
    }

    @Test
    void createEntry_futureDate_returns400() {
        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY.plusDays(1), "Wpis")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Data wpisu nie może być z przyszłości.");

        verify(entryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void createEntry_todayInWarsawIsAccepted_whenUtcIsStillPreviousDay() {
        TherapyEntryServiceImpl lateNight = serviceAt(Clock.fixed(Instant.parse("2026-07-14T23:30:00Z"), WARSAW));

        TimelineItemDTO item = lateNight.createEntry(CLIENT_ID, new NewTherapyEntryDTO(LocalDate.of(2026, 7, 15), "Wpis"));

        assertThat(item.getDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void createEntry_dayAfterTodayInWarsaw_returns400() {
        TherapyEntryServiceImpl lateNight = serviceAt(Clock.fixed(Instant.parse("2026-07-14T23:30:00Z"), WARSAW));

        assertThatThrownBy(() -> lateNight.createEntry(CLIENT_ID, new NewTherapyEntryDTO(LocalDate.of(2026, 7, 16), "Wpis")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Data wpisu nie może być z przyszłości.");

        verify(entryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createEntry_missingDate_returns400() {
        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(null, "Wpis")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Data wpisu jest wymagana.");
    }

    @Test
    void createEntry_blankContent_returns400() {
        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "   ")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Treść wpisu nie może być pusta.");
        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, null)))
                .isInstanceOf(ValidationException.class);

        verify(clientCardService, never()).getOrCreateCard(anyLong());
    }

    @Test
    void createEntry_tooLongContent_returns400_butLimitItselfIsAccepted() {
        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "a".repeat(5001))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Treść wpisu może mieć maksymalnie 5000 znaków.");

        TimelineItemDTO item = service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, " " + "a".repeat(5000) + " "));
        assertThat(item.getEntry().getContent()).hasSize(5000);
    }

    @Test
    void createEntry_dayTaken_returns409WithExistingEntryId() {
        entry(55L, TODAY, RECEPTION, 0L, false);

        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "Drugi wpis")))
                .isInstanceOfSatisfying(TherapyEntryDayTakenException.class,
                        ex -> assertThat(ex.getEntryId()).isEqualTo(55L));

        verify(entryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void createEntry_invalidEntryDoesNotBlockDay() {
        entry(55L, TODAY, ANNA, 0L, true);

        TimelineItemDTO item = service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "Poprawiony wpis"));

        assertThat(item.getEntry().getId()).isEqualTo(100L);
        assertThat(item.getEntry().getInvalid()).isFalse();
    }

    @Test
    void createEntry_uniqueIndexRace_returns409() {
        doThrow(new DataIntegrityViolationException("dup")).when(entryRepo).saveAndFlush(any(TherapyEntry.class));

        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "Wpis")))
                .isInstanceOf(TherapyEntryDayTakenException.class);
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void createEntry_deletedClient_returns409_evenForAdmin() {
        deletedClient();
        loginAs(ANNA, true);

        assertThatThrownBy(() -> service.createEntry(CLIENT_ID, new NewTherapyEntryDTO(TODAY, "Wpis")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Klient jest usunięty. Przywróć klienta przed edycją karty.");
        verify(clientCardService, never()).getOrCreateCard(anyLong());
    }

    @Test
    void updateEntry_notAuthor_returns403() {
        entry(55L, TODAY, RECEPTION, 0L, false);

        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", 0L)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Możesz edytować tylko własne wpisy.");
        verifyNoInteractions(revisionRepo);
    }

    @Test
    void updateEntry_versionMismatch_returns409WithCurrentVersion() {
        entry(55L, TODAY, ANNA, 3L, false);

        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", 2L)))
                .isInstanceOfSatisfying(TherapyEntryConflictException.class,
                        ex -> assertThat(ex.getCurrentVersion()).isEqualTo(3L));
        verifyNoInteractions(revisionRepo);
    }

    @Test
    void updateEntry_dateMovedToTakenDay_returns409WithOtherEntryId() {
        entry(55L, TODAY, ANNA, 0L, false);
        entry(56L, TODAY.minusDays(1), RECEPTION, 0L, false);

        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L,
                new TherapyEntryUpdateDTO(TODAY.minusDays(1), "Wpis 55", 0L)))
                .isInstanceOfSatisfying(TherapyEntryDayTakenException.class,
                        ex -> assertThat(ex.getEntryId()).isEqualTo(56L));
        verifyNoInteractions(revisionRepo);
    }

    @Test
    void updateEntry_noActualChange_skipsRevisionAndVersionBump() {
        TherapyEntry entry = entry(55L, TODAY, ANNA, 2L, false);

        TimelineItemDTO item = service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "  Wpis 55 ", 2L));

        assertThat(item.getEntry().getVersion()).isEqualTo(2L);
        assertThat(entry.getUpdatedAt()).isNull();
        verifyNoInteractions(revisionRepo);
        verify(entryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void updateEntry_change_storesRevisionWithPreviousValuesBeforeUpdate() {
        entry(55L, TODAY, ANNA, 3L, false);
        when(revisionRepo.save(any(TherapyEntryRevision.class))).thenAnswer(invocation -> {
            TherapyEntryRevision saved = invocation.getArgument(0);
            saved.setId(900L);
            return saved;
        });

        TimelineItemDTO item = service.updateEntry(CLIENT_ID, 55L,
                new TherapyEntryUpdateDTO(TODAY.minusDays(2), " Nowa treść ", 3L));

        ArgumentCaptor<TherapyEntryRevision> revision = ArgumentCaptor.forClass(TherapyEntryRevision.class);
        InOrder order = inOrder(clientCardRepo, revisionRepo, entryRepo);
        order.verify(clientCardRepo).lockCard(CARD_ID);
        order.verify(revisionRepo).save(revision.capture());
        order.verify(entryRepo).saveAndFlush(any(TherapyEntry.class));
        assertThat(revision.getValue().getPreviousEntryDate()).isEqualTo(TODAY);
        assertThat(revision.getValue().getPreviousContent()).isEqualTo("Wpis 55");
        assertThat(revision.getValue().getChangedByUserId()).isEqualTo(ANNA);
        assertThat(revision.getValue().getChangedAt()).isNotNull();

        assertThat(item.getDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(item.getEntry().getContent()).isEqualTo("Nowa treść");
        assertThat(item.getEntry().getVersion()).isEqualTo(4L);
        assertThat(item.getEntry().getEdited()).isTrue();
        assertThat(item.getEntry().getUpdatedAt()).isEqualTo(revision.getValue().getChangedAt());
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_THERAPY_ENTRY, CLIENT_ID,
                "THERAPY_ENTRY_REVISION", 900L);
    }

    @Test
    void updateEntry_invalidEntry_returns409() {
        entry(55L, TODAY, ANNA, 0L, true);

        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", 0L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Błędnego wpisu nie można edytować.");
    }

    @Test
    void updateEntry_entryOfAnotherCard_returns404() {
        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 999L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateEntry_missingExpectedVersion_returns400() {
        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Brak wersji wpisu.");
    }

    @Test
    void updateEntry_deletedClient_returns409() {
        deletedClient();

        assertThatThrownBy(() -> service.updateEntry(CLIENT_ID, 55L, new TherapyEntryUpdateDTO(TODAY, "Zmiana", 0L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void invalidateEntry_byAuthor_marksInvalidAndLogs() {
        TherapyEntry entry = entry(55L, TODAY, ANNA, 1L, false);

        TimelineItemDTO item = service.invalidateEntry(CLIENT_ID, 55L);

        assertThat(entry.getInvalidatedAt()).isNotNull();
        assertThat(entry.getInvalidatedByUserId()).isEqualTo(ANNA);
        assertThat(item.getEntry().getInvalid()).isTrue();
        assertThat(item.getEntry().getInvalidatedAt()).isEqualTo(entry.getInvalidatedAt());
        assertThat(item.getEntry().getCanEdit()).isFalse();
        verify(clientCardRepo).lockCard(CARD_ID);
        verify(cardAccessLogService).log(CardAccessAction.INVALIDATE_THERAPY_ENTRY, CLIENT_ID, "THERAPY_ENTRY", 55L);
    }

    @Test
    void invalidateEntry_alreadyInvalid_returns409() {
        entry(55L, TODAY, ANNA, 1L, true);

        assertThatThrownBy(() -> service.invalidateEntry(CLIENT_ID, 55L))
                .isInstanceOf(ConflictException.class);
        verify(entryRepo, never()).saveAndFlush(any());
    }

    @Test
    void invalidateEntry_byOtherUser_returns403() {
        entry(55L, TODAY, RECEPTION, 1L, false);

        assertThatThrownBy(() -> service.invalidateEntry(CLIENT_ID, 55L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void invalidateEntry_deletedClient_returns409() {
        deletedClient();
        loginAs(ANNA, true);

        assertThatThrownBy(() -> service.invalidateEntry(CLIENT_ID, 55L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getRevisions_returnsRevisionsWithNamesAndLogs() {
        TherapyEntry entry = entry(55L, TODAY, ANNA, 2L, false);
        when(revisionRepo.findByEntryIdOrderByChangedAtDescIdDesc(55L)).thenReturn(List.of(
                TherapyEntryRevision.builder().id(2L).entry(entry).previousEntryDate(TODAY.minusDays(1))
                        .previousContent("Druga").changedAt(CREATED_AT.plusHours(2)).changedByUserId(ANNA).build(),
                TherapyEntryRevision.builder().id(1L).entry(entry).previousEntryDate(TODAY.minusDays(1))
                        .previousContent("Pierwsza").changedAt(CREATED_AT.plusHours(1)).changedByUserId(RECEPTION).build()));

        List<TherapyEntryRevisionDTO> revisions = service.getRevisions(CLIENT_ID, 55L);

        assertThat(revisions)
                .extracting(TherapyEntryRevisionDTO::getPreviousContent, TherapyEntryRevisionDTO::getChangedByName)
                .containsExactly(tuple("Druga", "Anna"), tuple("Pierwsza", "recepcja"));
        verify(cardAccessLogService).log(CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY, CLIENT_ID, "THERAPY_ENTRY", 55L);
    }

    @Test
    void getRevisions_deletedClient_returns404ForUser_butAdminSeesIt() {
        entry(55L, TODAY, ANNA, 0L, false);
        deletedClient();

        assertThatThrownBy(() -> service.getRevisions(CLIENT_ID, 55L))
                .isInstanceOf(ResourceNotFoundException.class);

        loginAs(ANNA, true);
        assertThat(service.getRevisions(CLIENT_ID, 55L)).isEmpty();
    }

    @Test
    void getTimeline_deletedClient_returns404ForUser_butAdminSeesIt() {
        deletedClient();

        assertThatThrownBy(() -> service.getTimeline(CLIENT_ID, null, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cardAccessLogService);

        loginAs(ANNA, true);
        assertThat(service.getTimeline(CLIENT_ID, null, 20).getItems()).isEmpty();
    }

    @Test
    void getTimeline_buildsItemTypesAndOrderWithinDay() {
        LocalDate d1 = TODAY.minusDays(1);
        LocalDate d2 = TODAY.minusDays(2);
        LocalDate d3 = TODAY.minusDays(3);
        LocalDate d4 = TODAY.minusDays(4);
        LocalDate d5 = TODAY.minusDays(5);
        visit(11L, d1, true, PaymentStatus.UNPAID);
        visit(10L, d1, false, PaymentStatus.PAID, "Pedicure");
        entry(21L, d1, RECEPTION, 0L, true);
        entry(22L, d1, ANNA, 0L, false);
        visit(12L, d2, false, PaymentStatus.PAID, "Kontrola");
        entry(23L, d3, ANNA, 0L, true);
        visit(13L, d3, false, PaymentStatus.PARTIAL, "Klamra");
        entry(24L, d4, RECEPTION, 1L, false);
        visit(14L, d5, true, PaymentStatus.PARTIAL);

        TherapyTimelineDTO timeline = service.getTimeline(CLIENT_ID, null, 20);

        assertThat(timeline.getNextBeforeDate()).isNull();
        assertThat(timeline.getItems())
                .extracting(TimelineItemDTO::getType, TimelineItemDTO::getDate)
                .containsExactly(
                        tuple(TimelineItemType.ENTRY, d1),
                        tuple(TimelineItemType.ENTRY, d1),
                        tuple(TimelineItemType.ABSENCE, d1),
                        tuple(TimelineItemType.VISIT, d2),
                        tuple(TimelineItemType.ENTRY, d3),
                        tuple(TimelineItemType.VISIT, d3),
                        tuple(TimelineItemType.ENTRY, d4),
                        tuple(TimelineItemType.ABSENCE, d5));

        List<TimelineItemDTO> items = timeline.getItems();
        assertThat(items.get(0).getEntry().getId()).isEqualTo(22L);
        assertThat(items.get(0).getEntry().getCanEdit()).isTrue();
        assertThat(items.get(0).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(10L);
        assertThat(items.get(1).getEntry().getId()).isEqualTo(21L);
        assertThat(items.get(1).getEntry().getInvalid()).isTrue();
        assertThat(items.get(1).getEntry().getCanEdit()).isFalse();
        assertThat(items.get(1).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(10L);
        assertThat(items.get(2).getEntry()).isNull();
        assertThat(items.get(2).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(11L);
        assertThat(items.get(3).getEntry()).isNull();
        assertThat(items.get(3).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(12L);
        assertThat(items.get(4).getEntry().getInvalid()).isTrue();
        assertThat(items.get(5).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(13L);
        assertThat(items.get(6).getEntry().getAuthorName()).isEqualTo("recepcja");
        assertThat(items.get(6).getEntry().getCanEdit()).isFalse();
        assertThat(items.get(6).getVisits()).isEmpty();
        assertThat(items.get(7).getVisits()).singleElement().satisfies(v -> {
            assertThat(v.getAbsence()).isTrue();
            assertThat(v.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        });
        verify(cardAccessLogService).log(CardAccessAction.VIEW_THERAPY_ENTRIES, CLIENT_ID, "THERAPY_TIMELINE", CARD_ID);
    }

    @Test
    void getTimeline_pagesByDistinctDays_andEndsWithNullCursor() {
        LocalDate d1 = TODAY.minusDays(1);
        LocalDate d2 = TODAY.minusDays(2);
        LocalDate d3 = TODAY.minusDays(3);
        LocalDate d4 = TODAY.minusDays(4);
        LocalDate d5 = TODAY.minusDays(5);
        entry(21L, d1, ANNA, 0L, false);
        visit(10L, d1, false, PaymentStatus.PAID, "Pedicure");
        visit(11L, d2, false, PaymentStatus.PAID, "Pedicure");
        visit(12L, d2, false, PaymentStatus.PAID, "Kontrola");
        entry(22L, d3, ANNA, 0L, true);
        entry(23L, d3, ANNA, 0L, false);
        visit(13L, d4, true, PaymentStatus.UNPAID);
        entry(24L, d5, ANNA, 0L, false);

        TherapyTimelineDTO first = service.getTimeline(CLIENT_ID, null, 2);
        assertThat(first.getItems()).extracting(TimelineItemDTO::getDate).containsOnly(d1, d2);
        assertThat(first.getItems().get(1).getVisits()).extracting(VisitSummaryDTO::getId).containsExactly(11L, 12L);
        assertThat(first.getNextBeforeDate()).isEqualTo(d2);

        TherapyTimelineDTO second = service.getTimeline(CLIENT_ID, first.getNextBeforeDate(), 2);
        assertThat(second.getItems()).extracting(TimelineItemDTO::getDate).containsExactly(d3, d3, d4);
        assertThat(second.getNextBeforeDate()).isEqualTo(d4);

        TherapyTimelineDTO last = service.getTimeline(CLIENT_ID, second.getNextBeforeDate(), 2);
        assertThat(last.getItems()).extracting(TimelineItemDTO::getDate).containsExactly(d5);
        assertThat(last.getNextBeforeDate()).isNull();

        TherapyTimelineDTO exact = service.getTimeline(CLIENT_ID, d4, 1);
        assertThat(exact.getNextBeforeDate()).isNull();
    }

    @Test
    void getTimeline_visitSummaryContainsServicesProductsAndPayment() {
        Visit visit = visit(10L, TODAY.minusDays(1), false, PaymentStatus.PARTIAL, "Pedicure", "Klamra");
        visit.setTotalValue(new BigDecimal("245.50"));
        visit.getItems().get(0).setService(BaseService.builder()
                .category(BaseServiceCategory.builder().color("143,118,230").build()).build());
        product(10L, "Krem mocznikowy", 2L, "20,184,166");
        product(10L, "Pasta ochronna", 1L);

        TherapyTimelineDTO timeline = service.getTimeline(CLIENT_ID, null, 20);

        VisitSummaryDTO summary = timeline.getItems().get(0).getVisits().get(0);
        assertThat(timeline.getItems().get(0).getType()).isEqualTo(TimelineItemType.VISIT);
        assertThat(summary.getId()).isEqualTo(10L);
        assertThat(summary.getEmployeeName()).isEqualTo("Anna");
        assertThat(summary.getServices())
                .extracting(VisitSummaryServiceDTO::getName, VisitSummaryServiceDTO::getColor)
                .containsExactly(tuple("Klamra", null), tuple("Pedicure", "143,118,230"));
        assertThat(summary.getProducts())
                .extracting(p -> p.getName(), p -> p.getQuantity(), p -> p.getColor())
                .containsExactly(tuple("Krem mocznikowy", 2L, "20,184,166"), tuple("Pasta ochronna", 1L, null));
        assertThat(summary.getAbsence()).isFalse();
        assertThat(summary.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(summary.getTotalValue()).isEqualByComparingTo("245.50");
    }

    @Test
    void getTimeline_usesBatchQueries_regardlessOfDayCount() {
        for (int i = 1; i <= 6; i++) {
            entry(20L + i, TODAY.minusDays(i), i % 2 == 0 ? ANNA : RECEPTION, 0L, false);
            visit(10L + i, TODAY.minusDays(i), i % 3 == 0, PaymentStatus.PAID, "Pedicure");
        }

        service.getTimeline(CLIENT_ID, null, 20);

        verify(entryRepo, times(1)).findEntryDatesBefore(eq(CARD_ID), any(), any());
        verify(visitRepo, times(1)).findVisitDatesByClientIdBefore(eq(CLIENT_ID), any(), any());
        verify(entryRepo, times(1)).findByCardIdAndEntryDateIn(eq(CARD_ID), anyCollection());
        verify(visitRepo, times(1)).findWithEmployeeAndItemsByClientIdAndDateIn(eq(CLIENT_ID), anyCollection());
        verify(visitRepo, times(1)).findProductQuantitiesByVisitIdIn(anyCollection());
        verify(userRepo, times(1)).findAllWithEmployeeByIdIn(anyCollection());
        verify(cardAccessLogService, times(1)).log(any(), anyLong(), any(), any());
    }

    @Test
    void getTimeline_withoutCard_showsVisitsAndDoesNotCreateCard() {
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
        visit(10L, TODAY.minusDays(3), false, PaymentStatus.PAID, "Pedicure");

        TherapyTimelineDTO timeline = service.getTimeline(CLIENT_ID, null, 20);

        assertThat(timeline.getItems()).extracting(TimelineItemDTO::getType).containsExactly(TimelineItemType.VISIT);
        verifyNoInteractions(clientCardService);
        verify(entryRepo, never()).findEntryDatesBefore(anyLong(), any(), any());
        verify(cardAccessLogService).log(CardAccessAction.VIEW_THERAPY_ENTRIES, CLIENT_ID, "THERAPY_TIMELINE", null);
    }

    @Test
    void getTimeline_emptyHistory_skipsDetailQueries() {
        TherapyTimelineDTO timeline = service.getTimeline(CLIENT_ID, null, 20);

        assertThat(timeline.getItems()).isEmpty();
        assertThat(timeline.getNextBeforeDate()).isNull();
        verify(visitRepo, never()).findWithEmployeeAndItemsByClientIdAndDateIn(anyLong(), anyCollection());
        verify(entryRepo, never()).findByCardIdAndEntryDateIn(anyLong(), anyCollection());
    }

    @Test
    void getTimeline_daysOutOfRange_returns400() {
        assertThatThrownBy(() -> service.getTimeline(CLIENT_ID, null, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.getTimeline(CLIENT_ID, null, 101))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(cardAccessLogService);
    }
}
