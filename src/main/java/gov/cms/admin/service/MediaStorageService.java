package gov.cms.admin.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface MediaStorageService {

    StoredMediaObject store(MultipartFile file);

    Resource load(String storagePath);

    void delete(String storagePath);
}