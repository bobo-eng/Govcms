package gov.cms.admin.config;

import gov.cms.admin.entity.Article;
import gov.cms.admin.entity.ArticleStatus;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@Order(100)
public class NanjingDemoInitializer {

    private static final String SITE_CODE = "nanjing-demo";

    @Bean
    @Transactional
    public CommandLineRunner initNanjingDemo(
            SiteRepository siteRepository,
            CategoryRepository categoryRepository,
            ArticleRepository articleRepository,
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateBindingRepository templateBindingRepository
    ) {
        return args -> {
            if (siteRepository.existsByCodeIgnoreCase(SITE_CODE)) {
                return;
            }

            Site site = createSite(siteRepository);
            List<Category> categories = createCategories(categoryRepository, site.getId());
            createArticles(articleRepository, site.getId(), categories);

            Template homeTemplate = createHomeTemplate(templateRepository, site.getId());
            Template columnTemplate = createColumnTemplate(templateRepository, site.getId());
            Template detailTemplate = createDetailTemplate(templateRepository, site.getId());

            createTemplateVersions(templateRepository, templateVersionRepository, homeTemplate, columnTemplate, detailTemplate);
            createBindings(templateBindingRepository, templateRepository, site.getId(), homeTemplate, columnTemplate, detailTemplate, categories);
            updateCategoryTemplates(categoryRepository, categories, columnTemplate, detailTemplate);
        };
    }

    private Site createSite(SiteRepository siteRepository) {
        Site site = new Site();
        site.setName("南京政务服务示范站");
        site.setCode(SITE_CODE);
        site.setDomain("nanjing-demo.govcms.local");
        site.setDescription("南京市政务服务网上办事大厅，提供政务公开、办事服务、政策文件、互动交流、数据开放等一站式政务服务。");
        site.setStatus("enabled");
        return siteRepository.save(site);
    }

    private List<Category> createCategories(CategoryRepository categoryRepository, Long siteId) {
        Category cat1 = new Category();
        cat1.setSiteId(siteId);
        cat1.setName("政务公开");
        cat1.setCode("zhengwugongkai");
        cat1.setSlug("zhengwugongkai");
        cat1.setFullPath("/zhengwugongkai");
        cat1.setLevel(1);
        cat1.setSortOrder(1);
        cat1.setStatus("enabled");
        cat1.setNavVisible(true);
        cat1.setBreadcrumbVisible(true);
        cat1.setPublicVisible(true);
        cat1.setDescription("政府信息公开、机构职能、领导信息、人事信息、财政信息等。");

        Category cat2 = new Category();
        cat2.setSiteId(siteId);
        cat2.setName("办事服务");
        cat2.setCode("banshi");
        cat2.setSlug("banshi");
        cat2.setFullPath("/banshi");
        cat2.setLevel(1);
        cat2.setSortOrder(2);
        cat2.setStatus("enabled");
        cat2.setNavVisible(true);
        cat2.setBreadcrumbVisible(true);
        cat2.setPublicVisible(true);
        cat2.setDescription("个人办事、法人办事、部门服务、主题集成服务、一件事一次办等。");

        Category cat3 = new Category();
        cat3.setSiteId(siteId);
        cat3.setName("政策文件");
        cat3.setCode("zhengce");
        cat3.setSlug("zhengce");
        cat3.setFullPath("/zhengce");
        cat3.setLevel(1);
        cat3.setSortOrder(3);
        cat3.setStatus("enabled");
        cat3.setNavVisible(true);
        cat3.setBreadcrumbVisible(true);
        cat3.setPublicVisible(true);
        cat3.setDescription("市政府及各部门规范性文件、政策解读、法律法规等。");

        Category cat4 = new Category();
        cat4.setSiteId(siteId);
        cat4.setName("互动交流");
        cat4.setCode("hudong");
        cat4.setSlug("hudong");
        cat4.setFullPath("/hudong");
        cat4.setLevel(1);
        cat4.setSortOrder(4);
        cat4.setStatus("enabled");
        cat4.setNavVisible(true);
        cat4.setBreadcrumbVisible(true);
        cat4.setPublicVisible(true);
        cat4.setDescription("市长信箱、在线访谈、调查征集、投诉建议等政民互动渠道。");

        Category cat5 = new Category();
        cat5.setSiteId(siteId);
        cat5.setName("数据开放");
        cat5.setCode("shuju");
        cat5.setSlug("shuju");
        cat5.setFullPath("/shuju");
        cat5.setLevel(1);
        cat5.setSortOrder(5);
        cat5.setStatus("enabled");
        cat5.setNavVisible(true);
        cat5.setBreadcrumbVisible(true);
        cat5.setPublicVisible(true);
        cat5.setDescription("经济运行、社会民生、城市建设、生态环境等政务数据开放。");

        return categoryRepository.saveAll(List.of(cat1, cat2, cat3, cat4, cat5));
    }

