package com.example.verification;

/**
 * Where the services under test run.
 *
 * <p>Lookup order: system property, then environment variable, then the default.
 * <ul>
 *   <li>backend: {@code -Dbackend.url} / {@code BACKEND_URL}, default http://localhost:8080</li>
 *   <li>provider: {@code -Dprovider.url} / {@code PROVIDER_URL}, default http://localhost:8081</li>
 * </ul>
 */
final class TestConfig {

    static final String BACKEND_URL = resolve("backend.url", "BACKEND_URL", "http://localhost:8080");
    static final String PROVIDER_URL = resolve("provider.url", "PROVIDER_URL", "http://localhost:8081");

    private TestConfig() {
    }

    private static String resolve(String property, String envVar, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        if (value == null || value.isBlank()) {
            value = defaultValue;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
