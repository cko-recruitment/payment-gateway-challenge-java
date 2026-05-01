package com.checkout.payment.gateway;

import com.checkout.payment.gateway.idempotency.IdempotencyRepository;
import com.checkout.payment.gateway.payment.entity.PaymentsRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  @Autowired
  private PaymentsRepository paymentRepository;

  @Autowired
  private IdempotencyRepository idempotencyRepository;


  @Autowired
  private RetryRegistry retryRegistry;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  static final Network network = Network.newNetwork();

  static GenericContainer<?> mountBank;

  static {
    mountBank = new GenericContainer<>(DockerImageName.parse("bbyars/mountebank:2.8.1"))
        .withNetwork(network)
        .withExposedPorts(2525, 8080)
        .withCommand("--configfile /imposters/bank_simulator.ejs --allowInjection")
        .withFileSystemBind("./imposters", "/imposters", BindMode.READ_ONLY)
        .waitingFor(Wait.forLogMessage(".*Open for business.*", 1));

    Startables.deepStart(mountBank).join();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("client.mounte-bank.url",
        () -> "http://%s:%d".formatted(mountBank.getHost(), mountBank.getMappedPort(8080)));
  }

  @BeforeEach
  void setup() {
    paymentRepository.deleteAll();
    idempotencyRepository.deleteAll();

    circuitBreakerRegistry.circuitBreaker("mounteBank").reset();
    retryRegistry.retry("mounteBank").getEventPublisher();
  }
}
