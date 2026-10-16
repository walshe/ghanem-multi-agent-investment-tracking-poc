package poc.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * <b>Responsibility:</b> start the checkout service.
 *
 * <p>Boots Spring, scans for components and binds the {@code @ConfigurationProperties} records.
 * Holds no logic of its own.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
