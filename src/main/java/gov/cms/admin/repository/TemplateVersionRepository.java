package gov.cms.admin.repository;

import gov.cms.admin.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    List<TemplateVersion> findByTemplateIdOrderByVersionNoDesc(Long templateId);

    Optional<TemplateVersion> findByTemplateIdAndVersionNo(Long templateId, Integer versionNo);

    Optional<TemplateVersion> findTopByTemplateIdOrderByVersionNoDesc(Long templateId);
}
