package com.postgrespulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI postgresPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PostgresPulse API")
                .version("v1")
                .description("Plataforma de monitoreo y salud de bases de datos PostgreSQL. "
                        + "Registro de fuentes, analisis de diagnostico (8 chequeos), "
                        + "historial de tendencia y exportacion de reportes."));
    }
}
