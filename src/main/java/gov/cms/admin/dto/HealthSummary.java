package gov.cms.admin.dto;

public class HealthSummary {

    private String db;
    private String redis;
    private String hibernateSearch;
    private String quartz;

    public String getDb() { return db; }
    public void setDb(String db) { this.db = db; }

    public String getRedis() { return redis; }
    public void setRedis(String redis) { this.redis = redis; }

    public String getHibernateSearch() { return hibernateSearch; }
    public void setHibernateSearch(String hibernateSearch) { this.hibernateSearch = hibernateSearch; }

    public String getQuartz() { return quartz; }
    public void setQuartz(String quartz) { this.quartz = quartz; }
}
