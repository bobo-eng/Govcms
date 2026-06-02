package gov.cms.admin.service;

import gov.cms.admin.dto.NavigationItemRequest;
import gov.cms.admin.dto.NavigationItemSortRequest;
import gov.cms.admin.entity.NavigationItem;
import gov.cms.admin.repository.CategoryRepository;
import gov.cms.admin.repository.NavigationItemRepository;
import gov.cms.admin.repository.TopicRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NavigationService {

    private final NavigationItemRepository navigationItemRepository;
    private final CategoryRepository categoryRepository;
    private final TopicRepository topicRepository;
    private final SiteAccessService siteAccessService;

    public NavigationService(NavigationItemRepository navigationItemRepository,
                             CategoryRepository categoryRepository,
                             TopicRepository topicRepository,
                             SiteAccessService siteAccessService) {
        this.navigationItemRepository = navigationItemRepository;
        this.categoryRepository = categoryRepository;
        this.topicRepository = topicRepository;
        this.siteAccessService = siteAccessService;
    }

    public List<NavigationItem> getNavigationItems(Long siteId, String keyword, String status) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        return navigationItemRepository.search(accessibleSiteId, keyword, normalize(status));
    }

    public NavigationItem getNavigationItemById(Long id, Long siteId) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(siteId);
        return navigationItemRepository.findByIdAndSiteId(id, accessibleSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Navigation item not found."));
    }

    @Transactional
    public NavigationItem createNavigationItem(NavigationItemRequest request) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(request.getSiteId());
        NavigationItem item = new NavigationItem();
        applyPayload(item, request, accessibleSiteId, null);
        return navigationItemRepository.save(item);
    }

    @Transactional
    public NavigationItem updateNavigationItem(Long id, NavigationItemRequest request) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(request.getSiteId());
        NavigationItem item = navigationItemRepository.findByIdAndSiteId(id, accessibleSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Navigation item not found."));
        applyPayload(item, request, accessibleSiteId, id);
        return navigationItemRepository.save(item);
    }

    @Transactional
    public NavigationItem updateSort(Long id, NavigationItemSortRequest request) {
        Long accessibleSiteId = siteAccessService.resolveAccessibleSiteId(request.getSiteId());
        NavigationItem item = navigationItemRepository.findByIdAndSiteId(id, accessibleSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Navigation item not found."));
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return navigationItemRepository.save(item);
    }

    @Transactional
    public void deleteNavigationItem(Long id, Long siteId) {
        NavigationItem item = getNavigationItemById(id, siteId);
        navigationItemRepository.delete(item);
    }

    private void applyPayload(NavigationItem item, NavigationItemRequest request, Long siteId, Long existingId) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation payload is required.");
        }
        String name = required(request.getName(), "Navigation name is required.");
        String code = required(request.getCode(), "Navigation code is required.").toLowerCase(Locale.ROOT);
        String targetType = required(request.getTargetType(), "Navigation targetType is required.").toLowerCase(Locale.ROOT);
        if (!Set.of("category", "topic", "external_link", "custom_page").contains(targetType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported navigation target type.");
        }
        if (navigationItemRepository.existsBySiteIdAndCodeIgnoreCase(siteId, code) && existingId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Navigation code already exists.");
        }
        if (existingId != null && navigationItemRepository.existsBySiteIdAndCodeIgnoreCaseAndIdNot(siteId, code, existingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Navigation code already exists.");
        }

        Long parentId = request.getParentId();
        if (parentId != null) {
            NavigationItem parent = navigationItemRepository.findByIdAndSiteId(parentId, siteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation parent does not exist."));
            if (existingId != null && parent.getId().equals(existingId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation item cannot be its own parent.");
            }
        }

        Long targetId = request.getTargetId();
        String targetValue = normalize(request.getTargetValue());
        if (Set.of("category", "topic").contains(targetType)) {
            if (targetId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation targetId is required.");
            }
            if ("category".equals(targetType) && categoryRepository.findByIdAndSiteId(targetId, siteId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation category target does not exist in site.");
            }
            if ("topic".equals(targetType) && topicRepository.findByIdAndSiteId(targetId, siteId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation topic target does not exist in site.");
            }
            targetValue = null;
        } else {
            if (targetValue == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Navigation targetValue is required.");
            }
            targetId = null;
        }

        item.setSiteId(siteId);
        item.setParentId(parentId);
        item.setName(name);
        item.setCode(code);
        item.setTargetType(targetType);
        item.setTargetId(targetId);
        item.setTargetValue(targetValue);
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        item.setStatus(normalize(request.getStatus()) == null ? "enabled" : normalize(request.getStatus()));
        item.setPrimaryNav(request.getPrimaryNav() == null ? Boolean.TRUE : request.getPrimaryNav());
        item.setBreadcrumbEnabled(request.getBreadcrumbEnabled() == null ? Boolean.TRUE : request.getBreadcrumbEnabled());
    }

    private String required(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
