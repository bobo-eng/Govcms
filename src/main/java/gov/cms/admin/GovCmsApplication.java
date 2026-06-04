package gov.cms.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class GovCmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovCmsApplication.class, args);
    }
}
