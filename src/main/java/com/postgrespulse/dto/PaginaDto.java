package com.postgrespulse.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PaginaDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <E, T> PaginaDto<T> desde(Page<E> pagina, Function<E, T> mapeador) {
        return new PaginaDto<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