    private void createArticles(ArticleRepository articleRepository, Long siteId, List<Category> categories) {
        Category cat1 = categories.get(0);
        Category cat2 = categories.get(1);
        Category cat3 = categories.get(2);
        Category cat4 = categories.get(3);
        Category cat5 = categories.get(4);

        Article article1 = new Article();
        article1.setSiteId(siteId);
        article1.setPrimaryCategoryId(cat1.getId());
        article1.setTitle("南京市2026年政务服务工作要点");
        article1.setSummary("为持续提升政务服务效能，优化营商环境和便民服务水平，制定本年度政务服务工作要点。");
        article1.setContent(buildHtmlContent(
                "南京市2026年政务服务工作要点",
                "一、总体目标",
                "以企业和群众办事需求为导向，推进政务服务标准化、规范化、便利化建设，打造\"宁满意\"政务服务品牌。",
                "二、重点任务",
                "1. 深化\"一网通办\"，政务服务事项网上可办率达95%以上。",
                "2. 推进\"一件事\"改革，新增50个\"一件事\"主题服务场景。",
                "3. 优化窗口服务，推行\"中午不打烊\"和\"周末预约办\"。",
                "4. 加强基层政务服务能力建设，实现镇街便民服务中心全覆盖。"
        ));
        article1.setCategory(cat1.getName());
        article1.setAuthor("市政务服务管理办公室");
        article1.setStatus(ArticleStatus.published);
        article1.setViews(3280);
        article1.setPublishedAt(LocalDateTime.now().minusDays(15));
        article1.setPublishedBy("system");

        Article article2 = new Article();
        article2.setSiteId(siteId);
        article2.setPrimaryCategoryId(cat2.getId());
        article2.setTitle("企业开办一件事办理指南");
        article2.setSummary("整合营业执照申领、公章刻制、发票申领、社保登记、银行预约开户等事项，实现企业开办一日办结。");
        article2.setContent(buildHtmlContent(
                "企业开办一件事办理指南",
                "一、适用对象",
                "在南京市行政区域内新设立的各类企业。",
                "二、办理流程",
                "1. 登录江苏政务服务网南京旗舰店，进入\"企业开办一件事\"专区。",
                "2. 在线填报企业设立登记信息，同步勾选需联办事项。",
                "3. 电子签名提交申请，等待审核。",
                "4. 审核通过后领取电子营业执照和实体印章。"
        ));
        article2.setCategory(cat2.getName());
        article2.setAuthor("市市场监督管理局");
        article2.setStatus(ArticleStatus.published);
        article2.setViews(5620);
        article2.setPublishedAt(LocalDateTime.now().minusDays(30));
        article2.setPublishedBy("system");

        Article article3 = new Article();
        article3.setSiteId(siteId);
        article3.setPrimaryCategoryId(cat3.getId());
        article3.setTitle("关于优化营商环境若干措施的通知");
        article3.setSummary("为进一步激发市场主体活力，持续打造市场化、法治化、国际化一流营商环境，现制定如下措施。");
        article3.setContent(buildHtmlContent(
                "关于优化营商环境若干措施的通知",
                "一、深化市场准入改革",
                "全面推行企业开办\"一网通办、一窗通取\"，压缩审批时限至0.5个工作日。",
                "二、优化政务服务",
                "推行\"免证办\"，依托电子证照库实现高频事项\"两个免于提交\"。",
                "三、强化要素保障",
                "完善\"宁企通\"惠企服务平台，推动政策免申即享、直达快享。"
        ));
        article3.setCategory(cat3.getName());
        article3.setAuthor("市人民政府办公厅");
        article3.setStatus(ArticleStatus.published);
        article3.setViews(8940);
        article3.setPublishedAt(LocalDateTime.now().minusDays(45));
        article3.setPublishedBy("system");

        Article article4 = new Article();
        article4.setSiteId(siteId);
        article4.setPrimaryCategoryId(cat4.getId());
        article4.setTitle("市民建议征集活动启动公告");
        article4.setSummary("诚邀广大市民围绕城市治理、公共服务、民生保障等方面建言献策，共同建设美丽南京。");
        article4.setContent(buildHtmlContent(
                "市民建议征集活动启动公告",
                "一、征集时间",
                "2026年6月1日至2026年8月31日。",
                "二、征集内容",
                "1. 城市规划建设与管理",
                "2. 教育、医疗、养老等公共服务",
                "3. 交通出行与停车管理",
                "4. 生态环境与绿色发展",
                "三、参与方式",
                "登录\"我的南京\"APP或本网站互动交流专区提交建议。"
        ));
        article4.setCategory(cat4.getName());
        article4.setAuthor("市信访局");
        article4.setStatus(ArticleStatus.published);
        article4.setViews(2150);
        article4.setPublishedAt(LocalDateTime.now().minusDays(5));
        article4.setPublishedBy("system");

        Article article5 = new Article();
        article5.setSiteId(siteId);
        article5.setPrimaryCategoryId(cat5.getId());
        article5.setTitle("南京市2026年第一季度经济运行数据发布");
        article5.setSummary("一季度全市地区生产总值同比增长5.8%，经济运行稳中向好，高质量发展取得新成效。");
        article5.setContent(buildHtmlContent(
                "南京市2026年第一季度经济运行数据发布",
                "一、地区生产总值",
                "一季度全市实现地区生产总值4125.6亿元，按可比价格计算，同比增长5.8%。",
                "二、工业经济",
                "规模以上工业增加值同比增长7.2%，其中新能源汽车、集成电路产业分别增长18.5%和12.3%。",
                "三、消费市场",
                "社会消费品零售总额1568.2亿元，同比增长4.6%，新能源汽车、智能家电消费保持较快增长。"
        ));
        article5.setCategory(cat5.getName());
        article5.setAuthor("市统计局");
        article5.setStatus(ArticleStatus.published);
        article5.setViews(4560);
        article5.setPublishedAt(LocalDateTime.now().minusDays(60));
        article5.setPublishedBy("system");

        articleRepository.saveAll(List.of(article1, article2, article3, article4, article5));
    }

