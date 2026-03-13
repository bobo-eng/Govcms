package gov.cms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.admin.dto.PortalRenderResult;
import gov.cms.admin.dto.RenderContextSnapshot;
import gov.cms.admin.dto.RenderRequest;
import gov.cms.admin.dto.TemplateBindingRequest;
import gov.cms.admin.dto.TemplateImpactResponse;
import gov.cms.admin.dto.TemplatePreviewRequest;
import gov.cms.admin.dto.TemplatePreviewResponse;
import gov.cms.admin.dto.TemplateRequest;
import gov.cms.admin.dto.TemplateStatusUpdateRequest;
import gov.cms.admin.dto.TemplateVersionRequest;
import gov.cms.admin.dto.TemplateVersionRollbackRequest;
import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.Category;
import gov.cms.admin.entity.Site;
import gov.cms.admin.entity.Template;
import gov.cms.admin.entity.TemplateBinding;
import gov.cms.admin.entity.TemplateVersion;
import gov.cms.admin.repository.ArticleRepository;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.SiteRepository;
import gov.cms.admin.repository.TemplateBindingRepository;
import gov.cms.admin.repository.TemplateRepository;
import gov.cms.admin.repository.TemplateVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    private static final int MAX_SCHEMA_LENGTH = 200 * 1024;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final Set<String> ALLOWED_TYPES = Set.of("home", "column_list", "content_detail", "topic_page", "not_found");
    private static final Set<String> ALLOWED_STATUS = Set.of("draft", "active", "disabled");
    private static final Set<String> ALLOWED_BINDING_STATUS = Set.of("active", "inactive");
    private static final Set<String> ALLOWED_TARGET_TYPES = Set.of("site", "column", "topic", "content_rule");
    private static final Set<String> ALLOWED_BINDING_SLOTS = Set.of("site_home", "site_detail_default", "site_404", "column_list", "column_detail_default", "topic_page");
    private static final Set<String> ALLOWED_PREVIEW_SOURCES = Set.of("sample", "column", "content");
    private static final List<String> SCHEMA_BLACKLIST = List.of("script", "onerror", "onload", "javascript:");

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateBindingRepository templateBindingRepository;
    private final SiteRepository siteRepository;
    private final CategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;
    private final ObjectMapper objectMapper;
    private final RenderContextAssembler renderContextAssembler;
    private final PortalRenderService portalRenderService;

    public TemplateService(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateBindingRepository templateBindingRepository,
            SiteRepository siteRepository,
            CategoryRepository categoryRepository,
            ArticleRepository articleRepository,
            ObjectMapper objectMapper,
            RenderContextAssembler renderContextAssembler,
            PortalRenderService portalRenderService
    ) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateBindingRepository = templateBindingRepository;
        this.siteRepository = siteRepository;
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
        this.objectMapper = objectMapper;
        this.renderContextAssembler = renderContextAssembler;
        this.portalRenderService = portalRenderService;
    }

    @Transactional(readOnly = true)
    public List<Template> getTemplates(Long siteId, String type, String status, String keyword) {
        if (siteId != null) {
            ensureSiteExists(siteId);
        }
        return templateRepository.searchTemplates(siteId, normalizeType(type, true), normalizeStatus(status, true), normalizeText(keyword));
    }

    @Transactional(readOnly = true)
    public Template getTemplateById(Long id, Long siteId) {
        if (siteId != null) {
            return templateRepository.findByIdAndSiteId(id, siteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "?????"));
        }
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "?????"));
    }

    @Transactional
    public Template createTemplate(TemplateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘鏁版嵁涓嶈兘涓虹┖");
        }
        Long siteId = request.getSiteId();
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绔欑偣涓嶈兘涓虹┖");
        }
        ensureSiteExists(siteId);

        String name = normalizeRequiredText(request.getName(), "妯℃澘鍚嶇О涓嶈兘涓虹┖", 100);
        String code = normalizeCode(request.getCode(), "妯℃澘缂栫爜涓嶈兘涓虹┖");
        String type = normalizeType(request.getType(), false);
        String status = normalizeStatus(request.getStatus(), false);
        String description = normalizeText(request.getDescription());
        String defaultPreviewSource = normalizePreviewSource(request.getDefaultPreviewSource(), true);
        if (defaultPreviewSource == null) {
            defaultPreviewSource = "sample";
        }

        String layoutSchema = validateSchema(request.getLayoutSchema(), "甯冨眬缁撴瀯", true);
        String blockSchema = validateSchema(request.getBlockSchema(), "鍖哄潡閰嶇疆", true);
        String seoSchema = validateSchema(request.getSeoSchema(), "SEO 閰嶇疆", false);
        String styleSchema = validateSchema(request.getStyleSchema(), "鏍峰紡閰嶇疆", false);
        String changeLog = normalizeText(request.getChangeLog());

        if (templateRepository.existsBySiteIdAndCodeIgnoreCase(siteId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "???????");
        }

        String username = resolveUsername();
        Template template = new Template();
        template.setSiteId(siteId);
        template.setName(name);
        template.setCode(code);
        template.setType(type);
        template.setStatus(status);
        template.setDescription(description);
        template.setDefaultPreviewSource(defaultPreviewSource);
        template.setLatestVersionNo(1);
        template.setBindingCount(0);
        template.setCreatedBy(username);
        template.setUpdatedBy(username);
        Template savedTemplate = templateRepository.save(template);

        TemplateVersion version = buildVersion(savedTemplate.getId(), 1, layoutSchema, blockSchema, seoSchema, styleSchema, changeLog, username);
        TemplateVersion savedVersion = templateVersionRepository.save(version);
        savedTemplate.setCurrentVersionId(savedVersion.getId());
        savedTemplate.setLatestVersionNo(savedVersion.getVersionNo());
        return templateRepository.save(savedTemplate);
    }

    @Transactional
    public Template updateTemplate(Long id, TemplateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘鏁版嵁涓嶈兘涓虹┖");
        }
        Template template = getTemplateById(id, request.getSiteId());
        if (request.getSiteId() != null && !Objects.equals(template.getSiteId(), request.getSiteId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????");
        }

        String name = request.getName() != null ? normalizeRequiredText(request.getName(), "妯℃澘鍚嶇О涓嶈兘涓虹┖", 100) : template.getName();
        String code = request.getCode() != null ? normalizeCode(request.getCode(), "妯℃澘缂栫爜涓嶈兘涓虹┖") : template.getCode();
        if (!Objects.equals(code, template.getCode())
                && templateRepository.existsBySiteIdAndCodeIgnoreCase(template.getSiteId(), code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "???????");
        }

        if (request.getType() != null) {
            String requestedType = normalizeType(request.getType(), false);
            if (!Objects.equals(requestedType, template.getType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????????");
            }
        }

        if (request.getStatus() != null) {
            String requestedStatus = normalizeStatus(request.getStatus(), false);
            if ("disabled".equals(requestedStatus) && templateBindingRepository.countByTemplateIdAndStatus(template.getId(), "active") > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "妯℃澘宸茬粦瀹氱敓鏁堬紝鏃犳硶鍋滅敤");
            }
            template.setStatus(requestedStatus);
        }

        if (request.getDescription() != null) {
            template.setDescription(normalizeText(request.getDescription()));
        }
        if (request.getDefaultPreviewSource() != null) {
            template.setDefaultPreviewSource(normalizePreviewSource(request.getDefaultPreviewSource(), true));
        }

        template.setName(name);
        template.setCode(code);
        template.setUpdatedBy(resolveUsername());

        TemplateVersion currentVersion = resolveCurrentVersion(template);
        if (hasVersionPayload(request)) {
            TemplateVersionPayload payload = buildVersionPayload(request, currentVersion);
            if (payload.isChanged(currentVersion) || payload.changeLog != null) {
                int nextVersionNo = template.getLatestVersionNo() == null ? 1 : template.getLatestVersionNo() + 1;
                TemplateVersion version = buildVersion(
                        template.getId(),
                        nextVersionNo,
                        payload.layoutSchema,
                        payload.blockSchema,
                        payload.seoSchema,
                        payload.styleSchema,
                        payload.changeLog,
                        resolveUsername()
                );
                TemplateVersion savedVersion = templateVersionRepository.save(version);
                template.setCurrentVersionId(savedVersion.getId());
                template.setLatestVersionNo(savedVersion.getVersionNo());
            }
        }

        return templateRepository.save(template);
    }

    @Transactional
    public Template updateStatus(Long id, TemplateStatusUpdateRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绔欑偣涓嶈兘涓虹┖");
        }
        Template template = getTemplateById(id, request.getSiteId());
        String status = normalizeStatus(request.getStatus(), false);
        if ("disabled".equals(status) && templateBindingRepository.countByTemplateIdAndStatus(template.getId(), "active") > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "妯℃澘宸茬粦瀹氱敓鏁堬紝鏃犳硶鍋滅敤");
        }
        template.setStatus(status);
        template.setUpdatedBy(resolveUsername());
        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public List<TemplateVersion> getVersions(Long templateId, Long siteId) {
        Template template = getTemplateById(templateId, siteId);
        return templateVersionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId());
    }

    @Transactional
    public TemplateVersion createVersion(Long templateId, TemplateVersionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘鐗堟湰鏁版嵁涓嶈兘涓虹┖");
        }
        Template template = getTemplateById(templateId, null);
        TemplateVersionPayload payload = buildVersionPayload(request);
        int nextVersionNo = template.getLatestVersionNo() == null ? 1 : template.getLatestVersionNo() + 1;
        TemplateVersion version = buildVersion(
                template.getId(),
                nextVersionNo,
                payload.layoutSchema,
                payload.blockSchema,
                payload.seoSchema,
                payload.styleSchema,
                payload.changeLog,
                resolveUsername()
        );
        TemplateVersion saved = templateVersionRepository.save(version);
        template.setCurrentVersionId(saved.getId());
        template.setLatestVersionNo(saved.getVersionNo());
        template.setUpdatedBy(resolveUsername());
        templateRepository.save(template);
        return saved;
    }

    @Transactional
    public Template rollbackVersion(Long templateId, TemplateVersionRollbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍥炴粴淇℃伅涓嶈兘涓虹┖");
        }
        Template template = getTemplateById(templateId, request.getSiteId());
        TemplateVersion target = resolveRollbackTarget(templateId, request);
        int nextVersionNo = template.getLatestVersionNo() == null ? 1 : template.getLatestVersionNo() + 1;
        String changeLog = "鍥炴粴鑷崇増鏈?" + target.getVersionNo();
        TemplateVersion version = buildVersion(
                template.getId(),
                nextVersionNo,
                target.getLayoutSchema(),
                target.getBlockSchema(),
                target.getSeoSchema(),
                target.getStyleSchema(),
                changeLog,
                resolveUsername()
        );
        TemplateVersion savedVersion = templateVersionRepository.save(version);
        template.setCurrentVersionId(savedVersion.getId());
        template.setLatestVersionNo(savedVersion.getVersionNo());
        template.setUpdatedBy(resolveUsername());
        return templateRepository.save(template);
    }

    @Transactional
    public TemplateBinding createBinding(Long templateId, TemplateBindingRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缁戝畾鏁版嵁涓嶈兘涓虹┖");
        }
        if (request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绔欑偣涓嶈兘涓虹┖");
        }

        Template template = getTemplateById(templateId, request.getSiteId());
        if (!"active".equals(template.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "妯℃澘鏈惎鐢紝鏃犳硶缁戝畾");
        }

        String targetType = normalizeTargetType(request.getTargetType(), false);
        String bindingSlot = normalizeBindingSlot(request.getBindingSlot(), false);
        Long targetId = request.getTargetId();
        if (targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缁戝畾鐩爣涓嶈兘涓虹┖");
        }

        validateBindingMapping(template.getType(), targetType, bindingSlot);
        validateBindingTarget(request.getSiteId(), targetType, targetId);

        if (request.getTemplateVersionId() != null) {
            TemplateVersion version = templateVersionRepository.findById(request.getTemplateVersionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "???????"));
            if (!Objects.equals(version.getTemplateId(), template.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "???????????");
            }
        }

        List<TemplateBinding> existing = Optional.ofNullable(templateBindingRepository
                .findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
                        request.getSiteId(),
                        targetType,
                        targetId,
                        bindingSlot,
                        "active"
                )).orElse(List.of());
        boolean conflict = !existing.isEmpty();
        boolean replaceExisting = Boolean.TRUE.equals(request.getReplaceExisting());
        if (conflict && !replaceExisting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "???????????");
        }

        Set<Long> affectedTemplateIds = new java.util.LinkedHashSet<>();
        if (conflict) {
            String username = resolveUsername();
            existing.forEach(binding -> {
                binding.setStatus("inactive");
                binding.setUpdatedBy(username);
                affectedTemplateIds.add(binding.getTemplateId());
            });
            templateBindingRepository.saveAll(existing);
        }

        TemplateBinding binding = new TemplateBinding();
        binding.setSiteId(request.getSiteId());
        binding.setTemplateId(template.getId());
        binding.setTemplateVersionId(request.getTemplateVersionId());
        binding.setTargetType(targetType);
        binding.setTargetId(targetId);
        binding.setBindingSlot(bindingSlot);
        binding.setStatus("active");
        String username = resolveUsername();
        binding.setCreatedBy(username);
        binding.setUpdatedBy(username);
        TemplateBinding saved = templateBindingRepository.save(binding);
        affectedTemplateIds.add(template.getId());
        refreshBindingCounts(affectedTemplateIds);
        syncCategoryTemplateReference(saved, template.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TemplateBinding> getBindings(Long templateId, Long siteId, String targetType, String status) {
        Template template = getTemplateById(templateId, siteId);
        return templateBindingRepository.searchBindings(
                template.getId(),
                siteId,
                normalizeTargetType(targetType, true),
                normalizeBindingStatus(status, true)
        );
    }

    @Transactional
    public void deleteBinding(Long bindingId, Long siteId) {
        if (siteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绔欑偣涓嶈兘涓虹┖");
        }
        TemplateBinding binding = templateBindingRepository.findByIdAndSiteId(bindingId, siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "???????"));
        binding.setStatus("inactive");
        binding.setUpdatedBy(resolveUsername());
        TemplateBinding saved = templateBindingRepository.save(binding);

        Long activeTemplateId = resolveActiveCategoryTemplateId(siteId, saved.getTargetType(), saved.getTargetId(), saved.getBindingSlot());
        syncCategoryTemplateReference(saved, activeTemplateId);

        Set<Long> affectedTemplateIds = new java.util.LinkedHashSet<>();
        affectedTemplateIds.add(saved.getTemplateId());
        if (activeTemplateId != null) {
            affectedTemplateIds.add(activeTemplateId);
        }
        refreshBindingCounts(affectedTemplateIds);
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResponse previewTemplate(Long templateId, TemplatePreviewRequest request) {
        if (request == null || request.getSiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId is required for preview.");
        }

        RenderRequest renderRequest = new RenderRequest();
        renderRequest.setSiteId(request.getSiteId());
        renderRequest.setTemplateId(templateId);
        renderRequest.setSourceType(request.getSourceType());
        renderRequest.setSourceId(request.getSourceId());
        renderRequest.setMode("preview");

        RenderContextSnapshot snapshot = renderContextAssembler.assemble(renderRequest);
        PortalRenderResult renderResult = portalRenderService.render(snapshot);

        TemplatePreviewResponse response = new TemplatePreviewResponse();
        response.setTemplateId(snapshot.getTemplateId());
        response.setTemplateName(snapshot.getTemplateName());
        response.setTemplateVersionId(snapshot.getTemplateVersionId());
        response.setTemplateType(snapshot.getTemplateType());
        response.setVersionNo(snapshot.getVersionNo());
        response.setPageType(snapshot.getPageType());
        response.setSourceType(snapshot.getSourceType());
        response.setSourceId(snapshot.getSourceId());
        response.setLayoutSchema(snapshot.getLayoutSchema());
        response.setBlockSchema(snapshot.getBlockSchema());
        response.setSeoSchema(snapshot.getSeoSchema());
        response.setStyleSchema(snapshot.getStyleSchema());
        response.setContext(snapshot.getContext());
        response.setSummary(snapshot.getSummary());
        response.setWarnings(snapshot.getWarnings());
        response.setRenderedHtml(renderResult.getRenderedHtml());
        response.setRenderEngine(renderResult.getRenderEngine());
        response.setRenderTemplateName(renderResult.getRenderTemplateName());
        response.setMessage("Preview render completed.");
        return response;
    }

    @Transactional(readOnly = true)
    public TemplateImpactResponse getImpact(Long templateId, Long siteId) {
        Template template = getTemplateById(templateId, siteId);
        List<TemplateBinding> bindings = templateBindingRepository.findByTemplateIdAndStatus(template.getId(), "active");

        TemplateImpactResponse response = new TemplateImpactResponse();
        response.setTemplateId(template.getId());
        response.setTemplateName(template.getName());
        response.setTemplateType(template.getType());
        response.setActiveBindingCount(bindings.size());
        Map<String, Long> counts = bindings.stream()
                .collect(Collectors.groupingBy(TemplateBinding::getTargetType, LinkedHashMap::new, Collectors.counting()));
        response.setTargetTypeCounts(counts);
        response.setSampleTargets(bindings.stream()
                .map(binding -> binding.getTargetType() + ":" + binding.getTargetId() + "@" + binding.getBindingSlot())
                .limit(8)
                .collect(Collectors.toList()));

        List<String> warnings = new ArrayList<>();
        if (!bindings.isEmpty()) {
            warnings.add("Template has " + bindings.size() + " active bindings; review publishing impact before changes.");
        }
        if ("disabled".equals(template.getStatus())) {
            warnings.add("Template is disabled; active bindings are not effective.");
        }
        response.setWarnings(warnings);
        return response;
    }

    private TemplateVersion resolveCurrentVersion(Template template) {
        if (template.getCurrentVersionId() != null) {
            return templateVersionRepository.findById(template.getCurrentVersionId())
                    .orElseGet(() -> templateVersionRepository.findTopByTemplateIdOrderByVersionNoDesc(template.getId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "???????")));
        }
        return templateVersionRepository.findTopByTemplateIdOrderByVersionNoDesc(template.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "???????"));
    }

    private TemplateVersion resolveRollbackTarget(Long templateId, TemplateVersionRollbackRequest request) {
        if (request.getVersionId() != null) {
            TemplateVersion version = templateVersionRepository.findById(request.getVersionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "???????"));
            if (!Objects.equals(version.getTemplateId(), templateId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "???????????");
            }
            return version;
        }
        if (request.getVersionNo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "鍥炴粴鐗堟湰涓嶈兘涓虹┖");
        }
        return templateVersionRepository.findByTemplateIdAndVersionNo(templateId, request.getVersionNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "???????"));
    }

    private TemplateVersionPayload buildVersionPayload(TemplateRequest request, TemplateVersion currentVersion) {
        String layoutSchema = request.getLayoutSchema() != null
                ? validateSchema(request.getLayoutSchema(), "甯冨眬缁撴瀯", true)
                : currentVersion.getLayoutSchema();
        String blockSchema = request.getBlockSchema() != null
                ? validateSchema(request.getBlockSchema(), "鍖哄潡閰嶇疆", true)
                : currentVersion.getBlockSchema();
        if (layoutSchema == null || blockSchema == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘缁撴瀯涓嶈兘涓虹┖");
        }
        String seoSchema = request.getSeoSchema() != null
                ? validateSchema(request.getSeoSchema(), "SEO 閰嶇疆", false)
                : currentVersion.getSeoSchema();
        String styleSchema = request.getStyleSchema() != null
                ? validateSchema(request.getStyleSchema(), "鏍峰紡閰嶇疆", false)
                : currentVersion.getStyleSchema();
        String changeLog = normalizeText(request.getChangeLog());
        return new TemplateVersionPayload(layoutSchema, blockSchema, seoSchema, styleSchema, changeLog);
    }

    private TemplateVersionPayload buildVersionPayload(TemplateVersionRequest request) {
        String layoutSchema = validateSchema(request.getLayoutSchema(), "甯冨眬缁撴瀯", true);
        String blockSchema = validateSchema(request.getBlockSchema(), "鍖哄潡閰嶇疆", true);
        String seoSchema = validateSchema(request.getSeoSchema(), "SEO 閰嶇疆", false);
        String styleSchema = validateSchema(request.getStyleSchema(), "鏍峰紡閰嶇疆", false);
        String changeLog = normalizeText(request.getChangeLog());
        return new TemplateVersionPayload(layoutSchema, blockSchema, seoSchema, styleSchema, changeLog);
    }

    private boolean hasVersionPayload(TemplateRequest request) {
        return request.getLayoutSchema() != null
                || request.getBlockSchema() != null
                || request.getSeoSchema() != null
                || request.getStyleSchema() != null
                || request.getChangeLog() != null;
    }

    private TemplateVersion buildVersion(Long templateId,
                                         int versionNo,
                                         String layoutSchema,
                                         String blockSchema,
                                         String seoSchema,
                                         String styleSchema,
                                         String changeLog,
                                         String username) {
        TemplateVersion version = new TemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setLayoutSchema(layoutSchema);
        version.setBlockSchema(blockSchema);
        version.setSeoSchema(seoSchema);
        version.setStyleSchema(styleSchema);
        version.setChangeLog(changeLog);
        version.setCreatedBy(username);
        return version;
    }

    private void validateBindingMapping(String templateType, String targetType, String bindingSlot) {
        if ("home".equals(templateType) && "site".equals(targetType) && "site_home".equals(bindingSlot)) {
            return;
        }
        if ("not_found".equals(templateType) && "site".equals(targetType) && "site_404".equals(bindingSlot)) {
            return;
        }
        if ("column_list".equals(templateType) && "column".equals(targetType) && "column_list".equals(bindingSlot)) {
            return;
        }
        if ("topic_page".equals(templateType) && "topic".equals(targetType) && "topic_page".equals(bindingSlot)) {
            return;
        }
        if ("content_detail".equals(templateType)) {
            if ("site".equals(targetType) && "site_detail_default".equals(bindingSlot)) {
                return;
            }
            if ("column".equals(targetType) && "column_detail_default".equals(bindingSlot)) {
                return;
            }
            if ("content_rule".equals(targetType) && "column_detail_default".equals(bindingSlot)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘绫诲瀷涓庣粦瀹氭Ы浣嶄笉鍖归厤");
    }

    private void validateBindingTarget(Long siteId, String targetType, Long targetId) {
        if ("site".equals(targetType)) {
            if (!Objects.equals(siteId, targetId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绔欑偣妯℃澘鍙兘缁戝畾褰撳墠绔欑偣");
            }
            ensureSiteExists(siteId);
            return;
        }
        if ("column".equals(targetType)) {
            Category category = categoryRepository.findByIdAndSiteId(targetId, siteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????"));
            if (!Objects.equals(category.getSiteId(), siteId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "????????");
            }
        }
    }

    private void refreshBindingCount(Template template) {
        long activeCount = templateBindingRepository.countByTemplateIdAndStatus(template.getId(), "active");
        template.setBindingCount((int) activeCount);
        template.setUpdatedBy(resolveUsername());
        templateRepository.save(template);
    }

    private void refreshBindingCounts(Set<Long> templateIds) {
        templateIds.stream()
                .filter(Objects::nonNull)
                .forEach(templateId -> templateRepository.findById(templateId).ifPresent(this::refreshBindingCount));
    }

    private void syncCategoryTemplateReference(TemplateBinding binding, Long templateId) {
        if (!"column".equals(binding.getTargetType())) {
            return;
        }
        Category category = categoryRepository.findByIdAndSiteId(binding.getTargetId(), binding.getSiteId()).orElse(null);
        if (category == null) {
            return;
        }
        if ("column_list".equals(binding.getBindingSlot())) {
            category.setListTemplateId(templateId);
        } else if ("column_detail_default".equals(binding.getBindingSlot())) {
            category.setDetailTemplateId(templateId);
        } else {
            return;
        }
        categoryRepository.save(category);
    }

    private Long resolveActiveCategoryTemplateId(Long siteId, String targetType, Long targetId, String bindingSlot) {
        List<TemplateBinding> bindings = Optional.ofNullable(templateBindingRepository.findBySiteIdAndTargetTypeAndTargetIdAndBindingSlotAndStatus(
                siteId,
                targetType,
                targetId,
                bindingSlot,
                "active"
        )).orElse(List.of());
        return bindings.stream().findFirst().map(TemplateBinding::getTemplateId).orElse(null);
    }

    private Map<String, Object> buildSiteContext(Site site) {
        Map<String, Object> siteContext = new LinkedHashMap<>();
        siteContext.put("id", site.getId());
        siteContext.put("name", site.getName());
        siteContext.put("code", site.getCode());
        siteContext.put("domain", site.getDomain());
        siteContext.put("organizationId", site.getOrganizationId());
        siteContext.put("status", site.getStatus());
        return siteContext;
    }

    private Map<String, Object> buildNavigationContext(Long siteId, Category previewCategory) {
        List<Category> categories = Optional.ofNullable(categoryRepository.findBySiteIdOrderBySortOrderAscIdAsc(siteId)).orElse(List.of());
        List<Map<String, Object>> navigation = categories.stream()
                .filter(category -> "enabled".equalsIgnoreCase(category.getStatus()))
                .filter(category -> Boolean.TRUE.equals(category.getNavVisible()))
                .filter(category -> category.getParentId() == null)
                .limit(12)
                .map(category -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", category.getName());
                    item.put("path", category.getFullPath());
                    item.put("slug", category.getSlug());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> navigationContext = new LinkedHashMap<>();
        navigationContext.put("primaryNavigation", navigation);
        navigationContext.put("breadcrumb", buildBreadcrumb(previewCategory));
        return navigationContext;
    }

    private List<Map<String, Object>> buildBreadcrumb(Category previewCategory) {
        if (previewCategory == null || previewCategory.getFullPath() == null) {
            return List.of();
        }
        String[] segments = previewCategory.getFullPath().split("/");
        List<Map<String, Object>> breadcrumb = new ArrayList<>();
        StringBuilder currentPath = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            currentPath.append('/').append(segment);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", segment);
            item.put("path", currentPath.toString());
            breadcrumb.add(item);
        }
        return breadcrumb;
    }

    private Map<String, Object> buildColumnContext(Category previewCategory, String sourceType) {
        Map<String, Object> columnContext = new LinkedHashMap<>();
        columnContext.put("sourceType", sourceType);
        if (previewCategory == null) {
            columnContext.put("available", false);
            columnContext.put("message", "褰撳墠棰勮鏈姞杞芥爮鐩笂涓嬫枃");
            return columnContext;
        }
        columnContext.put("available", true);
        columnContext.put("id", previewCategory.getId());
        columnContext.put("name", previewCategory.getName());
        columnContext.put("code", previewCategory.getCode());
        columnContext.put("slug", previewCategory.getSlug());
        columnContext.put("fullPath", previewCategory.getFullPath());
        columnContext.put("level", previewCategory.getLevel());
        columnContext.put("aggregationMode", previewCategory.getAggregationMode());
        return columnContext;
    }

    private Map<String, Object> buildContentContext(Article previewArticle, String sourceType) {
        Map<String, Object> contentContext = new LinkedHashMap<>();
        contentContext.put("sourceType", sourceType);
        if (previewArticle == null) {
            contentContext.put("available", false);
            contentContext.put("message", "褰撳墠棰勮鏈姞杞藉唴瀹逛笂涓嬫枃");
            return contentContext;
        }
        contentContext.put("available", true);
        contentContext.put("id", previewArticle.getId());
        contentContext.put("title", previewArticle.getTitle());
        contentContext.put("summary", previewArticle.getSummary());
        contentContext.put("category", previewArticle.getCategory());
        contentContext.put("author", previewArticle.getAuthor());
        contentContext.put("status", previewArticle.getStatus());
        contentContext.put("views", previewArticle.getViews());
        contentContext.put("primaryCategoryId", previewArticle.getPrimaryCategoryId());
        return contentContext;
    }

    private Map<String, Object> buildTopicContext() {
        Map<String, Object> topicContext = new LinkedHashMap<>();
        topicContext.put("available", false);
        topicContext.put("supported", false);
        topicContext.put("message", "??????????????????????");
        return topicContext;
    }

    private Map<String, Object> buildRenderMeta(Template template,
                                                TemplateVersion version,
                                                String sourceType,
                                                Long sourceId,
                                                String pageType,
                                                boolean publishReady) {
        Map<String, Object> renderMeta = new LinkedHashMap<>();
        renderMeta.put("generatedAt", LocalDateTime.now().toString());
        renderMeta.put("mode", "template-preview");
        renderMeta.put("contractStage", "render-context-ready");
        renderMeta.put("templateId", template.getId());
        renderMeta.put("templateVersionId", version.getId());
        renderMeta.put("templateVersionNo", version.getVersionNo());
        renderMeta.put("templateStatus", template.getStatus());
        renderMeta.put("pageType", pageType);
        renderMeta.put("sourceType", sourceType);
        renderMeta.put("sourceId", sourceId);
        renderMeta.put("publishReady", publishReady);
        return renderMeta;
    }

    private String resolvePageType(String templateType) {
        return switch (templateType) {
            case "home" -> "home";
            case "column_list" -> "column-list";
            case "content_detail" -> "content-detail";
            case "topic_page" -> "topic-page";
            case "not_found" -> "error-404";
            default -> templateType;
        };
    }

    private Category buildSampleCategory(Long siteId) {
        Category category = new Category();
        category.setId(0L);
        category.setSiteId(siteId);
        category.setName("绀轰緥鏍忕洰");
        category.setCode("sample-column");
        category.setSlug("sample-column");
        category.setFullPath("/sample-column");
        category.setLevel(1);
        category.setAggregationMode("manual");
        category.setStatus("enabled");
        category.setNavVisible(true);
        category.setBreadcrumbVisible(true);
        category.setPublicVisible(true);
        return category;
    }

    private Article buildSampleArticle(Long siteId, Long categoryId) {
        Article article = new Article();
        article.setId(0L);
        article.setSiteId(siteId);
        article.setPrimaryCategoryId(categoryId);
        article.setTitle("绀轰緥鍐呭鏍囬");
        article.setSummary("???????????????");
        article.setCategory("绀轰緥鏍忕洰");
        article.setAuthor("system");
        article.setStatus("published");
        article.setViews(128);
        return article;
    }

    private void ensureSiteExists(Long siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????");
        }
    }

    private String normalizeRequiredText(String value, String message, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeCode(String code, String message) {
        String normalized = normalizeText(code);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????????");
        }
        return normalized;
    }

    private String normalizeType(String type, boolean allowNull) {
        String normalized = normalizeText(type);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘绫诲瀷涓嶈兘涓虹┖");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "???????");
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean allowNull) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            return "draft";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "妯℃澘鐘舵€佷笉姝ｇ‘");
        }
        return normalized;
    }

    private String normalizeBindingStatus(String status, boolean allowNull) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return allowNull ? null : "active";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_BINDING_STATUS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缁戝畾鐘舵€佷笉姝ｇ‘");
        }
        return normalized;
    }

    private String normalizeTargetType(String targetType, boolean allowNull) {
        String normalized = normalizeText(targetType);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缁戝畾鐩爣绫诲瀷涓嶈兘涓虹┖");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_TARGET_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "?????????");
        }
        return normalized;
    }

    private String normalizeBindingSlot(String bindingSlot, boolean allowNull) {
        String normalized = normalizeText(bindingSlot);
        if (normalized == null) {
            if (allowNull) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缁戝畾妲戒綅涓嶈兘涓虹┖");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_BINDING_SLOTS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "???????");
        }
        return normalized;
    }

    private String normalizePreviewSource(String source, boolean allowNull) {
        String normalized = normalizeText(source);
        if (normalized == null) {
            return allowNull ? null : "sample";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ALLOWED_PREVIEW_SOURCES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "棰勮鏁版嵁婧愪笉姝ｇ‘");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String validateSchema(String schema, String label, boolean required) {
        if (schema == null) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "涓嶈兘涓虹┖");
            }
            return null;
        }
        String normalized = schema.trim();
        if (normalized.isEmpty()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "涓嶈兘涓虹┖");
            }
            return null;
        }
        if (normalized.length() > MAX_SCHEMA_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "鍐呭杩囬暱");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String keyword : SCHEMA_BLACKLIST) {
            if (lower.contains(keyword)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "???????");
            }
        }
        try {
            objectMapper.readTree(normalized);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "涓嶆槸鍚堟硶 JSON");
        }
        return normalized;
    }

    private String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "system";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return "system";
        }
        return name;
    }

    private static class TemplateVersionPayload {
        private final String layoutSchema;
        private final String blockSchema;
        private final String seoSchema;
        private final String styleSchema;
        private final String changeLog;

        private TemplateVersionPayload(String layoutSchema, String blockSchema, String seoSchema, String styleSchema, String changeLog) {
            this.layoutSchema = layoutSchema;
            this.blockSchema = blockSchema;
            this.seoSchema = seoSchema;
            this.styleSchema = styleSchema;
            this.changeLog = changeLog;
        }

        private boolean isChanged(TemplateVersion currentVersion) {
            return !Objects.equals(layoutSchema, currentVersion.getLayoutSchema())
                    || !Objects.equals(blockSchema, currentVersion.getBlockSchema())
                    || !Objects.equals(seoSchema, currentVersion.getSeoSchema())
                    || !Objects.equals(styleSchema, currentVersion.getStyleSchema());
        }
    }
}


