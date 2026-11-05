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
 */
final class E2eEnvironment {

  private static final Logger LOG = LoggerFactory.getLogger(E2eEnvironment.class);
  
  private static final String BANK_ALIAS = "bank-simulator";
  private static final int BANK_PORT = 8080;
  private static final int BANK_ADMIN_PORT = 2525;
  private static final int GATEWAY_PORT = 8090;

  private static final String IMAGE = System.getProperty("e2e.image", "payment-gateway:e2e");
  private static final Path IMPOSTER_FILE =
      Path.of(System.getProperty("e2e.imposterFile", "imposters/bank_simulator.ejs"));

  private static final Network NETWORK = Network.newNetwork();
  private static final GenericContainer<?> BANK;
  private static final GenericContainer<?> GATEWAY;

  static {
    BANK = new GenericContainer<>(DockerImageName.parse("bbyars/mountebank:2.8.1"))
        .withNetwork(NETWORK)
        .withNetworkAliases(BANK_ALIAS)
        .withExposedPorts(BANK_ADMIN_PORT, BANK_PORT)
        // Copied rather than bind-mounted, which avoids Windows path translation.
        .withCopyFileToContainer(
            MountableFile.forHostPath(IMPOSTER_FILE), "/imposters/bank_simulator.ejs")
        // --allowInjection is required: the stub uses a `decorate` JS behavior.
        .withCommand(
            "--configfile", "/imposters/bank_simulator.ejs", "--allowInjection", "--debug")
        // Waits for the imposter to exist on one of its endpoints
        .waitingFor(Wait.forHttp("/imposters/" + BANK_PORT)
            .forPort(BANK_ADMIN_PORT)
            .forStatusCode(200))
        .withStartupTimeout(Duration.ofSeconds(60))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("bank"));

    GATEWAY = new GenericContainer<>(DockerImageName.parse(IMAGE))
        .withNetwork(NETWORK)
        .withExposedPorts(GATEWAY_PORT)
        .withEnv("PAYMENTS_BANK_API_URL", "http://" + BANK_ALIAS + ":" + BANK_PORT)
        .withEnv("PAYMENTS_SUPPORTED_CURRENCIES", "GBP,USD,EUR")
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

  /** Hostname the bank answers on inside the container network. */
  static String bankAlias() {
    return BANK_ALIAS;
  }

  /**
   * An unstarted gateway pointed at some other bank.
   */
  static GenericContainer<?> gatewayPointingAt(String bankApiUrl) {
    return new GenericContainer<>(DockerImageName.parse(IMAGE))
        .withNetwork(NETWORK)
        .withExposedPorts(GATEWAY_PORT)
        .withEnv("PAYMENTS_BANK_API_URL", bankApiUrl)
        .withEnv("PAYMENTS_SUPPORTED_CURRENCIES", "GBP,USD,EUR")
        .waitingFor(Wait.forHttp("/actuator/health").forPort(GATEWAY_PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2))
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("gateway-alt"));
  }

  static String baseUrlOf(GenericContainer<?> gateway) {
    return "http://" + gateway.getHost() + ":" + gateway.getMappedPort(GATEWAY_PORT);
  }

  private E2eEnvironment() {
  }
}
