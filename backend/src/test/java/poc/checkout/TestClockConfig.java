package poc.checkout;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * <b>Responsibility:</b> replace the application's system clock with a {@link MutableClock} in
 * tests that import this configuration.
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }
}
