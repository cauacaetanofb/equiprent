package com.equiprent.catalog;

import com.equiprent.company.*;
import com.equiprent.equipment.*;
import com.equiprent.reservation.*;
import com.equiprent.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceAccessIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    private static final String PASSWORD = "Senha-exclusiva-teste-123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired CatalogService catalog;
    @Autowired RegistrationService registration;
    @Autowired CompanyLocationService location;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    User adminA, adminB, client;
    Equipment itemA, itemB;
    Long companyB;
    LocalDate today;

    @BeforeEach void prepare() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE chat_message, reservation_offer, reservation_event, reservation, equipment, app_user, company_membership, company_customer RESTART IDENTITY");
        jdbc.update("DELETE FROM company WHERE code <> 'INITIAL'");
        jdbc.update("UPDATE company SET name='Empresa A', active=true, city='Curitiba', state='PR' WHERE id=1");
        companyB = jdbc.queryForObject("INSERT INTO company(code,name,city,state) VALUES ('B','Empresa B','Campinas','SP') RETURNING id", Long.class);
        var hash = encoder.encode(PASSWORD);
        adminA = users.saveAndFlush(new User("Admin A", "admin-a@example.test", hash, UserRole.ADMIN, clock.instant()));
        adminB = users.saveAndFlush(new User("Admin B", "admin-b@example.test", hash, UserRole.ADMIN, clock.instant()));
        client = users.saveAndFlush(new User("Cliente privado", "cliente@example.test", hash, UserRole.CLIENTE, clock.instant()));
        jdbc.update("INSERT INTO company_membership(user_id,company_id) VALUES (?,1),(?,?)", adminA.getId(), adminB.getId(), companyB);
        itemA = equipment.saveAndFlush(new Equipment(1L, "SEGREDO-A", "Notebook A", "Descrição pública A", EquipmentCategory.NOTEBOOK, new BigDecimal("20.00"), clock.instant()));
        itemB = equipment.saveAndFlush(new Equipment(companyB, "SEGREDO-B", "Notebook B", "Descrição pública B", EquipmentCategory.NOTEBOOK, new BigDecimal("30.00"), clock.instant()));
        today = LocalDate.now(clock);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void shouldPublishOnlyCustomerFacingDataAcrossCompanies() throws Exception {
        mvc.perform(get("/catalogo")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Empresa A")))
                .andExpect(content().string(containsString("Empresa B")))
                .andExpect(content().string(containsString("Curitiba / PR")))
                .andExpect(content().string(not(containsString("SEGREDO-"))))
                .andExpect(content().string(not(containsString("cliente@example.test"))))
                .andExpect(content().string(not(containsString("/admin/"))));
        mvc.perform(get("/catalogo/{id}", itemA.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Descrição pública A")))
                .andExpect(content().string(not(containsString("SEGREDO-A"))));
        assertThat(catalog.search(new CatalogFilter()).getTotalElements()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"MAINTENANCE","RETIRED"})
    void shouldHideNonOperationalEquipmentEvenThroughDirectUrls(String condition) throws Exception {
        jdbc.update("UPDATE equipment SET condition=? WHERE id=?", condition, itemA.getId());
        assertThat(catalog.search(new CatalogFilter()).getContent()).extracting(CatalogItem::id).containsExactly(itemB.getId());
        mvc.perform(get("/catalogo/{id}", itemA.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/catalogo/{id}/continuar", itemA.getId())).andExpect(status().isNotFound());
    }

    @Test void shouldHideInactiveCompaniesAndPreserveUnknownLocations() throws Exception {
        jdbc.update("UPDATE company SET active=false WHERE id=?", companyB);
        jdbc.update("UPDATE company SET city=null,state=null WHERE id=1");
        mvc.perform(get("/catalogo")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Localização não informada")))
                .andExpect(content().string(not(containsString("Empresa B"))));
        mvc.perform(get("/catalogo/{id}", itemB.getId())).andExpect(status().isNotFound());
        var filter = new CatalogFilter(); filter.setState(BrazilState.PR);
        assertThat(catalog.search(filter).getTotalElements()).isZero();
    }

    @Test void shouldFilterByNameCategoryCityAndStateAndEscapeWildcards() {
        var filter = new CatalogFilter(); filter.setCity("cUrIt"); filter.setState(BrazilState.PR);
        filter.setQ("NOTEBOOK"); filter.setCategory(EquipmentCategory.NOTEBOOK);
        assertThat(catalog.search(filter).getContent()).extracting(CatalogItem::id).containsExactly(itemA.getId());
        filter.setState(BrazilState.SP); assertThat(catalog.search(filter).getTotalElements()).isZero();
        filter = new CatalogFilter(); filter.setCategory(EquipmentCategory.CAMERA);
        assertThat(catalog.search(filter).getTotalElements()).isZero();
        for (String q : List.of("SEGREDO", "%", "_", "!")) {
            filter = new CatalogFilter(); filter.setQ(q);
            assertThat(catalog.search(filter).getTotalElements()).isZero();
        }
    }

    @Test void shouldPaginateAndSortWithStableCountsAndRetainFilters() throws Exception {
        var filter = period(2,4); filter.setSize(1); filter.setSort("priceDesc");
        var first = catalog.search(filter);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).extracting(CatalogItem::id).containsExactly(itemB.getId());
        assertThat(first.getContent().getFirst().total()).isEqualByComparingTo("90.00");
        filter.setPage(1);
        assertThat(catalog.search(filter).getContent()).extracting(CatalogItem::id).containsExactly(itemA.getId());
        mvc.perform(get("/catalogo").param("size","1").param("city","Curitiba").param("state","PR").param("page","99"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Primeira página")))
                .andExpect(content().string(containsString("city=Curitiba")));
    }

    @ParameterizedTest @CsvSource({"3,4,true","3,5,false","7,8,false","8,9,true","5,7,false"})
    void shouldUseInclusiveAvailabilityAndSameRuleOnDetails(int start, int end, boolean available) {
        reserve(5,7,"RESERVED");
        var filter = period(start,end);
        assertThat(catalog.search(filter).getContent().stream().anyMatch(i -> i.id().equals(itemA.getId()))).isEqualTo(available);
        if (available) assertThat(catalog.estimate(itemA.getId(),filter).days()).isEqualTo(end-start+1L);
        else assertThatThrownBy(() -> catalog.estimate(itemA.getId(),filter)).isInstanceOf(ReservationRuleException.class);
    }

    @ParameterizedTest @CsvSource({"RESERVED,false","IN_USE,false","RETURNED,true","CANCELLED,true"})
    void shouldRespectReservationStates(String state, boolean available) {
        reserve(2,4,state);
        assertThat(catalog.search(period(2,4)).getContent().stream().anyMatch(i -> i.id().equals(itemA.getId()))).isEqualTo(available);
    }

    @Test void shouldBlockOverdueUseEvenOutsideRequestedDates() {
        reserve(-5,-1,"IN_USE");
        assertThat(catalog.search(period(50,51)).getContent()).extracting(CatalogItem::id).containsExactly(itemB.getId());
    }

    @Test void shouldRejectPastReversedMissingAndTooLongDatesAndOverflow() throws Exception {
        for (var filter : List.of(period(-1,2),period(3,2),period(1,366))) {
            assertThatThrownBy(() -> catalog.search(filter)).isInstanceOf(ReservationRuleException.class);
        }
        var missing = new CatalogFilter(); missing.setStartDate(today);
        assertThatThrownBy(() -> catalog.search(missing)).isInstanceOf(ReservationRuleException.class);
        mvc.perform(get("/catalogo").param("startDate","invalid").param("state","XX"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("filter","startDate","state"));
        mvc.perform(get("/catalogo/{id}/continuar",itemA.getId())).andExpect(status().isOk())
                .andExpect(model().hasErrors());
        jdbc.update("UPDATE equipment SET daily_rate=9999999999.99 WHERE id=?", itemA.getId());
        assertThatThrownBy(() -> catalog.estimate(itemA.getId(),period(0,364))).isInstanceOf(ReservationRuleException.class);
    }

    @Test void shouldEscapePublishedTextAndKeepDestinationsRelative() throws Exception {
        jdbc.update("UPDATE equipment SET description='<script>alert(1)</script>' WHERE id=?", itemA.getId());
        mvc.perform(get("/catalogo/{id}",itemA.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))));
        var filter = period(2,4); filter.setQ("x&returnUrl=https://example.test");
        assertThat(URI.create(filter.searchUrl(0)).isAbsolute()).isFalse();
        assertThat(filter.searchUrl(0)).contains("%26returnUrl%3D");
    }

    @Test void shouldReturnToSelectedEquipmentAfterLoginFailureThenSuccess() throws Exception {
        var filter = period(2,4); filter.setQ("Notebook"); filter.setCity("Curitiba"); filter.setState(BrazilState.PR);
        var session = beginJourney(filter);
        mvc.perform(post("/login").session(session).with(csrf()).param("email",client.getEmail()).param("password","errada"))
                .andExpect(redirectedUrl("/login?error"));
        var loggedIn = login(session, client.getEmail(), CatalogJourney.reservationUrl(itemA.getId(),filter));
        mvc.perform(get(URI.create(CatalogJourney.reservationUrl(itemA.getId(),filter))).session(loggedIn))
                .andExpect(status().isOk()).andExpect(model().attributeExists("estimate"))
                .andExpect(model().attribute("catalogBackUrl",filter.searchUrl(0)))
                .andExpect(content().string(containsString("60,00")));
        assertThat(reservations.count()).isZero();
    }

    @Test void shouldKeepJourneyAcrossRegistrationAndCreateCustomerWithoutCompanyPrivileges() throws Exception {
        var filter = period(2,4); var session = beginJourney(filter);
        mvc.perform(post("/cadastro").session(session).with(csrf())
                        .param("name"," Nova cliente ").param("email"," NOVA@EXAMPLE.TEST ")
                        .param("password",PASSWORD).param("passwordConfirmation",PASSWORD)
                        .param("role","ADMIN").param("companyId",companyB.toString()).param("active","false"))
                .andExpect(redirectedUrl("/login")).andExpect(flash().attribute("registered",true));
        var created = users.findByEmail("nova@example.test").orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.CLIENTE);
        assertThat(created.isActive()).isTrue();
        assertThat(encoder.matches(PASSWORD,created.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_membership WHERE user_id=?",Long.class,created.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company_customer WHERE user_id=?",Long.class,created.getId())).isZero();
        var loggedIn = login(session,created.getEmail(),CatalogJourney.reservationUrl(itemA.getId(),filter));
        var quotePage=mvc.perform(get(URI.create(CatalogJourney.reservationUrl(itemA.getId(),filter))).session(loggedIn)).andExpect(status().isOk()).andReturn();
        var quoted=(ReservationForm)quotePage.getModelAndView().getModel().get("reservationForm");
        mvc.perform(post("/reservas").session(loggedIn).with(csrf()).param("quoteToken",quoted.getQuoteToken()).param("equipmentId",itemA.getId().toString())
                        .param("startDate",filter.getStartDate().toString()).param("endDate",filter.getEndDate().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT company_id FROM company_customer WHERE user_id=?",Long.class,created.getId())).isEqualTo(1);
        mvc.perform(get("/admin/empresa").session(loggedIn)).andExpect(status().isForbidden());
    }

    @Test void shouldResumeSearchAfterNavbarLoginAndPreventArbitraryRedirects() throws Exception {
        var filter = period(2,4); filter.setQ("Notebook");
        var session = (MockHttpSession)mvc.perform(get(URI.create(filter.searchUrl(0)))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        login(session,client.getEmail(),filter.searchUrl(0));
        mvc.perform(post("/login").with(csrf()).param("email",client.getEmail()).param("password",PASSWORD)
                .param("returnUrl","https://example.test").param("redirect","//example.test"))
                .andExpect(redirectedUrl("/conta"));
    }

    @Test void shouldSendAdminBackToPublicDetailsInsteadOfCustomerReservation() throws Exception {
        var filter = period(2,4);
        login(beginJourney(filter),adminA.getEmail(),filter.detailsUrl(itemA.getId()));
    }

    @Test void shouldRecheckAvailabilityAfterLoginWithoutCreatingReservation() throws Exception {
        var filter = period(2,4); var session = beginJourney(filter);
        reserve(2,4,"RESERVED");
        var loggedIn = login(session,client.getEmail(),CatalogJourney.reservationUrl(itemA.getId(),filter));
        mvc.perform(get(URI.create(CatalogJourney.reservationUrl(itemA.getId(),filter))).session(loggedIn))
                .andExpect(status().isOk()).andExpect(model().hasErrors()).andExpect(model().attributeDoesNotExist("estimate"));
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test void shouldRejectInvalidPasswordsDuplicateEmailsAndAvoidEchoingSecrets() throws Exception {
        mvc.perform(post("/cadastro").with(csrf()).param("name","Nova").param("email","nova@example.test")
                .param("password",PASSWORD).param("passwordConfirmation","outra-senha-longa"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrationForm","passwordConfirmed"))
                .andExpect(content().string(not(containsString(PASSWORD))));
        mvc.perform(post("/cadastro").with(csrf()).param("name"," ").param("email","invalido")
                .param("password","curta").param("passwordConfirmation","curta"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrationForm","name","email","password"));
        var hashBefore = adminA.getPasswordHash();
        mvc.perform(post("/cadastro").with(csrf()).param("name","Outra pessoa").param("email"," ADMIN-A@EXAMPLE.TEST ")
                .param("password",PASSWORD).param("passwordConfirmation",PASSWORD))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrationForm","email"));
        assertThat(users.count()).isEqualTo(3);
        assertThat(users.findById(adminA.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
    }

    @Test void shouldRequireCsrfAndDisallowRegistrationWhileAuthenticated() throws Exception {
        mvc.perform(post("/cadastro").param("name","Nova").param("email","nova@example.test")
                .param("password",PASSWORD).param("passwordConfirmation",PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/cadastro").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings={"/conta","/reservas","/reservas/1","/admin/clientes","/admin/empresa","/equipamentos"})
    void shouldKeepPrivateRoutesProtected(String route) throws Exception {
        mvc.perform(get(route)).andExpect(redirectedUrl("/login"));
    }

    @Test void shouldHandleConcurrentRegistrationForSameNormalizedEmail() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> attempt = () -> {
                var form = new RegistrationForm(); form.setName("Cliente"); form.setEmail(" NOVA@EXAMPLE.TEST ");
                form.setPassword(PASSWORD); form.setPasswordConfirmation(PASSWORD);
                barrier.await(10,TimeUnit.SECONDS);
                try { registration.register(form); return "created"; }
                catch (DuplicateEmailException exception) { return "duplicate"; }
            };
            var one=executor.submit(attempt); var two=executor.submit(attempt);
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("created","duplicate");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email='nova@example.test'",Long.class)).isEqualTo(1);
    }

    @Test void shouldUpdateOnlyOwnLocationAndReflectItInPublicSearch() throws Exception {
        mvc.perform(get("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Curitiba")));
        mvc.perform(post("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN")).with(csrf())
                .param("city"," Londrina ").param("state","PR").param("companyId",companyB.toString()).param("name","FORJADO"))
                .andExpect(redirectedUrl("/admin/empresa"));
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=1",String.class)).isEqualTo("Londrina");
        assertThat(jdbc.queryForObject("SELECT name FROM company WHERE id=1",String.class)).isEqualTo("Empresa A");
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=?",String.class,companyB)).isEqualTo("Campinas");
        var filter = new CatalogFilter(); filter.setCity("Londrina");
        assertThat(catalog.search(filter).getContent()).extracting(CatalogItem::id).containsExactly(itemA.getId());
        mvc.perform(post("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN")).with(csrf()).param("city","").param("state",""))
                .andExpect(redirectedUrl("/admin/empresa"));
        assertThat(catalog.details(itemA.getId()).city()).isNull();
    }

    @ParameterizedTest @CsvSource({"Curitiba,'',locationComplete","'',PR,locationComplete","Curitiba,XX,state"})
    void shouldValidateLocationPairAndState(String city,String state,String error) throws Exception {
        mvc.perform(post("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN")).with(csrf())
                .param("city",city).param("state",state)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("locationForm",error));
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=1",String.class)).isEqualTo("Curitiba");
    }

    @Test void shouldRequireActiveAdminAndCsrfToUpdateLocation() throws Exception {
        mvc.perform(post("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN")).param("city","Londrina").param("state","PR"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/empresa").with(user(client.getEmail()).roles("CLIENTE")).with(csrf())).andExpect(status().isForbidden());
        jdbc.update("UPDATE company_membership SET active=false WHERE user_id=?",adminA.getId());
        mvc.perform(post("/admin/empresa").with(user(adminA.getEmail()).roles("ADMIN")).with(csrf()).param("city","Londrina").param("state","PR"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=1",String.class)).isEqualTo("Curitiba");
    }

    @Test void shouldSerializeSimultaneousLocationUpdates() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> attempt = () -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(adminA.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                try {
                    var form = new CompanyLocationForm(); form.setCity("Londrina"); form.setState(BrazilState.PR);
                    barrier.await(10,TimeUnit.SECONDS); location.update(form); return null;
                } finally { SecurityContextHolder.clearContext(); }
            };
            var one=executor.submit(attempt); var two=executor.submit(attempt);
            one.get(20,TimeUnit.SECONDS); two.get(20,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=1",String.class)).isEqualTo("Londrina");
    }

    private CatalogFilter period(int start, int end) {
        var filter = new CatalogFilter(); filter.setStartDate(today.plusDays(start)); filter.setEndDate(today.plusDays(end)); return filter;
    }
    private void reserve(int start,int end,String status) {
        var reservation=reservations.saveAndFlush(new Reservation(client,itemA,today.plusDays(start),today.plusDays(end),new BigDecimal("20.00"),new BigDecimal("60.00"),clock.instant()));
        switch(status) {
            case "IN_USE" -> jdbc.update("UPDATE reservation SET status='IN_USE',picked_up_at=created_at WHERE id=?",reservation.getId());
            case "RETURNED" -> jdbc.update("UPDATE reservation SET status='RETURNED',picked_up_at=created_at,returned_at=created_at WHERE id=?",reservation.getId());
            case "CANCELLED" -> jdbc.update("UPDATE reservation SET status='CANCELLED',cancelled_at=created_at WHERE id=?",reservation.getId());
            default -> { }
        }
    }
    private MockHttpSession beginJourney(CatalogFilter filter) throws Exception {
        return (MockHttpSession)mvc.perform(get(URI.create(filter.continueUrl(itemA.getId())))).andExpect(redirectedUrl("/login")).andReturn().getRequest().getSession(false);
    }
    private MockHttpSession login(MockHttpSession session,String email,String destination) throws Exception {
        return (MockHttpSession)mvc.perform(post("/login").session(session).with(csrf()).param("email",email).param("password",PASSWORD))
                .andExpect(redirectedUrl(destination)).andReturn().getRequest().getSession(false);
    }
}
