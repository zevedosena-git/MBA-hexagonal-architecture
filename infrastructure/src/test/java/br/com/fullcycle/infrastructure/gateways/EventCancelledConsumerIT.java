package br.com.fullcycle.infrastructure.gateways;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.application.event.CancelEventUseCase;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.infrastructure.jpa.entities.OutboxEntity;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.StreamSupport;

class EventCancelledConsumerIT extends IntegrationTest {

    @Autowired
    private ConsumerQueueGateway consumerQueueGateway;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private CancelEventUseCase cancelEventUseCase;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cancelar os ingressos ao publicar EventCancelled no gateway e permanecer idempotente")
    public void testPublishEventCancelledCancelsTickets() throws Exception {
        // given
        final var partner = Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com");
        final var event = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, partner));
        final var other = eventRepository.create(Event.newEvent("Other show", "2021-02-01", 10, partner));

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));
        final var otherTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), other.eventId()));

        final var content = mapper.writeValueAsString(new EventCancelled(event.eventId()));

        // when
        consumerQueueGateway.publish(content);
        final var cancelled = ticketRepository.ticketsByEventId(event.eventId());

        consumerQueueGateway.publish(content);
        final var cancelledAgain = ticketRepository.ticketsByEventId(event.eventId());

        // then
        Assertions.assertEquals(2, cancelled.size());
        Assertions.assertTrue(cancelled.stream().allMatch(ticket -> ticket.status() == TicketStatus.CANCELLED));
        Assertions.assertEquals(TicketStatus.PENDING, ticketRepository.ticketOfId(otherTicket.ticketId()).orElseThrow().status());
        Assertions.assertTrue(cancelledAgain.stream().allMatch(ticket -> ticket.status() == TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("Deve cancelar os ingressos a partir da mensagem gravada na outbox pelo cancelamento do evento")
    public void testCancelEventThroughOutboxCancelsTickets() throws Exception {
        // given
        final var partner = Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com");
        final var event = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, partner));

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), event.eventId()));

        cancelEventUseCase.execute(new CancelEventUseCase.Input(event.eventId().value()));

        final var stored = StreamSupport.stream(outboxJpaRepository.findAll().spliterator(), false)
                .map(OutboxEntity::content)
                .filter(content -> content.contains("event.cancelled") && content.contains(event.eventId().value()))
                .findFirst()
                .orElseThrow();

        // H2 persists a string bound to a JSON column as a JSON string literal; MySQL stores the object itself.
        final var json = mapper.readTree(stored);
        final var message = json.isTextual() ? json.asText() : stored;

        // when
        consumerQueueGateway.publish(message);

        // then
        final var tickets = ticketRepository.ticketsByEventId(event.eventId());
        Assertions.assertEquals(2, tickets.size());
        Assertions.assertTrue(tickets.stream().allMatch(ticket -> ticket.status() == TicketStatus.CANCELLED));
    }
}
