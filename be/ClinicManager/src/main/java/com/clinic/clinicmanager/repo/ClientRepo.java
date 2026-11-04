package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.repo.projection.ClientCardListProjection;
import com.clinic.clinicmanager.repo.projection.ClientMatchProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepo extends JpaRepository<Client, Long> {

    String CARD_SEARCH_MODE_ALL = "ALL";
    String CARD_SEARCH_MODE_PHONE = "PHONE";
    String CARD_SEARCH_MODE_NAME = "NAME";

    String CARD_SEARCH_WHERE = """
    WHERE (:includeDeleted = true OR c.is_deleted = false)
      AND (
            :mode = 'ALL'
         OR (:mode = 'PHONE' AND normalize_phone(c.phone_number) LIKE '%' || normalize_phone(:keyword) || '%')
         OR (:mode = 'NAME' AND NOT EXISTS (
                SELECT 1
                FROM unnest(regexp_split_to_array(normalize_person_name(:keyword), '[ -]+')) AS token(value)
                WHERE token.value <> ''
                  AND NOT EXISTS (
                        SELECT 1
                        FROM unnest(regexp_split_to_array(
                                normalize_person_name(c.first_name) || ' ' || normalize_person_name(c.last_name),
                                '[ -]+')) AS part(value)
                        WHERE part.value LIKE token.value || '%' ESCAPE '\\'
                  )
            ))
      )
    """;

    String MATCH_COLUMNS = """
    SELECT c.id AS id,
           c.first_name AS firstName,
           c.last_name AS lastName,
           c.birth_date AS birthDate,
           c.phone_number AS phoneNumber,
           (SELECT MAX(v.date) FROM visit v WHERE v.client_id = c.id AND v.absence = false) AS lastVisitDate,
           c.is_deleted AS isDeleted,
    """;

    String SAME_NAME_CONDITION =
            " (normalize_person_name(c.first_name) = normalize_person_name(:firstName)"
            + " AND normalize_person_name(c.last_name) = normalize_person_name(:lastName)) ";

    Optional<Client> findOneById(Long id);

    @Query("""
    SELECT new com.clinic.clinicmanager.DTO.ClientDTO(
                        c,
                        (CASE WHEN (SELECT COUNT(d) FROM ClientDebt d WHERE d.client.id = c.id) > 0 THEN true ELSE false END),
                        (SELECT COUNT(v) FROM Visit v WHERE v.client.id = c.id AND SIZE(v.items) > 0),
                        (CASE WHEN (SELECT COUNT(vch) FROM Voucher vch WHERE vch.client.id = c.id AND vch.status = com.clinic.clinicmanager.model.constants.VoucherStatus.ACTIVE) > 0 THEN true ELSE false END),
                        (CASE WHEN (SELECT COUNT(r) FROM Review r WHERE r.client.id = c.id AND r.source = com.clinic.clinicmanager.model.constants.ReviewSource.BOOKSY) > 0 THEN true ELSE false END),
                        (CASE WHEN (SELECT COUNT(r) FROM Review r WHERE r.client.id = c.id AND r.source = com.clinic.clinicmanager.model.constants.ReviewSource.GOOGLE) > 0 THEN true ELSE false END),
                        (CASE WHEN (SELECT COUNT(r) FROM Review r WHERE r.client.id = c.id AND r.source = com.clinic.clinicmanager.model.constants.ReviewSource.GOOGLE AND r.isUsed = false) > 0 THEN true ELSE false END)
                    )
    FROM Client c
    WHERE c.isDeleted = false
      AND (:keyword IS NULL OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%'))
         OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')))
      AND (:boostClient IS NULL OR c.boostClient = :boostClient)
      AND (:signedRegulations IS NULL OR c.signedRegulations = :signedRegulations)
      AND (
            :hasDebts IS NULL
            OR (:hasDebts = true AND EXISTS (SELECT 1 FROM ClientDebt d WHERE d.client.id = c.id))
            OR (:hasDebts = false AND NOT EXISTS (SELECT 1 FROM ClientDebt d WHERE d.client.id = c.id))
          )
      AND (:discountId IS NULL OR c.discount.id = :discountId)
""")
    List<ClientDTO> findAllWithFilters(
            @Param("keyword") String keyword,
            @Param("boostClient") Boolean boostClient,
            @Param("signedRegulations") Boolean signedRegulations,
            @Param("hasDebts") Boolean hasDebts,
            @Param("discountId") Long discountId
    );

    List<Client> findAllByDiscountId(Long discountId);

    @Query(
            value = """
    SELECT c.id AS id,
           c.first_name AS firstName,
           c.last_name AS lastName,
           c.birth_date AS birthDate,
           c.phone_number AS phoneNumber,
           c.is_deleted AS isDeleted,
           lv.date AS lastVisitDate,
           lv.absence AS lastVisitAbsence
    FROM client c
    LEFT JOIN LATERAL (
        SELECT v.date, v.absence
        FROM visit v
        WHERE v.client_id = c.id
        ORDER BY v.date DESC, v.id DESC
        LIMIT 1
    ) lv ON true
    """ + CARD_SEARCH_WHERE + """
    ORDER BY c.last_name COLLATE "pl-x-icu", c.first_name COLLATE "pl-x-icu", c.id
    """,
            countQuery = "SELECT COUNT(*) FROM client c " + CARD_SEARCH_WHERE,
            nativeQuery = true
    )
    Page<ClientCardListProjection> searchCards(
            @Param("mode") String mode,
            @Param("keyword") String keyword,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable
    );

    @Query(
            value = MATCH_COLUMNS + """
           'EXACT' AS matchReason
    FROM client c
    WHERE """ + SAME_NAME_CONDITION + """
      AND normalize_phone(c.phone_number) = normalize_phone(:phoneNumber)
      AND c.id <> :excludeId
      AND (:activeOnly = false OR c.is_deleted = false)
    ORDER BY c.is_deleted, c.id
    LIMIT 1
    """,
            nativeQuery = true
    )
    Optional<ClientMatchProjection> findExactMatchNative(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("phoneNumber") String phoneNumber,
            @Param("excludeId") long excludeId,
            @Param("activeOnly") boolean activeOnly
    );

    @Query(
            value = MATCH_COLUMNS + """
           CASE WHEN """ + SAME_NAME_CONDITION + """
                THEN 'SAME_NAME' ELSE 'SAME_PHONE' END AS matchReason
    FROM client c
    WHERE c.is_deleted = false
      AND c.id <> :excludeId
      AND (
            (""" + SAME_NAME_CONDITION + """
             AND normalize_phone(c.phone_number) <> normalize_phone(:phoneNumber))
         OR (normalize_phone(c.phone_number) = normalize_phone(:phoneNumber)
             AND length(normalize_phone(:phoneNumber)) >= 9
             AND (normalize_person_name(c.first_name) <> normalize_person_name(:firstName)
                  OR normalize_person_name(c.last_name) <> normalize_person_name(:lastName)))
      )
    ORDER BY c.last_name COLLATE "pl-x-icu", c.first_name COLLATE "pl-x-icu", c.id
    LIMIT 5
    """,
            nativeQuery = true
    )
    List<ClientMatchProjection> findSimilarNative(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("phoneNumber") String phoneNumber,
            @Param("excludeId") long excludeId
    );

    @Query(
            value = """
    SELECT COUNT(*) > 0
    FROM client c
    WHERE c.id = :id
      AND """ + SAME_NAME_CONDITION + """
      AND normalize_phone(c.phone_number) = normalize_phone(:phoneNumber)
    """,
            nativeQuery = true
    )
    boolean matchesIdentityNative(
            @Param("id") Long id,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("phoneNumber") String phoneNumber
    );

    default Optional<ClientMatchProjection> findExactMatch(String firstName, String lastName, String phoneNumber,
                                                           Long excludeId, boolean activeOnly) {
        return findExactMatchNative(nullToEmpty(firstName), nullToEmpty(lastName), nullToEmpty(phoneNumber),
                excludeIdOrNone(excludeId), activeOnly);
    }

    default List<ClientMatchProjection> findSimilar(String firstName, String lastName, String phoneNumber, Long excludeId) {
        return findSimilarNative(nullToEmpty(firstName), nullToEmpty(lastName), nullToEmpty(phoneNumber),
                excludeIdOrNone(excludeId));
    }

    default boolean matchesIdentity(Long id, String firstName, String lastName, String phoneNumber) {
        return matchesIdentityNative(id, nullToEmpty(firstName), nullToEmpty(lastName), nullToEmpty(phoneNumber));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static long excludeIdOrNone(Long excludeId) {
        return excludeId != null ? excludeId : 0L;
    }
}
