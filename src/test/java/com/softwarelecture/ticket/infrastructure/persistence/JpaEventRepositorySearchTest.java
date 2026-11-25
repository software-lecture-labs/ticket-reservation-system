package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventCriteria;
import com.softwarelecture.ticket.domain.model.Venue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Katalog aramasını gerçek bir JPA sağlayıcı ve bellek içi veritabanı ile çalıştırır. */
class JpaEventRepositorySearchTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant JAN_10 = Instant.parse("2030-01-10T18:00:00Z");
    private static final Instant JAN_20 = Instant.parse("2030-01-20T18:00:00Z");
    private static final Instant FEB_01 = Instant.parse("2030-02-01T18:00:00Z");
    private static final String ROCK = "Rock Gecesi";
    private static final String JAZZ = "Caz 100% Canli";
    private static final String HAMLET = "Hamlet";
    // H2 LOWER() JVM yerel ayarını kullanır (tr: I -> ı); büyük I içeren test verisi bu yüzden kullanılmaz.

    private static EntityManagerFactory factory;

    private EntityManager entityManager;
    private JpaEventRepository repository;
    private Category music;
    private Category theatre;

    @BeforeAll
    static void startProvider() {
        factory = new PersistenceProvider().createContainerEntityManagerFactory(new TestUnit(), Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:catalogtest;DB_CLOSE_DELAY=-1;MODE=LEGACY",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "eclipselink.target-database", "org.eclipse.persistence.platform.database.H2Platform",
                "eclipselink.ddl-generation", "create-tables",
                "eclipselink.ddl-generation.output-mode", "database"));
    }

    @AfterAll
    static void stopProvider() {
        factory.close();
    }

    @BeforeEach
    void seed() throws ReflectiveOperationException {
        entityManager = factory.createEntityManager();
        repository = new JpaEventRepository();
        Field field = JpaEventRepository.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, entityManager);

        entityManager.getTransaction().begin();
        music = persist(Category.create("Muzik"));
        theatre = persist(Category.create("Tiyatro"));
        Venue ankara = persist(Venue.create("Salon A", "Ankara", "Merkez"));
        Venue izmir = persist(Venue.create("Salon B", "Bursa", "Nilufer"));
        entityManager.flush(); // IDENTITY kimlikleri etkinlikler bağlanmadan önce atanmalı
        persist(Event.schedule(ROCK, ankara, music, JAN_10, 100, BigDecimal.TEN, NOW));
        persist(Event.schedule(JAZZ, izmir, music, JAN_20, 50, BigDecimal.TEN, NOW));
        persist(Event.schedule(HAMLET, ankara, theatre, FEB_01, 80, BigDecimal.TEN, NOW));
        Event inactive = persist(Event.schedule("Pasif Gosteri", ankara, music, JAN_10, 10, BigDecimal.TEN, NOW));
        entityManager.flush();
        inactive.deactivate();
        entityManager.getTransaction().commit();
        entityManager.clear();
    }

    @AfterEach
    void cleanUp() {
        entityManager.getTransaction().begin();
        entityManager.createQuery("DELETE FROM Event").executeUpdate();
        entityManager.createQuery("DELETE FROM Venue").executeUpdate();
        entityManager.createQuery("DELETE FROM Category").executeUpdate();
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private static EventCriteria criteria(Instant from, Instant to, String city, Long categoryId, String title) {
        return new EventCriteria(NOW, from, to, city, categoryId, title);
    }

    private List<String> titles(EventCriteria criteria) {
        return repository.search(criteria, 0, 50).stream().map(Event::getTitle).toList();
    }

    @Test
    void withoutFiltersReturnsActiveUpcomingEventsInDateOrder() {
        EventCriteria all = criteria(null, null, null, null, null);

        assertEquals(List.of(ROCK, JAZZ, HAMLET), titles(all));
        assertEquals(3, repository.count(all));
    }

    @Test
    void ignoresEventsThatAlreadyStarted() {
        EventCriteria late = new EventCriteria(Instant.parse("2030-01-15T00:00:00Z"), null, null, null, null, null);

        assertEquals(List.of(JAZZ, HAMLET), titles(late));
    }

    @Test
    void filtersByDateRange() {
        EventCriteria january = criteria(JAN_10, FEB_01, null, null, null);

        assertEquals(List.of(ROCK, JAZZ), titles(january));
        assertEquals(2, repository.count(january));
    }

    @Test
    void filtersByCategory() {
        assertEquals(List.of(HAMLET), titles(criteria(null, null, null, theatre.getId(), null)));
    }

    @Test
    void filtersByCityIgnoringCase() {
        assertEquals(List.of(ROCK, HAMLET), titles(criteria(null, null, "ANKARA", null, null)));
        assertEquals(List.of(JAZZ), titles(criteria(null, null, "bursa", null, null)));
    }

    @Test
    void filtersByTitleIgnoringCaseAndTreatingWildcardsAsText() {
        assertEquals(List.of(ROCK), titles(criteria(null, null, null, null, "ROCK")));
        assertEquals(List.of(JAZZ), titles(criteria(null, null, null, null, "100%")));
        assertEquals(List.of(JAZZ), titles(criteria(null, null, null, null, "%")));
        assertTrue(titles(criteria(null, null, null, null, "_")).isEmpty());
    }

    @Test
    void combinesAllFiltersAndPaginates() {
        EventCriteria combined = criteria(JAN_10, FEB_01, "Ankara", music.getId(), "gece");
        assertEquals(List.of(ROCK), titles(combined));
        assertEquals(1, repository.count(combined));

        EventCriteria all = criteria(null, null, null, null, null);
        assertEquals(List.of(JAZZ), repository.search(all, 1, 1).stream().map(Event::getTitle).toList());
    }

    @Test
    void findsEventsById() {
        Event stored = repository.search(criteria(null, null, null, null, HAMLET), 0, 1).get(0);

        assertEquals(Optional.of(HAMLET), repository.findById(stored.getId()).map(Event::getTitle));
        assertTrue(repository.findById(-1L).isEmpty());
    }

    /** persistence.xml taraması yerine yalnızca katalog varlıklarını tanıtan test birimi. */
    private static final class TestUnit implements PersistenceUnitInfo {
        @Override public String getPersistenceUnitName() {
            return "ticketTestPU";
        }

        @Override public String getPersistenceProviderClassName() {
            return PersistenceProvider.class.getName();
        }

        @Override public String getScopeAnnotationName() {
            return null;
        }

        @Override public List<String> getQualifierAnnotationNames() {
            return List.of();
        }

        @Override public PersistenceUnitTransactionType getTransactionType() {
            return PersistenceUnitTransactionType.RESOURCE_LOCAL;
        }

        @Override public DataSource getJtaDataSource() {
            return null;
        }

        @Override public DataSource getNonJtaDataSource() {
            return null;
        }

        @Override public List<String> getMappingFileNames() {
            return List.of();
        }

        @Override public List<URL> getJarFileUrls() {
            return List.of();
        }

        @Override public URL getPersistenceUnitRootUrl() {
            return Event.class.getProtectionDomain().getCodeSource().getLocation();
        }

        @Override public List<String> getManagedClassNames() {
            return List.of(Event.class.getName(), Venue.class.getName(), Category.class.getName());
        }

        @Override public boolean excludeUnlistedClasses() {
            return true;
        }

        @Override public SharedCacheMode getSharedCacheMode() {
            return SharedCacheMode.NONE;
        }

        @Override public ValidationMode getValidationMode() {
            return ValidationMode.NONE;
        }

        @Override public Properties getProperties() {
            return new Properties();
        }

        @Override public String getPersistenceXMLSchemaVersion() {
            return "3.2";
        }

        @Override public ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        @Override public void addTransformer(ClassTransformer transformer) {
            // Test ortamında sınıf dönüşümü kullanılmaz.
        }

        @Override public ClassLoader getNewTempClassLoader() {
            return getClassLoader();
        }
    }
}
