package gov.cms.admin.service;

import gov.cms.admin.dto.PortalSearchResponse;
import gov.cms.admin.entity.SearchIndexEntry;
import gov.cms.admin.repository.SearchIndexEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class HibernateSearchServiceTest {

  @Autowired
  private HibernateSearchService hibernateSearchService;

  @Autowired
  private SearchIndexEntryRepository searchIndexEntryRepository;

  @Autowired
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    SearchIndexEntry entry1 = new SearchIndexEntry();
    entry1.setSiteId(1L);
    entry1.setObjectType("content");
    entry1.setObjectId(1L);
    entry1.setTitle("Government Digital Service");
    entry1.setSummary("Digital transformation initiatives");
    entry1.setPath("/news/1");
    entry1.setStatus("published");
    entry1.setPublishedAt(LocalDateTime.now().minusDays(1));
    searchIndexEntryRepository.save(entry1);

    SearchIndexEntry entry2 = new SearchIndexEntry();
    entry2.setSiteId(1L);
    entry2.setObjectType("content");
    entry2.setObjectId(2L);
    entry2.setTitle("Public Health Update");
    entry2.setSummary("Latest health policies");
    entry2.setPath("/news/2");
    entry2.setStatus("published");
    entry2.setPublishedAt(LocalDateTime.now().minusDays(5));
    searchIndexEntryRepository.save(entry2);

    hibernateSearchService.rebuildIndex();
  }

  @AfterEach
  void tearDown() {
    searchIndexEntryRepository.deleteAll();
  }

  @Test
  void search_byKeyword_shouldReturnResults() {
    PortalSearchResponse response = hibernateSearchService.search(1L, "digital", 0, 10, null, null, null, null);
    assertTrue(response.getTotal() > 0);
  }

  @Test
  void search_sortByTitleAsc_shouldReturnAlphabeticalOrder() {
    PortalSearchResponse response = hibernateSearchService.search(1L, null, 0, 10, null, null, "title", "asc");
    assertEquals(2, response.getTotal());
    assertTrue(response.getItems().get(0).getTitle().compareTo(response.getItems().get(1).getTitle()) <= 0);
  }

  @Test
  void search_sortByPublishedAtDesc_shouldReturnNewestFirst() {
    PortalSearchResponse response = hibernateSearchService.search(1L, null, 0, 10, null, null, "publishedAt", "desc");
    assertTrue(response.getItems().get(0).getPublishedAt().compareTo(response.getItems().get(1).getPublishedAt()) >= 0);
  }
}
