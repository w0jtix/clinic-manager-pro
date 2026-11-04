package com.clinic.clinicmanager.model.converter;

import com.clinic.clinicmanager.utils.CardEncryption;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final ObjectProvider<CardEncryption> cardEncryption;

    public EncryptedStringConverter(ObjectProvider<CardEncryption> cardEncryption) {
        this.cardEncryption = cardEncryption;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cardEncryption.getObject().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cardEncryption.getObject().decrypt(dbData);
    }
}
