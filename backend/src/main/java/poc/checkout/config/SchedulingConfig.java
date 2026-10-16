package poc.checkout.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import poc.checkout.job.CheckoutJob;

/** Runs the scheduled job every checkout.job-interval (fixed delay, so runs never overlap). */
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
