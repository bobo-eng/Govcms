package gov.cms.admin.controller;

import gov.cms.admin.dto.HealthSummary;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthSummaryController {

    private final HealthEndpoint healthEndpoint;

    public HealthSummaryController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_admin','ROLE_site_admin')")
    public ResponseEntity<HealthSummary> getHealthSummary() {
        HealthSummary summary = new HealthSummary();
        var health = healthEndpoint.health();
        summary.setDb(getComponentStatus(health, "db"));
        summary.setRedis(getComponentStatus(health, "redis"));
        summary.setHibernateSearch(getComponentStatus(health, "hibernateSearch"));
        summary.setQuartz(getComponentStatus(health, "quartz"));
        return ResponseEntity.ok(summary);
    }

    private String getComponentStatus(org.springframework.boot.actuate.health.HealthComponent root, String key) {
        if (root instanceof org.springframework.boot.actuate.health.SystemHealth systemHealth) {
            HealthComponent component = systemHealth.getComponents().get(key);
            if (component != null) {
                return component.getStatus().getCode();
            }
        }
        if (root instanceof org.springframework.boot.actuate.health.CompositeHealth compositeHealth) {
            HealthComponent component = compositeHealth.getComponents().get(key);
            if (component != null) {
                return component.getStatus().getCode();
            }
        }
        return Status.UNKNOWN.getCode();
    }
}
