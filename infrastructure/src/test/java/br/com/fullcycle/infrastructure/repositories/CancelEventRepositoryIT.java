package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.application.event.GetEventByIdUseCase;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.infrastructure.jpa.entities.OutboxEntity;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

class CancelEventRepositoryIT extends IntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private GetEventByIdUseCase getEventByIdUseCase;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve persistir o status do evento e gravar event.cancelled na outbox")
    public void testStatusRoundTripAndOutbox() {
        // given
        final var partner = Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com");
        final var created = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, partner));

        // when
        final var active = eventRepository.eventOfId(created.eventId()).orElseThrow();
        final var queried = getEventByIdUseCase.execute(new GetEventByIdUseCase.Input(created.eventId().value())).orElseThrow();
        Assertions.assertEquals(EventStatus.ACTIVE, active.status());

        active.cancel();
        eventRepository.update(active);
        final var cancelled = eventRepository.eventOfId(created.eventId()).orElseThrow();

        final var outbox = new ArrayList<OutboxEntity>();
        outboxJpaRepository.findAll().forEach(outbox::add);

        // then
        Assertions.assertEquals("ACTIVE", queried.status());
        Assertions.assertEquals(created.eventId().value(), queried.id());
        Assertions.assertEquals("Disney on Ice", queried.name());
        Assertions.assertEquals("2021-01-01", queried.date());
        Assertions.assertEquals(100, queried.totalSpots());
        Assertions.assertEquals(EventStatus.CANCELLED, cancelled.status());
        Assertions.assertTrue(outbox.stream().anyMatch(message ->
                message.content().contains("event.cancelled") && message.content().contains(created.eventId().value())));
    }

    @Test
    @DisplayName("Deve buscar somente os ingressos do evento no banco")
    public void testTicketsByEventId() {
        // given
        final var partner = Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com");
        final var event = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, partner));
        final var other = eventRepository.create(Event.newEvent("Other show", "2021-02-01", 10, partner));

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), other.eventId()));

        // when
        final var ofEvent = ticketRepository.ticketsByEventId(event.eventId());
        final var ofOther = ticketRepository.ticketsByEventId(other.eventId());

        // then
        Assertions.assertEquals(2, ofEvent.size());
        Assertions.assertTrue(ofEvent.stream().allMatch(ticket -> ticket.eventId().equals(event.eventId())));
        Assertions.assertEquals(1, ofOther.size());
        Assertions.assertEquals(other.eventId(), ofOther.get(0).eventId());
    }
}
