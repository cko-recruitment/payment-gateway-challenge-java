package com.checkout.payment.gateway.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis session configuration.
 * Enables distributed session storage so sessions survive pod restarts.
 * Only enabled when redis.enabled=true (production with Redis).
 */
@Configuration
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class RedisSessionConfig {

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    return template;
  }
}
