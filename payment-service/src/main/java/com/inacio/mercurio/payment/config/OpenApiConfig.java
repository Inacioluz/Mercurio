package com.inacio.mercurio.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    /** Codigo e mensagem padrao por status, para gerar exemplos coerentes. */
    private static final Map<String, String[]> DEFAULT_SAMPLES = Map.of(
            "400", new String[]{"VALIDATION_ERROR", "Um ou mais campos estao invalidos"},
            "404", new String[]{"PAYMENT_NOT_FOUND", "Pagamento nao encontrado"},
            "409", new String[]{"IDEMPOTENCY_KEY_REUSED", "A chave de idempotencia ja foi usada com outro conteudo"},
            "422", new String[]{"SAME_ACCOUNT_PAYMENT", "A conta de origem e destino devem ser diferentes"},
            "500", new String[]{"INTERNAL_ERROR", "Erro interno, tente novamente mais tarde"});

    @Bean
    public OpenAPI paymentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mercurio — Payment Service")
                        .version("1.0.0")
                        .description("""
                                Porta de entrada do sistema de pagamentos. Aceita a solicitacao, garante
                                idempotencia e publica o evento que inicia a saga.

                                ### O caminho de um pagamento
                                | # | Servico | Acao | Evento publicado |
                                |---|---------|------|------------------|
                                | 1 | payment | Aceita e grava `PENDING` | `payments.requested` |
                                | 2 | antifraud | Pontua o risco | `payments.approved` ou `payments.rejected` |
                                | 3 | ledger | Debita e credita | `payments.settled` ou `payments.failed` |
                                | 4 | notification | Avisa as partes | — |

                                Cada passo e assincrono. `POST /payments` responde **202** e o estado final
                                aparece em milissegundos no `GET /payments/{id}`.

                                ### Idempotencia
                                O header `Idempotency-Key` e obrigatorio no POST. A garantia e do indice
                                unico no banco; o Redis apenas evita a ida ao Postgres no caminho quente
                                dos retries.

                                ### Contas de demonstracao
                                `ACC-1001` e `ACC-1002` tem saldo; `ACC-2002` e `ACC-2003` recebem.
                                `ACC-9999` esta inativa e faz a liquidacao falhar.
                                Valores acima de 50.000 sao barrados pelo antifraude.
                                """)
                        .contact(new Contact().name("Jose Inacio").email("joseinaciolds@gmail.com"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url("http://localhost:8081").description("Ambiente local")));
    }

    /**
     * Acrescenta o 500 a todas as operacoes e preenche o exemplo de qualquer
     * resposta de erro que nao declare um proprio — sem isso, o Swagger UI monta
     * o exemplo a partir dos exemplos de campo do schema e um 404 acaba exibindo
     * o corpo de um 422.
     */
    @Bean
    public OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperations().forEach(operation -> {
                    operation.getResponses().addApiResponse("500", errorResponse(
                            "Erro interno inesperado", 500,
                            "INTERNAL_ERROR", "Erro interno, tente novamente mais tarde", path));

                    operation.getResponses().forEach((code, response) -> {
                        String[] sample = DEFAULT_SAMPLES.get(code);
                        if (sample == null || response.getContent() == null) {
                            return;
                        }
                        MediaType mediaType = response.getContent().get("application/json");
                        if (mediaType == null || mediaType.getExample() != null || mediaType.getExamples() != null) {
                            return;
                        }
                        if (mediaType.getSchema() == null || !ERROR_SCHEMA_REF.equals(mediaType.getSchema().get$ref())) {
                            return;
                        }
                        mediaType.setExample(exampleBody(Integer.parseInt(code), sample[0], sample[1], path));
                    });
                }));
    }

    private ApiResponse errorResponse(String description, int status, String error, String message, String path) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref(ERROR_SCHEMA_REF))
                        .example(exampleBody(status, error, message, path))));
    }

    private Map<String, Object> exampleBody(int status, String error, String message, String path) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("timestamp", "2026-08-26T13:45:30Z");
        example.put("status", status);
        example.put("error", error);
        example.put("message", message);
        example.put("path", path);
        return example;
    }
}
