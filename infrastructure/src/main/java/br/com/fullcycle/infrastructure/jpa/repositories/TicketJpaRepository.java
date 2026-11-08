package br.com.fullcycle.infrastructure.jpa.repositories;

import br.com.fullcycle.infrastructure.jpa.entities.TicketEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketJpaRepository extends CrudRepository<TicketEntity, UUID> {

    @Query("select ticket from Ticket ticket where ticket.eventId = :eventId")
    List<TicketEntity> findByEventId(@Param("eventId") UUID eventId);
}
