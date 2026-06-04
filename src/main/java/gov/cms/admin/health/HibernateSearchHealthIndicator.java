package gov.cms.admin.health;

import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class HibernateSearchHealthIndicator implements HealthIndicator {
    private final SearchMapping searchMapping;
    public HibernateSearchHealthIndicator(SearchMapping searchMapping) {
        this.searchMapping = searchMapping;
    }
    @Override
    public Health health() {
        try {
            searchMapping.allIndexedEntities();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
