package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
@Component
public class RateLimitProperties {
    private boolean enabled = true;
    private List<Rule> rules = List.of();

    public static class Rule {
        private String path;
        private long capacity;
        private long refill;
        private String period;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }
        public long getRefill() { return refill; }
        public void setRefill(long refill) { this.refill = refill; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }
}
