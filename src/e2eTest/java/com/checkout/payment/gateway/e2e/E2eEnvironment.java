package com.checkout.payment.gateway.e2e;

import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The containers the end-to-end suite runs against: the real application image and the real
 * bank simulator, on a shared network.
 *
 * <p>Started once per test JVM from a static initialiser and never stopped explicitly —
 * Testcontainers' Ryuk sidecar reaps them when the JVM exits. Note that {@code @Testcontainers}
 * with a static {@code @Container} would start and stop them once per <em>class</em>, which
 * would pay the application's startup cost again for every test class.
 */
final class E2eEnvironment {

  private static final Logger LOG = LoggerFactory.getLogger(E2eEnvironment.class);

  // Hyphen, not underscore: underscores are invalid in hostnames, so java.net.URI reports a
  // null host and the JDK HTTP client behind Spring's RestClient rejects the URI.
  private static final String BANK_ALIAS = "bank-simulator";
  private static final int BANK_PORT = 8080;
  private static final int BANK_ADMIN_PORT = 2525;
  private static final int GATEWAY_PORT = 8090;

  private static final String IMAGE = System.getProperty("e2e.image", "payment-gateway:e2e");
  private static final Path IMPOSTER_FILE =
      Path.of(System.getProperty("e2e.imposterFile", "imposters/bank_simulator.ejs"));

  private static final GenericContainer<?> BANK;
  private static final GenericContainer<?> GATEWAY;

  static {
    // No "is Docker available" pre-check here on purpose: it reports false for any client
    // initialisation failure, which masks the real reason. The e2eTest task checks the
    // daemon up front, and Testcontainers' own error is more informative than a guess.
    Network network = Network.newNetwork();

    BANK = new GenericContainer<>(DockerImageName.parse("bbyars/mountebank:2.8.1"))
        .withNetwork(network)
        .withNetworkAliases(BANK_ALIAS)
        .withExposedPorts(BANK_ADMIN_PORT, BANK_PORT)
        // imposters/ is excluded by .dockerignore, so the stub definition is copied in over
        // the Docker API rather than bind-mounted, which keeps it portable off Windows.
        .withCopyFileToContainer(
            MountableFile.forHostPath(IMPOSTER_FILE), "/imposters/bank_simulator.ejs")
        // --allowInjection is required: the authorized stub uses a `decorate` JS behavior.
        .withCommand(
            "--configfile", "/imposters/bank_simulator.ejs", "--allowInjection", "--debug")
        // Waits for the imposter itself to exist, not just for the admin port to listen.
        .waitingFor(Wait.forHttp("/imposters/" + BANK_PORT)
            .forPort(BANK_ADMIN_PORT)
            .forStatusCode(200))
        .withStartupTimeout(Duration.ofSeconds(60))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("bank"));

    GATEWAY = new GenericContainer<>(DockerImageName.parse(IMAGE))
        .withNetwork(network)
        .withExposedPorts(GATEWAY_PORT)
        .withEnv("PAYMENTS_BANK_API_URL", "http://" + BANK_ALIAS + ":" + BANK_PORT)
        .withEnv("PAYMENTS_SUPPORTED_CURRENCIES", "GBP,USD,EUR")
        // Not Wait.forHealthcheck(): the Dockerfile's HEALTHCHECK has --start-period=40s, so
        // Docker cannot report healthy before then. Polling the endpoint ourselves is quicker.
        .waitingFor(Wait.forHttp("/actuator/health").forPort(GATEWAY_PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("gateway"));

    // Started separately so a simulator failure is not reported as a gateway failure.
    BANK.start();
    GATEWAY.start();
  }

  static String gatewayBaseUrl() {
    return "http://" + GATEWAY.getHost() + ":" + GATEWAY.getMappedPort(GATEWAY_PORT);
  }

  static String bankAdminBaseUrl() {
    return "http://" + BANK.getHost() + ":" + BANK.getMappedPort(BANK_ADMIN_PORT);
  }

  static int bankImposterPort() {
    return BANK_PORT;
  }

  private E2eEnvironment() {
  }
}
