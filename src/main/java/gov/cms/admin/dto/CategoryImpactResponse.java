package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class CategoryImpactResponse {

    private Long categoryId;
    private String categoryName;
    private String fullPath;
    private Integer subtreeCount;
    private Long relatedArticleCount;
    private Boolean canDelete;
    private Boolean canMove;
    private List<String> impactedPaths = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public Integer getSubtreeCount() { return subtreeCount; }
    public void setSubtreeCount(Integer subtreeCount) { this.subtreeCount = subtreeCount; }
    public Long getRelatedArticleCount() { return relatedArticleCount; }
    public void setRelatedArticleCount(Long relatedArticleCount) { this.relatedArticleCount = relatedArticleCount; }
    public Boolean getCanDelete() { return canDelete; }
    public void setCanDelete(Boolean canDelete) { this.canDelete = canDelete; }
    public Boolean getCanMove() { return canMove; }
    public void setCanMove(Boolean canMove) { this.canMove = canMove; }
    public List<String> getImpactedPaths() { return impactedPaths; }
    public void setImpactedPaths(List<String> impactedPaths) { this.impactedPaths = impactedPaths; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
