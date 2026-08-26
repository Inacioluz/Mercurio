package com.inacio.mercurio.notification.repository;

import com.inacio.mercurio.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    Page<Notification> findByRecipientAccountOrderByCreatedAtDesc(String recipientAccount, Pageable pageable);

    Page<Notification> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    boolean existsByEventIdAndRecipientAccount(UUID eventId, String recipientAccount);

    long countByType(String type);
}
