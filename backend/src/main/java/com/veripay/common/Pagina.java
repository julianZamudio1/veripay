package com.veripay.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Respuesta paginada estable (evita serializar PageImpl directamente). */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapper) {
        return de(page.map(mapper));
    }
}