    private String buildHtmlContent(String title, String... paragraphs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(title).append("</h2>\n");
        for (int i = 0; i < paragraphs.length; i++) {
            if (paragraphs[i].startsWith("一、") || paragraphs[i].startsWith("二、") || paragraphs[i].startsWith("三、") || paragraphs[i].startsWith("四、")) {
                if (i > 0) {
                    sb.append("</p>\n");
                }
                sb.append("<h3>").append(paragraphs[i]).append("</h3>\n<p>");
            } else {
                sb.append(paragraphs[i]);
                if (i < paragraphs.length - 1 && !paragraphs[i + 1].startsWith("一、") && !paragraphs[i + 1].startsWith("二、") && !paragraphs[i + 1].startsWith("三、") && !paragraphs[i + 1].startsWith("四、")) {
                    sb.append("<br>\n");
                }
            }
        }
        sb.append("</p>\n");
        return sb.toString();
    }

    private Template createHomeTemplate(TemplateRepository templateRepository, Long siteId) {
        Template template = new Template();
        template.setSiteId(siteId);
        template.setName("南京示范站首页");
        template.setCode("nanjing-home");
        template.setType("home");
        template.setStatus("active");
        template.setDescription("南京政务服务示范站首页模板，包含头图、快捷入口、文章列表等模块。");
        return templateRepository.save(template);
    }

    private Template createColumnTemplate(TemplateRepository templateRepository, Long siteId) {
        Template template = new Template();
        template.setSiteId(siteId);
        template.setName("南京示范站栏目页");
        template.setCode("nanjing-column");
        template.setType("column_list");
        template.setStatus("active");
        template.setDescription("南京政务服务示范站栏目列表页模板，包含面包屑、栏目头、文章列表和分页。");
        return templateRepository.save(template);
    }

    private Template createDetailTemplate(TemplateRepository templateRepository, Long siteId) {
        Template template = new Template();
        template.setSiteId(siteId);
        template.setName("南京示范站详情页");
        template.setCode("nanjing-detail");
        template.setType("content_detail");
        template.setStatus("active");
        template.setDescription("南京政务服务示范站内容详情页模板，包含面包屑、标题、元信息和正文。");
        return templateRepository.save(template);
    }

