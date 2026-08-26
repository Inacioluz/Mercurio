package com.inacio.mercurio.payment.repository;

import com.inacio.mercurio.payment.domain.Payment;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByPayerAccountOrPayeeAccount(String payerAccount, String payeeAccount, Pageable pageable);

    /**
     * Trava a linha ate o fim da transacao. Os consumidores de evento atualizam
     * o status por aqui, de modo que dois eventos do mesmo pagamento chegando em
     * paralelo (topicos diferentes) sejam serializados pelo banco.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(PaymentStatus status);
}
