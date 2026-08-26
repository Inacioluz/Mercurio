package com.inacio.mercurio.ledger.repository;

import com.inacio.mercurio.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /** Trava a linha ate o fim da transacao, serializando movimentacoes concorrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByIdForUpdate(@Param("accountNumber") String accountNumber);

    /** Soma de todos os saldos — deve bater com a soma das partidas. */
    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal totalBalance();
}
