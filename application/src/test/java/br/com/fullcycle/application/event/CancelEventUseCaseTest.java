package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventUseCaseTest {

    @Test
    @DisplayName("Deve cancelar um evento")
    public void testCancelEvent() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var ticketRepository = new InMemoryTicketRepository();
        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEvent.eventId()));

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(EventStatus.CANCELLED.name(), output.status());
        Assertions.assertEquals(EventStatus.CANCELLED, eventRepository.eventOfId(anEvent.eventId()).get().status());
        Assertions.assertEquals(TicketStatus.PENDING, ticketRepository.ticketOfId(aTicket.ticketId()).get().status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento inexistente")
    public void testCancelMissingEvent() {
        // given
        final var expectedError = "Event not found";
        final var input = new CancelEventUseCase.Input(EventId.unique().value());
        final var eventRepository = new InMemoryEventRepository();

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var actualError = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualError.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelAlreadyCancelledEvent() {
        // given
        final var expectedError = "Event already cancelled";
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var actualError = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualError.getMessage());
        Assertions.assertEquals(EventStatus.CANCELLED, eventRepository.eventOfId(anEvent.eventId()).get().status());
    }
}
