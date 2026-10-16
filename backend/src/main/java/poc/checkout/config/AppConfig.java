package poc.checkout.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <b>Responsibility:</b> wire up the application's infrastructure beans.
 *
 * <p>Currently that is two small things: the {@link Clock} every "now" comes from (tests replace it
 * to move time forward), and CORS for the web and mobile apps (checkout.cors-allowed-origins,
 * GET and POST on /checkouts/** only).
 *
 * <p>These two are kept together on purpose because this is a proof of concept and both are tiny.
 * In production code, split them into a ClockConfig and a CorsConfig so each has one responsibility.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final CheckoutProperties checkoutProperties;

    public AppConfig(CheckoutProperties checkoutProperties) {
        this.checkoutProperties = checkoutProperties;
    }

    /** All "now" values come from this clock, so tests can move time forward. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/checkouts/**")
                .allowedOrigins(checkoutProperties.corsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }
}
