package com.inacio.mercurio.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PageResponse", description = "Envelope de paginacao")
public record PageResponse<T>(

        @Schema(description = "Itens da pagina") List<T> content,
        @Schema(description = "Indice da pagina, base zero", example = "0") int page,
        @Schema(description = "Itens por pagina", example = "20") int size,
        @Schema(description = "Total de itens", example = "137") long totalElements,
        @Schema(description = "Total de paginas", example = "7") int totalPages,
        @Schema(description = "Ultima pagina?", example = "false") boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