    private void createTemplateVersions(
            TemplateRepository templateRepository,
            TemplateVersionRepository templateVersionRepository,
            Template homeTemplate,
            Template columnTemplate,
            Template detailTemplate
    ) {
        String homeLayout = """
                {"layout":[{"slot":"main","name":"主内容区"}]}""";
        String homeBlocks = """
                {"blocks":[
                  {"type":"site_header","slot":"main"},
                  {"type":"hero","slot":"main","props":{"title":"南京政务服务示范站","subtitle":"让数据多跑路，让群众少跑腿"}},
                  {"type":"quick_links","slot":"main"},
                  {"type":"article_list","slot":"main","props":{"size":6}},
                  {"type":"site_footer","slot":"main"}
                ]}""";

        String columnLayout = """
                {"layout":[{"slot":"main","name":"主内容区"}]}""";
        String columnBlocks = """
                {"blocks":[
                  {"type":"site_header","slot":"main"},
                  {"type":"breadcrumb","slot":"main"},
                  {"type":"column_header","slot":"main"},
                  {"type":"article_list","slot":"main","props":{"size":10}},
                  {"type":"pagination","slot":"main"},
                  {"type":"site_footer","slot":"main"}
                ]}""";

        String detailLayout = """
                {"layout":[{"slot":"main","name":"主内容区"}]}""";
        String detailBlocks = """
                {"blocks":[
                  {"type":"site_header","slot":"main"},
                  {"type":"breadcrumb","slot":"main"},
                  {"type":"content_header","slot":"main"},
                  {"type":"content_meta","slot":"main"},
                  {"type":"content_body","slot":"main"},
                  {"type":"site_footer","slot":"main"}
                ]}""";

        TemplateVersion homeVersion = new TemplateVersion();
        homeVersion.setTemplateId(homeTemplate.getId());
        homeVersion.setVersionNo(1);
        homeVersion.setLayoutSchema(homeLayout);
        homeVersion.setBlockSchema(homeBlocks);
        homeVersion.setChangeLog("Initial version for nanjing demo home page.");

        TemplateVersion columnVersion = new TemplateVersion();
        columnVersion.setTemplateId(columnTemplate.getId());
        columnVersion.setVersionNo(1);
        columnVersion.setLayoutSchema(columnLayout);
        columnVersion.setBlockSchema(columnBlocks);
        columnVersion.setChangeLog("Initial version for nanjing demo column page.");

        TemplateVersion detailVersion = new TemplateVersion();
        detailVersion.setTemplateId(detailTemplate.getId());
        detailVersion.setVersionNo(1);
        detailVersion.setLayoutSchema(detailLayout);
        detailVersion.setBlockSchema(detailBlocks);
        detailVersion.setChangeLog("Initial version for nanjing demo detail page.");

        templateVersionRepository.saveAll(List.of(homeVersion, columnVersion, detailVersion));

        homeTemplate.setCurrentVersionId(homeVersion.getId());
        homeTemplate.setLatestVersionNo(1);
        columnTemplate.setCurrentVersionId(columnVersion.getId());
        columnTemplate.setLatestVersionNo(1);
        detailTemplate.setCurrentVersionId(detailVersion.getId());
        detailTemplate.setLatestVersionNo(1);

        templateRepository.saveAll(List.of(homeTemplate, columnTemplate, detailTemplate));
    }

    private void createBindings(
            TemplateBindingRepository templateBindingRepository,
            TemplateRepository templateRepository,
            Long siteId,
            Template homeTemplate,
            Template columnTemplate,
            Template detailTemplate,
            List<Category> categories
    ) {
        TemplateBinding homeBinding = new TemplateBinding();
        homeBinding.setSiteId(siteId);
        homeBinding.setTemplateId(homeTemplate.getId());
        homeBinding.setTemplateVersionId(homeTemplate.getCurrentVersionId());
        homeBinding.setTargetType("site");
        homeBinding.setTargetId(siteId);
        homeBinding.setBindingSlot("home");
        homeBinding.setStatus("active");

        templateBindingRepository.save(homeBinding);

        for (Category category : categories) {
            TemplateBinding listBinding = new TemplateBinding();
            listBinding.setSiteId(siteId);
            listBinding.setTemplateId(columnTemplate.getId());
            listBinding.setTemplateVersionId(columnTemplate.getCurrentVersionId());
            listBinding.setTargetType("category");
            listBinding.setTargetId(category.getId());
            listBinding.setBindingSlot("list");
            listBinding.setStatus("active");

            TemplateBinding detailBinding = new TemplateBinding();
            detailBinding.setSiteId(siteId);
            detailBinding.setTemplateId(detailTemplate.getId());
            detailBinding.setTemplateVersionId(detailTemplate.getCurrentVersionId());
            detailBinding.setTargetType("category");
            detailBinding.setTargetId(category.getId());
            detailBinding.setBindingSlot("detail");
            detailBinding.setStatus("active");

            templateBindingRepository.saveAll(List.of(listBinding, detailBinding));
        }

        homeTemplate.setBindingCount(1);
        columnTemplate.setBindingCount(categories.size());
        detailTemplate.setBindingCount(categories.size());
        templateRepository.saveAll(List.of(homeTemplate, columnTemplate, detailTemplate));
    }

    private void updateCategoryTemplates(
            CategoryRepository categoryRepository,
            List<Category> categories,
            Template columnTemplate,
            Template detailTemplate
    ) {
        for (Category category : categories) {
            category.setListTemplateId(columnTemplate.getId());
            category.setDetailTemplateId(detailTemplate.getId());
        }
        categoryRepository.saveAll(categories);
    }
}
