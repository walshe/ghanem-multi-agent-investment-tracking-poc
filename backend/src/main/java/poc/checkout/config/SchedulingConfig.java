package poc.checkout.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import poc.checkout.job.CheckoutJob;

/**
 * <b>Responsibility:</b> decide <em>when</em> the scheduled job runs: every checkout.job-interval,
 * with a fixed delay so runs never overlap, and not at all if checkout.job-enabled is false.
 *
 * <p><b>Not responsible for:</b> what the job does ({@link CheckoutJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private final CheckoutProperties properties;
    private final CheckoutJob job;

    public SchedulingConfig(CheckoutProperties properties, CheckoutJob job) {
        this.properties = properties;
        this.job = job;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (properties.jobEnabled()) {
            registrar.addFixedDelayTask(job::runOnce, properties.jobInterval());
        }
    }
}
