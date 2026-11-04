package com.clinic.clinicmanager.config.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class RoleMaskingHttpMessageConverter extends MappingJackson2HttpMessageConverter {

    public RoleMaskingHttpMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected ObjectWriter customizeWriter(ObjectWriter writer, @Nullable JavaType javaType, @Nullable MediaType contentType) {
        return super.customizeWriter(writer, javaType, contentType)
                .withAttribute(RoleMaskingModule.HTTP_RESPONSE_ATTRIBUTE, Boolean.TRUE);
    }
}
