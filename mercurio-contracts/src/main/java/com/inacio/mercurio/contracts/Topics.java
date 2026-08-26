package com.inacio.mercurio.contracts;

import java.util.List;

/**
 * Nomes dos topicos Kafka. Um topico por tipo de evento — a alternativa (um
 * topico com varios tipos) exigiria um cabecalho de tipo e desserializacao
 * polimorfica, sem ganho aqui.
 *
 * <p>Todos os eventos de um mesmo pagamento usam o {@code paymentId} como chave
 * de particao, o que garante ordem por pagamento dentro de cada topico.
 */
public final class Topics {

    /** Pagamento aceito pela API e aguardando analise. */
    public static final String PAYMENT_REQUESTED = "mercurio.payments.requested";

    /** Analise antifraude liberou o pagamento. */
    public static final String PAYMENT_APPROVED = "mercurio.payments.approved";

    /** Analise antifraude barrou o pagamento. */
    public static final String PAYMENT_REJECTED = "mercurio.payments.rejected";

    /** Valor efetivamente movimentado no razao. */
    public static final String PAYMENT_SETTLED = "mercurio.payments.settled";

    /** Liquidacao impossivel (saldo, conta invalida). */
    public static final String PAYMENT_FAILED = "mercurio.payments.failed";

    /**
     * Numero de particoes de todo topico do sistema.
     *
     * <p>Precisa ser o mesmo em todos os servicos. Se um servico sobe antes do
     * topico existir e o broker o cria sozinho com o default de uma particao, as
     * demais ficam sem consumidor e os eventos que caem nelas nunca sao
     * processados — por isso o auto-create esta desligado no broker e cada
     * servico declara os topicos no start.
     */
    public static final int PARTITIONS = 3;

    public static final short REPLICAS = 1;

    /** Todos os topicos, para que cada servico os declare na subida. */
    public static List<String> all() {
        return List.of(PAYMENT_REQUESTED, PAYMENT_APPROVED, PAYMENT_REJECTED, PAYMENT_SETTLED, PAYMENT_FAILED);
    }

    /** Topico de mensagens mortas correspondente. */
    public static String deadLetter(String topic) {
        return topic + ".DLT";
    }

    private Topics() {
    }
}
