package gov.cms.admin.health;

import org.quartz.Scheduler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class QuartzHealthIndicator implements HealthIndicator {
    private final Scheduler scheduler;
    public QuartzHealthIndicator(Scheduler scheduler) { this.scheduler = scheduler; }
    @Override
    public Health health() {
        try {
            if (scheduler.isStarted() && !scheduler.isInStandbyMode()) {
                return Health.up().build();
            }
            return Health.down().withDetail("state", "standby or not started").build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
