package com.clinic.clinicmanager.model.revision;

import com.clinic.clinicmanager.model.Client;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public enum ClientCardDataField {
    FIRST_NAME("firstName", "Imię", Client::getFirstName),
    LAST_NAME("lastName", "Nazwisko", Client::getLastName),
    BIRTH_DATE("birthDate", "Data urodzenia",
            client -> client.getBirthDate() == null ? null : client.getBirthDate().toString()),
    PHONE_NUMBER("phoneNumber", "Telefon", Client::getPhoneNumber),
    SIGNED_REGULATIONS("signedRegulations", "Regulamin podpisany",
            client -> String.valueOf(Boolean.TRUE.equals(client.getSignedRegulations()))),
    REMARKS("remarks", "Warto pamiętać", Client::getRemarks);

    private final String key;
    private final String label;
    private final Function<Client, String> extractor;

    ClientCardDataField(String key, String label, Function<Client, String> extractor) {
        this.key = key;
        this.label = label;
        this.extractor = extractor;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String valueOf(Client client) {
        return extractor.apply(client);
    }

    public static Optional<ClientCardDataField> fromKey(String key) {
        return Arrays.stream(values()).filter(field -> field.key.equals(key)).findFirst();
    }
}
