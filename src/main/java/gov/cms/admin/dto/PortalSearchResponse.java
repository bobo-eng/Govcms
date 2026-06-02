package gov.cms.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class PortalSearchResponse {
    private long total;
    private int page;
    private int size;
    private List<PortalSearchItem> items = new ArrayList<>();

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<PortalSearchItem> getItems() { return items; }
    public void setItems(List<PortalSearchItem> items) { this.items = items; }
}
