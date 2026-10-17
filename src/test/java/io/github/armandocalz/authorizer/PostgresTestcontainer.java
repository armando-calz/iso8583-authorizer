package io.github.armandocalz.authorizer;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A disposable PostgreSQL for tests and for running the app locally (TestAuthorizerApplication). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                // Also wait for the mapped port to be reachable from the host: some Docker
                // runtimes (e.g. Colima) publish it a moment after Postgres reports ready.
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                        .withStrategy(Wait.forListeningPort())
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }
}
