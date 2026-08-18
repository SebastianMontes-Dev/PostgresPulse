package com.postgrespulse.analisis;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fabrica: Spring inyecta automaticamente todos los @Component que implementan
 * ChequeoAnalisis. Agregar un chequeo #9 no requiere tocar esta clase.
 */
@Component
public class FabricaChequeos {

    private final List<ChequeoAnalisis> chequeos;

    public FabricaChequeos(List<ChequeoAnalisis> chequeos) {
        this.chequeos = List.copyOf(chequeos);
    }

    public List<ChequeoAnalisis> obtenerTodos() {
        return chequeos;
    }
}
