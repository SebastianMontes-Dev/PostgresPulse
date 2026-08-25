package com.postgrespulse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI postgresPulseOpenApi(BuildProperties buildProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("PostgresPulse API")
                        .version(buildProperties.getVersion())
                        .description("Plataforma de monitoreo y salud de bases de datos PostgreSQL. "
                                + "Registro de fuentes, analisis de diagnostico (8 chequeos), "
                                + "historial de tendencia y exportacion de reportes."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token obtenido en POST /api/v1/auth/login "
                                        + "(campo \"token\" de la respuesta, sin el prefijo \"Bearer \")")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
