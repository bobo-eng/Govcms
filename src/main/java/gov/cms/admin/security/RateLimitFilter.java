package gov.cms.admin.security;

import gov.cms.admin.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ProxyManager<String> proxyManager;

    public RateLimitFilter(RateLimitProperties properties, ProxyManager<String> proxyManager) {
        this.properties = properties;
        this.proxyManager = proxyManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        RateLimitProperties.Rule matched = properties.getRules().stream()
                .filter(r -> uri.startsWith(r.getPath()))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = uri + ":" + request.getRemoteAddr();
        Duration refillPeriod = "SECONDS".equalsIgnoreCase(matched.getPeriod())
                ? Duration.ofSeconds(1)
                : Duration.ofMinutes(1);
        Bandwidth limit = Bandwidth.classic(matched.getCapacity(),
                Refill.intervally(matched.getRefill(), refillPeriod));
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit)
                .build();
        Bucket bucket = proxyManager.builder()
                .build(key, () -> configuration);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(refillPeriod.getSeconds()));
            response.getWriter().write("Too Many Requests");
        }
    }
}
