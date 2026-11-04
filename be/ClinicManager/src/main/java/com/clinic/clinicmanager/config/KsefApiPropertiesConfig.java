package com.clinic.clinicmanager.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import pl.akmf.ksef.sdk.api.KsefApiProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Setter
@Configuration
@ConfigurationProperties(prefix = "ksef-sdk")
public class KsefApiPropertiesConfig extends KsefApiProperties {

    private String baseUri;
    private String suffixUri;
    private int requestTimeout;
    private Map<String, String> defaultHeaders;

    @Override
    public String getBaseUri() { return baseUri; }

    @Override
    public String getSuffixUri() { return suffixUri; }

    @Override
    public String getQrUri() { return null; }

    @Override
    public Duration getRequestTimeout() { return Duration.ofSeconds(requestTimeout); }

    @Override
    public Map<String, String> getDefaultHeaders() {
        if (defaultHeaders == null) defaultHeaders = new HashMap<>();
        return defaultHeaders;
    }
}
