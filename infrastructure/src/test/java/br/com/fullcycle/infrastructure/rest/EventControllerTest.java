package br.com.fullcycle.infrastructure.rest;

import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.application.event.CreateEventUseCase;
import br.com.fullcycle.infrastructure.dtos.NewEventDTO;
import br.com.fullcycle.infrastructure.dtos.SubscribeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
@SpringBootTest
class EventControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GraphQlTester graphQlTester;

    private Customer johnDoe;
    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();

        johnDoe = customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com"));
        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve criar um evento")
    public void testCreate() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var result = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var actualResponse = mapper.readValue(result, NewEventDTO.class);
        Assertions.assertEquals(event.date(), actualResponse.date());
        Assertions.assertEquals(event.totalSpots(), actualResponse.totalSpots());
        Assertions.assertEquals(event.name(), actualResponse.name());
    }

    @Test
    @Transactional
    @DisplayName("Deve comprar um ticket de um evento")
    public void testReserveTicket() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var createResult = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var eventId = mapper.readValue(createResult, CreateEventUseCase.Output.class).id();

        var sub = new SubscribeDTO(johnDoe.customerId().value(), null);

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/subscribe", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(sub))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        var actualEvent = eventRepository.eventOfId(EventId.with(eventId)).get();
        Assertions.assertEquals(1, actualEvent.allTickets().size());
    }

    @Test
    @DisplayName("Deve consultar um evento por id")
    public void testGet() throws Exception {
        final var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(eventId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Disney on Ice"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.date").value("2021-01-01"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalSpots").value(100))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Deve consultar um evento por id com X-Public")
    public void testGetPublic() throws Exception {
        final var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", eventId)
                        .header("X-Public", "true"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(eventId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.date").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalSpots").doesNotExist());
    }

    @Test
    @DisplayName("Deve retornar 404 ao consultar um evento inexistente")
    public void testGetMissing() throws Exception {
        final var eventId = EventId.unique().value();

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", eventId))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", eventId)
                        .header("X-Public", "true"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));
    }

    @Test
    @DisplayName("Deve retornar 404 quando a consulta de evento falha")
    public void testGetInvalidId() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", "not-a-uuid"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", "not-a-uuid")
                        .header("X-Public", "true"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));
    }

    @Test
    @DisplayName("Deve cancelar um evento")
    public void testCancel() throws Exception {
        final var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(eventId))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelTwice() throws Exception {
        final var eventId = createEvent();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", eventId))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event already cancelled"));
    }

    @Test
    @DisplayName("Não deve cancelar um evento inexistente")
    public void testCancelMissing() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", EventId.unique().value()))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event not found"));
    }

    @Test
    @DisplayName("Deve criar, consultar e cancelar um evento via GraphQL")
    public void testGraphqlCancelAndQuery() {
        final var created = this.graphQlTester.document("""
                        mutation($input: EventInput) {
                          createEvent(input: $input) {
                            id
                            name
                            date
                            totalSpots
                            status
                          }
                        }
                        """)
                .variable("input", Map.of(
                        "name", "Disney on Ice",
                        "date", "2021-01-01",
                        "totalSpots", 100,
                        "partnerId", disney.partnerId().value()
                ))
                .execute();

        created.path("createEvent.name").entity(String.class).isEqualTo("Disney on Ice");
        created.path("createEvent.date").entity(String.class).isEqualTo("2021-01-01");
        created.path("createEvent.totalSpots").entity(Integer.class).isEqualTo(100);
        created.path("createEvent.status").valueIsNull();

        final var eventId = created.path("createEvent.id").entity(String.class).get();

        final var queried = this.graphQlTester.document("""
                        query($id: ID!) {
                          eventOfId(id: $id) {
                            id
                            name
                            date
                            totalSpots
                            status
                          }
                        }
                        """)
                .variable("id", eventId)
                .execute();

        queried.path("eventOfId.id").entity(String.class).isEqualTo(eventId);
        queried.path("eventOfId.name").entity(String.class).isEqualTo("Disney on Ice");
        queried.path("eventOfId.date").entity(String.class).isEqualTo("2021-01-01");
        queried.path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(100);
        queried.path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE");

        final var cancelled = this.graphQlTester.document("""
                        mutation($id: ID!) {
                          cancelEvent(id: $id) {
                            id
                            status
                          }
                        }
                        """)
                .variable("id", eventId)
                .execute();

        cancelled.path("cancelEvent.id").entity(String.class).isEqualTo(eventId);
        cancelled.path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");

        this.graphQlTester.document("""
                        query($id: ID!) {
                          eventOfId(id: $id) {
                            id
                            status
                          }
                        }
                        """)
                .variable("id", eventId)
                .execute()
                .path("eventOfId.status").entity(String.class).isEqualTo("CANCELLED");

        this.graphQlTester.document("""
                        query($id: ID!) {
                          eventOfId(id: $id) {
                            id
                          }
                        }
                        """)
                .variable("id", EventId.unique().value())
                .execute()
                .path("eventOfId").valueIsNull();
    }

    private String createEvent() throws Exception {
        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var result = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsByteArray();

        return mapper.readValue(result, CreateEventUseCase.Output.class).id();
    }
}