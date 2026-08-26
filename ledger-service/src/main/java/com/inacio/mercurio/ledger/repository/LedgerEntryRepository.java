package com.inacio.mercurio.ledger.repository;

import com.inacio.mercurio.ledger.domain.EntryDirection;
import com.inacio.mercurio.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Chave da idempotencia da liquidacao: ja existe partida para este pagamento? */
    Optional<LedgerEntry> findFirstByPaymentIdAndDirection(UUID paymentId, EntryDirection direction);

    List<LedgerEntry> findByTransactionIdOrderByDirectionAsc(UUID transactionId);

    Page<LedgerEntry> findByAccountNumberOrderByCreatedAtDesc(String accountNumber, Pageable pageable);

    /**
     * Soma com sinal de todas as partidas. Em partidas dobradas o resultado tem
     * de ser exatamente zero — qualquer outro valor denuncia lancamento
     * desbalanceado.
     */
    @Query("""
            select coalesce(sum(case when e.direction = com.inacio.mercurio.ledger.domain.EntryDirection.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e
            """)
    BigDecimal signedSum();

    long countByPaymentId(UUID paymentId);
}
