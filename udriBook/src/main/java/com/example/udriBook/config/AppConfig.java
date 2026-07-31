package com.example.udriBook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

/**
 * Application-level configuration beans.
 *
 * RestTemplate: Registered with explicit connect + read timeouts.
 * Without timeouts, a slow/unresponsive MSG91 API call can hang a
 * request thread indefinitely under load — a thread-starvation risk.
 *
 * @EnableRetry: Activates Spring Retry's AOP proxy so that @Retryable
 *               annotations in Msg91Service are intercepted and actually retry.
 *               Without this annotation, @Retryable is silently ignored.
 *
 *               Requires: spring-retry + spring-boot-starter-aop in pom.xml.
 */
@Configuration
@EnableRetry
public class AppConfig {

    /**
     * RestTemplate with connection and read timeouts.
     *
     * connectTimeout: max time to establish TCP connection with MSG91 (5s)
     * readTimeout: max time waiting for MSG91 to send a response (5s)
     *
     * Both values must be set — a missing readTimeout means the thread
     * can hang for minutes if MSG91 is slow.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000); // 5 seconds
        factory.setReadTimeout(5_000); // 5 seconds
        return new RestTemplate(factory);
    }
}
