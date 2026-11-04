package com.clinic.clinicmanager.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.interfaces.KSeFClient;
import pl.akmf.ksef.sdk.system.circuitbreaker.KsefCircuitBreakerOptions;
import pl.akmf.ksef.sdk.system.circuitbreaker.KsefCircuitBreakerProperties;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class KsefClientConfig {

    private final KsefApiPropertiesConfig apiProperties;

    @Bean
    public DefaultKsefClient ksefClient() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        KsefCircuitBreakerProperties circuitBreakerProperties = new KsefCircuitBreakerProperties() {
            @Override
            public KsefCircuitBreakerOptions getCircuitBreakerOptions() {
                return new KsefCircuitBreakerOptions(true, 5, 30);
            }
        };

        return new DefaultKsefClient(httpClient, apiProperties, circuitBreakerProperties, objectMapper);
    }

    @Bean
    public DefaultCryptographyService cryptographyService(KSeFClient ksefClient) {
        return new DefaultCryptographyService(ksefClient);
    }
}
