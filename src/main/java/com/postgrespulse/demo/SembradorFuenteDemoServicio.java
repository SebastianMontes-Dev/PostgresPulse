package com.postgrespulse.demo;

import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.excepcion.NombreDuplicadoException;
import com.postgrespulse.servicio.FuenteServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * docs/SPECS.md #17: "funcional con un solo comando de Docker Compose + panel
 * de control" -- sin este sembrado, arrancar con docker compose deja el
 * panel vacio y exige un POST /api/v1/fuentes manual antes de poder
 * demostrar nada. Solo activo con app.demo.sembrar=true (PULSE_DEMO_SEED),
 * que docker-compose.yml habilita por defecto; en un despliegue real contra
 * fuentes de produccion se deja en false. Reutiliza FuenteServicio.crear
 * para heredar validacion y cifrado igual que cualquier fuente registrada
 * por API; captura NombreDuplicadoException para que reiniciar el
 * contenedor "app" (fuente ya sembrada en un arranque previo) no falle.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "sembrar", havingValue = "true")
public class SembradorFuenteDemoServicio implements ApplicationRunner {

    private static final Logger REGISTRO = LoggerFactory.getLogger(SembradorFuenteDemoServicio.class);

    static final String NOMBRE_FUENTE_DEMO = "Ventas Demo";

    private final FuenteServicio fuenteServicio;
    private final String host;
    private final int puerto;

    public SembradorFuenteDemoServicio(FuenteServicio fuenteServicio,
                                        @Value("${app.demo.host:target-demo}") String host,
                                        @Value("${app.demo.puerto:5432}") int puerto) {
        this.fuenteServicio = fuenteServicio;
        this.host = host;
        this.puerto = puerto;
    }

    @Override
    public void run(ApplicationArguments args) {
        CrearFuenteDto dto = new CrearFuenteDto(
                NOMBRE_FUENTE_DEMO,
                host,
                puerto,
                "ventas_db",
                "demo",
                "demo",
                "public,ventas",
                List.of("demo"));
        try {
            fuenteServicio.crear(dto);
            REGISTRO.info("Fuente de demostración '{}' sembrada automáticamente ({}:{})",
                    NOMBRE_FUENTE_DEMO, host, puerto);
        } catch (NombreDuplicadoException ex) {
            REGISTRO.debug("Fuente de demostración '{}' ya estaba registrada, no se vuelve a sembrar",
                    NOMBRE_FUENTE_DEMO);
        }
    }
}
