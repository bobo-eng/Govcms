package gov.cms.admin.dto;

public record ArtifactVerifyResponse(
    Long artifactId,
    String status,
    String expected,
    String actual
) {}
