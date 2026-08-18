package com.postgrespulse;

import com.postgrespulse.config.PropiedadesCifrado;
import com.postgrespulse.config.PropiedadesSeguridad;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({PropiedadesCifrado.class, PropiedadesSeguridad.class})
public class PostgresPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostgresPulseApplication.class, args);
    }
}
