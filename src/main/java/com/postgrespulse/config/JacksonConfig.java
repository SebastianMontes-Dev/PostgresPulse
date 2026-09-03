package com.postgrespulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring Boot 4 auto-configura Jackson 3 (tools.jackson) por defecto, no
 * Jackson 2 (com.fasterxml.jackson) -- el resto de la app sigue en Jackson 2
 * (LimiteTasaApiFilter, ExportacionServicio, jjwt-jackson, los DTOs), asi que
 * sin este bean explicito el arranque falla por completo (ningun ObjectMapper
 * candidato para inyectar). Jackson2ObjectMapperBuilder registra
 * automaticamente los modulos disponibles via SPI (jackson-datatype-jsr310
 * para OffsetDateTime, etc.), igual que hacia la auto-configuracion previa.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }
}
