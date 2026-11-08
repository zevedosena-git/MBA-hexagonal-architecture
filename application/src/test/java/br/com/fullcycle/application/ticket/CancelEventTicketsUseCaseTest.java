package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar somente os ingressos do evento")
    public void testCancelTicketsOfEvent() {
        // given
        final var eventId = EventId.unique();
        final var otherEventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), eventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), eventId));
        final var otherTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        final var loaded = ticketRepository.ticketsByEventId(eventId);

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(new CancelEventTicketsUseCase.Input(eventId.value()));

        // then
        Assertions.assertEquals(2, loaded.size());
        Assertions.assertTrue(loaded.stream().allMatch(ticket -> ticket.eventId().equals(eventId)));
        Assertions.assertEquals(eventId.value(), output.eventId());
        Assertions.assertTrue(ticketRepository.ticketsByEventId(eventId).stream()
                .allMatch(ticket -> ticket.status() == TicketStatus.CANCELLED));
        Assertions.assertEquals(TicketStatus.PENDING, ticketRepository.ticketOfId(otherTicket.ticketId()).get().status());
    }

    @Test
    @DisplayName("Deve concluir quando o evento não tem ingressos")
    public void testCancelWhenEventHasNoTickets() {
        // given
        final var eventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);

        // when
        final var output = useCase.execute(new CancelEventTicketsUseCase.Input(eventId.value()));

        // then
        Assertions.assertEquals(eventId.value(), output.eventId());
        Assertions.assertTrue(ticketRepository.ticketsByEventId(eventId).isEmpty());
    }

    @Test
    @DisplayName("Deve permanecer cancelado ao reprocessar")
    public void testCancelTicketsTwice() {
        // given
        final var eventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), eventId));
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var input = new CancelEventTicketsUseCase.Input(eventId.value());

        // when
        useCase.execute(input);
        final var output = Assertions.assertDoesNotThrow(() -> useCase.execute(input));

        // then
        Assertions.assertEquals(eventId.value(), output.eventId());
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketsByEventId(eventId).get(0).status());
    }
}
