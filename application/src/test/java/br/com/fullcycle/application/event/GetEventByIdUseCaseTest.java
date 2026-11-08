package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;

class GetEventByIdUseCaseTest {

    @Test
    @DisplayName("Deve obter um evento ativo por id")
    public void testGetActiveEvent() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, aPartner);
        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new GetEventByIdUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(input).orElseThrow();

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(anEvent.name().value(), output.name());
        Assertions.assertEquals(anEvent.date().format(DateTimeFormatter.ISO_LOCAL_DATE), output.date());
        Assertions.assertEquals(anEvent.totalSpots(), output.totalSpots());
        Assertions.assertEquals(EventStatus.ACTIVE.name(), output.status());
    }

    @Test
    @DisplayName("Deve obter um evento cancelado por id")
    public void testGetCancelledEvent() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, aPartner);
        anEvent.cancel();
        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new GetEventByIdUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(input).orElseThrow();

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(anEvent.name().value(), output.name());
        Assertions.assertEquals(anEvent.date().format(DateTimeFormatter.ISO_LOCAL_DATE), output.date());
        Assertions.assertEquals(anEvent.totalSpots(), output.totalSpots());
        Assertions.assertEquals(EventStatus.CANCELLED.name(), output.status());
    }

    @Test
    @DisplayName("Deve obter vazio ao tentar recuperar um evento inexistente")
    public void testGetMissingEvent() {
        // given
        final var input = new GetEventByIdUseCase.Input(EventId.unique().value());
        final var eventRepository = new InMemoryEventRepository();

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertTrue(output.isEmpty());
    }
}
