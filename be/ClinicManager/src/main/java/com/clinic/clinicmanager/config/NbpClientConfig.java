package com.clinic.clinicmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class NbpClientConfig {

    private static final Duration NBP_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient nbpRestClient(RestClient.Builder builder,
                                    @Value("${nbp.api-url:https://api.nbp.pl/api}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(NBP_TIMEOUT);
        requestFactory.setReadTimeout(NBP_TIMEOUT);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
