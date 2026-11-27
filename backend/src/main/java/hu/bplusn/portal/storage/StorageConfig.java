package hu.bplusn.portal.storage;

import hu.bplusn.portal.config.PortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** PORTAL_DB_URL megadásakor PostgreSQL, egyébként helyi fájlok (data mappa). */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public PortalStorage portalStorage(PortalProperties properties) {
        PortalProperties.Database database = properties.database();
        PortalStorage storage = database.url().isBlank()
                ? new FileStorage(Path.of(properties.dataDir()))
                : new JdbcStorage(database.url(), database.username(), database.password());
        log.info("Adattárolás: {}", storage.description());
        return storage;
    }
}
