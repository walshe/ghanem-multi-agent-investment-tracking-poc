package poc.checkout.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
