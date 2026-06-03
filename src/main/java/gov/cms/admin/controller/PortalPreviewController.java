package gov.cms.admin.controller;

import gov.cms.admin.entity.PublishJob;
import gov.cms.admin.repository.PublishJobRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class PortalPreviewController {

    private final PublishJobRepository publishJobRepository;

    public PortalPreviewController(PublishJobRepository publishJobRepository) {
        this.publishJobRepository = publishJobRepository;
    }

    @GetMapping("/preview/{token}")
    public void preview(@PathVariable String token, HttpServletResponse response) throws IOException {
        Optional<PublishJob> optionalJob = publishJobRepository.findByPreviewToken(token);
        if (optionalJob.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        PublishJob job = optionalJob.get();
        String outputRoot = job.getOutputRoot();
        if (outputRoot == null || outputRoot.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path indexPath = Paths.get(outputRoot, "index.html");
        if (!Files.exists(indexPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        response.setContentType("text/html;charset=UTF-8");
        Files.copy(indexPath, response.getOutputStream());
        response.getOutputStream().flush();
    }
}
