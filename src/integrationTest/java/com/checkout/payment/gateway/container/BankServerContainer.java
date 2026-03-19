package com.checkout.payment.gateway.container;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public class BankServerContainer {

  @Container
  static GenericContainer<?> mountebank;

  static {
    mountebank = new GenericContainer<>(DockerImageName.parse("bbyars/mountebank:2.8.1"))
        .withExposedPorts(8080, 2525)
        .withCopyFileToContainer(MountableFile.forHostPath("imposters/bank_simulator.ejs"),
            "/imposters/bank_simulator.ejs")
        .withCommand("start", "--configfile", "/imposters/bank_simulator.ejs", "--allowInjection")
        .waitingFor(Wait.forHttp("/").forPort(2525));
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("bank.simulator.url", () -> "http://" + mountebank.getHost() + ":" + mountebank.getMappedPort(8080) + "/payments");
  }
}
