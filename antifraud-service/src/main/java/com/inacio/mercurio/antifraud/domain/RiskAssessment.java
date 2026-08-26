package com.inacio.mercurio.antifraud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Laudo da analise, guardado no MongoDB.
 *
 * <p>O modelo de documento ganha aqui: cada regra acrescenta campos proprios ao
 * laudo, e a lista de sinais varia em forma conforme o que disparou. Num schema
 * relacional isso viraria uma tabela larga com colunas nulas ou um EAV.
 */
@Document(collection = "risk_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

    @Id
    private String id;

    /** Unico: uma reentrega do evento nao gera segundo laudo. */
    @Indexed(unique = true)
    private UUID paymentId;

    @Indexed
    private String payerAccount;

    private String payeeAccount;
    private BigDecimal amount;
    private String currency;

    /** 0 (seguro) a 100 (critico). */
    private int riskScore;

    private RiskDecision decision;

    /** Sinais que contribuiram para a pontuacao. */
    private List<TriggeredRule> triggeredRules;

    @Indexed
    private Instant assessedAt;

    /** Tempo gasto na analise, util para acompanhar a latencia da saga. */
    private long evaluationMillis;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TriggeredRule {
        private String code;
        private String description;
        private int points;
    }
}
