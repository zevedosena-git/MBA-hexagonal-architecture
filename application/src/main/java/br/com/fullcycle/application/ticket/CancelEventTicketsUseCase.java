package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.UseCase;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.TicketRepository;

import java.util.Objects;

public class CancelEventTicketsUseCase
        extends UseCase<CancelEventTicketsUseCase.Input, CancelEventTicketsUseCase.Output> {

    private final TicketRepository ticketRepository;

    public CancelEventTicketsUseCase(final TicketRepository ticketRepository) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
    }

    @Override
    public Output execute(final Input input) {
        final var eventId = EventId.with(input.eventId());

        for (final var ticket : this.ticketRepository.ticketsByEventId(eventId)) {
            ticket.cancel();
            this.ticketRepository.update(ticket);
        }

        return new Output(input.eventId());
    }

    public record Input(String eventId) {
    }

    public record Output(String eventId) {
    }
}
