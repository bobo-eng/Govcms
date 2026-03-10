package gov.cms.admin.service;

import gov.cms.admin.entity.MediaFile;
import org.springframework.core.io.Resource;

public record MediaPreviewResource(MediaFile mediaFile, Resource resource) {
}