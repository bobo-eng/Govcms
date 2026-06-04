package gov.cms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.entity.PublishArtifact;
import gov.cms.admin.entity.PublishImpactItem;
import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.PublishArtifactRepository;
import gov.cms.admin.repository.PublishImpactItemRepository;
import gov.cms.admin.repository.PublishJobRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TopicRepository;
import gov.cms.admin.security.BouncyCastleGmCryptoService;
import gov.cms.admin.security.GmCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishExecutorSm3Test {

    @Mock private PublishJobRepository publishJobRepository;
    @Mock private PublishArtifactRepository publishArtifactRepository;
    @Mock private PublishImpactItemRepository publishImpactItemRepository;
    @Mock private PortalRenderService portalRenderService;
    @Mock private RenderContextAssembler renderContextAssembler;
    @Mock private ArticleService articleService;
    @Mock private SearchIndexService searchIndexService;
    @Mock private ArticleRepository articleRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateBindingRepository templateBindingRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void execute_producesSm3DigestWhenCryptoEnabled() throws Exception {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        ObjectMapper objectMapper = new ObjectMapper();

        PublishExecutor executor = new PublishExecutor(
                publishJobRepository, publishArtifactRepository, publishImpactItemRepository,
                portalRenderService, renderContextAssembler, articleService, searchIndexService,
                articleRepository, categoryRepository, templateRepository, templateBindingRepository,
                topicRepository, objectMapper, transactionManager, "./storage/publish",
                gmCryptoService, true
        );

        PublishJob job = new PublishJob();
        job.setId(1L);
        job.setSiteId(1L);
        job.setStatus("queued");
        job.setMode("incremental");
        job.setUnitType("site");
        job.setUnitIds("1");
        job.setSourceSnapshot("{\"siteId\":1,\"unitType\":\"site\",\"unitIds\":[1],\"mode\":\"incremental\"}");

        PublishImpactItem impact = new PublishImpactItem();
        impact.setId(10L);
        impact.setJobId(1L);
        impact.setPageType("home");
        impact.setPath("/index.html");
        impact.setSourceType("site");
        impact.setSourceId(1L);
        impact.setAction("publish");

        TemplateBinding binding = new TemplateBinding();
        binding.setTemplateId(100L);

        RenderContextSnapshot snapshot = new RenderContextSnapshot();
        var renderResult = new gov.cms.admin.dto.PortalRenderResult();
        renderResult.setRenderedHtml("<html><body>Hello</body></html>");

        when(publishJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(publishImpactItemRepository.findByJobIdOrderByIdAsc(1L)).thenReturn(List.of(impact));
        when(transactionManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.DefaultTransactionStatus(null, true, false, false, false, null));
        when(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(1L, "site", 1L, "site_home", "active"))
                .thenReturn(List.of(binding));
        when(renderContextAssembler.assemble(any(RenderRequest.class))).thenReturn(snapshot);
        when(portalRenderService.render(any())).thenReturn(renderResult);

        executor.execute(1L, "production");

        ArgumentCaptor<PublishArtifact> artifactCaptor = ArgumentCaptor.forClass(PublishArtifact.class);
        verify(publishArtifactRepository).save(artifactCaptor.capture());
        PublishArtifact artifact = artifactCaptor.getValue();
        assertThat(artifact.getSm3Digest()).isNotNull();
        assertThat(artifact.getSm3Digest()).hasSize(64);
    }
}
