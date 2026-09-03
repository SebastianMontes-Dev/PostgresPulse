package com.postgrespulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 auto-configura Jackson 3 (tools.jackson) por defecto, no
 * Jackson 2 (com.fasterxml.jackson) -- el resto de la app sigue en Jackson 2
 * (LimiteTasaApiFilter, ExportacionServicio, jjwt-jackson, los DTOs), asi que
 * sin este bean explicito el arranque falla por completo (ningun ObjectMapper
 * candidato para inyectar). findAndAddModules() registra via SPI los modulos
 * disponibles (jackson-datatype-jsr310 para OffsetDateTime, etc.), igual que
 * hacia la auto-configuracion previa -- Jackson2ObjectMapperBuilder (el que se
 * usaba antes) esta deprecado y marcado para remocion desde Spring Framework
 * 7.0 a favor de builders directos de Jackson.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
