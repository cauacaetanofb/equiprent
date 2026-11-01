package com.equiprent.reservation;

import com.equiprent.company.CompanyPolicy;
import com.equiprent.equipment.*;
import com.equiprent.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
@Import(ReservationNegotiationIT.TimeConfiguration.class)
class ReservationNegotiationIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    static class TestClock extends Clock {
        volatile Instant now=Instant.parse("2026-11-01T12:00:00Z");
        public ZoneId getZone(){return ZoneId.of("America/Sao_Paulo");}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary TestClock clock(){return new TestClock();} }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationOfferRepository offers;
    @Autowired ReservationEventRepository events;
    @Autowired ReservationService creation;
    @Autowired ReservationNegotiationService negotiation;
    @Autowired ReservationManagementService management;
    @Autowired ReservationOperationsService operations;
    @Autowired EquipmentService equipmentService;
    @Autowired TestClock clock;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired UserService userService;
    User client,other,adminA,adminB;
    Equipment itemA,itemB;
    Long companyB;
    static final LocalDate START=LocalDate.of(2026,11,3), END=LocalDate.of(2026,11,5);

    @BeforeEach void prepare(){
        SecurityContextHolder.clearContext();clock.now=Instant.parse("2026-11-01T12:00:00Z");
        jdbc.execute("TRUNCATE reservation_offer,reservation_event,reservation,equipment,app_user,company_membership,company_customer RESTART IDENTITY");
        jdbc.update("DELETE FROM company WHERE code<>'INITIAL'");jdbc.update("UPDATE company SET active=true WHERE id=1");
        companyB=jdbc.queryForObject("INSERT INTO company(code,name) VALUES ('B','Empresa B') RETURNING id",Long.class);
        client=account("cliente",UserRole.CLIENTE);other=account("outro",UserRole.CLIENTE);adminA=account("admin-a",UserRole.ADMIN);adminB=account("admin-b",UserRole.ADMIN);
        jdbc.update("INSERT INTO company_membership(user_id,company_id) VALUES (?,1),(?,?)",adminA.getId(),adminB.getId(),companyB);
        itemA=equipment.saveAndFlush(new Equipment(1L,"INTERNO-A","Notebook A","Descrição",EquipmentCategory.NOTEBOOK,new BigDecimal("50.00"),clock.instant()));
        itemB=equipment.saveAndFlush(new Equipment(companyB,"INTERNO-B","Notebook B","Descrição",EquipmentCategory.NOTEBOOK,new BigDecimal("60.00"),clock.instant()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private User account(String name,UserRole role){return users.saveAndFlush(new User(name,name+"@example.test","hash-teste",role,clock.instant()));}
    private void auth(User user){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name()))));}
    private ReservationForm form(Equipment item){var form=new ReservationForm();form.setEquipmentId(item.getId());form.setStartDate(START);form.setEndDate(END);return form;}
    private Long request(User user,Equipment item){auth(user);var form=form(item);return creation.create(form,creation.quote(form));}
    private OfferVersionForm version(int n){var form=new OfferVersionForm();form.setVersion(n);return form;}
    private OfferForm proposal(int version,String rate,String terms){var form=new OfferForm();form.setVersion(version);form.setDailyRate(new BigDecimal(rate));form.setTerms(terms);return form;}
    private RejectionForm rejection(String reason){var form=new RejectionForm();form.setReason(reason);return form;}
    private Reservation saved(Long id){return reservations.findById(id).orElseThrow();}

    @Test void shouldCreateAcceptedInitialProposalWithoutBlockingOrContract(){
        var id=request(client,itemA);var saved=saved(id);
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(saved.getContractDailyRate()).isNull();assertThat(saved.getApprovedAt()).isNull();
        assertThat(saved.getDailyRateSnapshot()).isEqualByComparingTo("50.00");
        var offer=offers.findByReservationIdAndVersion(id,1).orElseThrow();
        assertThat(offer.getAmount()).isEqualByComparingTo("150.00");assertThat(offer.getAcceptedAt()).isEqualTo(clock.instant());
        assertThat(offer.getAcceptedBy().getId()).isEqualTo(client.getId());
        assertThat(reservations.hasBlockingReservation(itemA.getId(),START,END,LocalDate.now(clock))).isFalse();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
    }
    @Test void shouldRequireLatestAcceptanceAndPreserveInitialAndContractPrices(){
        var id=request(client,itemA);auth(adminA);negotiation.propose(id,proposal(1,"40.00","Retirada na empresa."));
        assertThatThrownBy(()->negotiation.approve(id,version(2))).isInstanceOf(ReservationRuleException.class);
        auth(client);assertThatThrownBy(()->negotiation.accept(id,version(1))).isInstanceOf(ReservationRuleException.class);
        negotiation.accept(id,version(2));auth(adminA);negotiation.approve(id,version(2));
        var saved=saved(id);assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getDailyRateSnapshot()).isEqualByComparingTo("50.00");assertThat(saved.getEstimatedAmount()).isEqualByComparingTo("150.00");
        assertThat(saved.getContractDailyRate()).isEqualByComparingTo("40.00");assertThat(saved.getContractAmount()).isEqualByComparingTo("120.00");
        assertThat(saved.getContractTerms()).isEqualTo("Retirada na empresa.");
        jdbc.update("UPDATE equipment SET daily_rate=99.00 WHERE id=?",itemA.getId());
        clock.now=START.atTime(12,0).toInstant(ZoneOffset.UTC);operations.pickUp(id);operations.returnEquipment(id,newReturn());
        assertThat(saved(id).getContractAmount()).isEqualByComparingTo("120.00");
        auth(client);assertThat(management.details(id,false).summary().total()).isEqualByComparingTo("120.00");
    }
    @Test void shouldPreserveAcceptanceHistoryButRequireNewAcceptanceEvenIfOnlyTermsChange(){
        var id=request(client,itemA);auth(adminA);negotiation.propose(id,proposal(1,"50.00","Devolver limpo."));
        assertThat(offers.findByReservationIdAndVersion(id,1).orElseThrow().getAcceptedAt()).isNotNull();
        assertThat(offers.findByReservationIdAndVersion(id,2).orElseThrow().getAcceptedAt()).isNull();
        assertThatThrownBy(()->negotiation.approve(id,version(1))).isInstanceOf(ReservationRuleException.class);
        assertThatThrownBy(()->negotiation.approve(id,version(2))).isInstanceOf(ReservationRuleException.class);
    }
    @Test void shouldKeepRepeatedProposalAcceptanceAndApprovalIdempotent(){
        var id=request(client,itemA);auth(adminA);var form=proposal(1,"40.00","Condições");
        negotiation.propose(id,form);negotiation.propose(id,form);assertThat(offers.count()).isEqualTo(2);
        auth(client);negotiation.accept(id,version(2));var accepted=offers.findByReservationIdAndVersion(id,2).orElseThrow().getAcceptedAt();
        clock.now=clock.now.plusSeconds(10);negotiation.accept(id,version(2));
        assertThat(offers.findByReservationIdAndVersion(id,2).orElseThrow().getAcceptedAt()).isEqualTo(accepted);
        auth(adminA);negotiation.approve(id,version(2));negotiation.approve(id,version(2));
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);
    }
    @Test void shouldReturnExistingPendingRequestWithoutChangingNegotiation(){
        auth(client);var form=form(itemA);var quote=creation.quote(form);var id=creation.create(form,quote);
        assertThat(creation.create(form,quote)).isEqualTo(id);
        auth(adminA);negotiation.propose(id,proposal(1,"45.00","Novas condições"));
        auth(client);assertThat(creation.create(form,creation.quote(form))).isEqualTo(id);
        assertThat(saved(id).getCurrentOfferVersion()).isEqualTo(2);
        assertThat(offers.findByReservationIdAndVersion(id,2).orElseThrow().getAcceptedAt()).isNull();
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void shouldNotRecreateRequestWhenSameSubmissionIsRetriedAfterApprovalOrCancellation(){
        auth(client);var form=form(itemA);var quote=creation.quote(form);var id=creation.create(form,quote);
        auth(adminA);negotiation.approve(id,version(1));auth(client);assertThat(creation.create(form,quote)).isEqualTo(id);
        management.cancel(id,new CancellationForm(),false);assertThat(creation.create(form,quote)).isEqualTo(id);
        var next=creation.create(form,creation.quote(form));assertThat(next).isNotEqualTo(id);assertThat(reservations.count()).isEqualTo(2);
    }
    @Test void shouldRejectMissingForeignChangedAndExpiredQuote(){
        auth(client);var form=form(itemA);var quote=creation.quote(form);
        assertThatThrownBy(()->creation.create(form,null)).isInstanceOf(ReservationRuleException.class);
        auth(other);assertThatThrownBy(()->creation.create(form,quote)).isInstanceOf(ReservationRuleException.class);
        auth(client);var changed=form(itemA);changed.setEndDate(END.plusDays(1));
        assertThatThrownBy(()->creation.create(changed,quote)).isInstanceOf(ReservationRuleException.class);
        jdbc.update("UPDATE equipment SET daily_rate=51 WHERE id=?",itemA.getId());
        assertThatThrownBy(()->creation.create(form,quote)).isInstanceOf(ReservationRuleException.class);
        jdbc.update("UPDATE equipment SET daily_rate=50 WHERE id=?",itemA.getId());clock.now=clock.now.plusSeconds(1801);
        assertThatThrownBy(()->creation.create(form,quote)).isInstanceOf(ReservationRuleException.class);
        assertThat(reservations.count()).isZero();assertThat(offers.count()).isZero();
    }
    @Test void shouldRequireReasonForRejectionAndPreserveItOnRetry(){
        var id=request(client,itemA);auth(adminA);
        assertThatThrownBy(()->negotiation.reject(id,rejection(" "))).isInstanceOf(ConstraintViolationException.class);
        negotiation.reject(id,rejection("Equipamento em revisão."));negotiation.reject(id,rejection("Outra tentativa"));
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REJECTED);
        var history=events.findAllByReservationIdOrderByOccurredAtAscIdAsc(id);assertThat(history).hasSize(2);
        assertThat(history.getLast().getNote()).isEqualTo("Equipamento em revisão.");assertThat(saved(id).getContractAmount()).isNull();
    }
    @Test void shouldAllowCustomerToCancelPendingAndDisallowCompanyCancelShortcut(){
        var id=request(client,itemA);auth(adminA);var cancel=new CancellationForm();cancel.setReason("Motivo");
        assertThatThrownBy(()->management.cancel(id,cancel,true)).isInstanceOf(ReservationRuleException.class);
        auth(client);management.cancel(id,new CancellationForm(),false);
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.CANCELLED);assertThat(saved(id).getContractAmount()).isNull();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(id).getLast().getPreviousStatus()).isEqualTo(ReservationStatus.REQUESTED);
    }
    @Test void shouldSignalExpiredRequestAndAllowRejectionOrCancellationOnly(){
        var first=request(client,itemA);var second=request(other,itemA);
        clock.now=START.plusDays(1).atStartOfDay(clock.getZone()).toInstant();auth(adminA);
        assertThat(negotiation.details(first,true,0).deadlinePassed()).isTrue();
        assertThatThrownBy(()->negotiation.approve(first,version(1))).isInstanceOf(ReservationRuleException.class);
        assertThatThrownBy(()->negotiation.propose(first,proposal(1,"40","Novo"))).isInstanceOf(ReservationRuleException.class);
        auth(client);assertThatThrownBy(()->negotiation.accept(first,version(1))).isInstanceOf(ReservationRuleException.class);
        management.cancel(first,new CancellationForm(),false);auth(adminA);negotiation.reject(second,rejection("Prazo encerrado"));
        assertThat(saved(first).getStatus()).isEqualTo(ReservationStatus.CANCELLED);assertThat(saved(second).getStatus()).isEqualTo(ReservationStatus.REJECTED);
    }
    @ParameterizedTest @ValueSource(strings={"company","company_membership","app_user","company_customer"})
    void shouldRevalidateAllActivityBeforeApproval(String table){
        var id=request(client,itemA);
        switch(table){
            case "company" -> jdbc.update("UPDATE company SET active=false WHERE id=1");
            case "company_membership" -> jdbc.update("UPDATE company_membership SET active=false WHERE user_id=?",adminA.getId());
            case "app_user" -> jdbc.update("UPDATE app_user SET active=false WHERE id=?",client.getId());
            default -> jdbc.update("UPDATE company_customer SET active=false WHERE company_id=1 AND user_id=?",client.getId());
        }
        auth(adminA);assertThatThrownBy(()->negotiation.approve(id,version(1))).isInstanceOf(AccessDeniedException.class);
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);assertThat(saved(id).getContractAmount()).isNull();
    }
    @Test void shouldKeepConflictingRequestsAndAllowOnlyOneApproval(){
        var first=request(client,itemA);var second=request(other,itemA);auth(adminA);negotiation.approve(first,version(1));
        assertThatThrownBy(()->negotiation.approve(second,version(1))).isInstanceOf(ReservationRuleException.class);
        assertThat(saved(second).getStatus()).isEqualTo(ReservationStatus.REQUESTED);assertThat(negotiation.details(second,true,0).unavailable()).isTrue();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(second)).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"MAINTENANCE","RETIRED"})
    void shouldRevalidateEquipmentConditionAtApproval(String condition){
        var id=request(client,itemA);jdbc.update("UPDATE equipment SET condition=? WHERE id=?",condition,itemA.getId());auth(adminA);
        assertThatThrownBy(()->negotiation.approve(id,version(1))).isInstanceOf(ReservationRuleException.class);
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);
    }
    @Test void shouldValidateMoneyTermsAndPreventChangesAfterApproval(){
        var id=request(client,itemA);auth(adminA);
        for(String rate:List.of("0","-1","10.001","10000000000")) assertThatThrownBy(()->negotiation.propose(id,proposal(1,rate,""))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(()->negotiation.propose(id,proposal(1,"40","x".repeat(2001)))).isInstanceOf(ConstraintViolationException.class);
        negotiation.approve(id,version(1));
        assertThatThrownBy(()->negotiation.propose(id,proposal(1,"40","Novo"))).isInstanceOf(ReservationRuleException.class);
        assertThatThrownBy(()->negotiation.reject(id,rejection("Tarde"))).isInstanceOf(ReservationRuleException.class);
        auth(client);assertThatThrownBy(()->negotiation.accept(id,version(1))).isInstanceOf(ReservationRuleException.class);
    }
    @Test void shouldKeepNegotiationPrivateByCustomerAndCompanyEvenWithInvalidForms() throws Exception {
        var id=request(client,itemA);auth(adminB);
        assertThatThrownBy(()->negotiation.details(id,true,0)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(()->negotiation.approve(id,version(1))).isInstanceOf(ReservationNotFoundException.class);
        auth(other);assertThatThrownBy(()->negotiation.accept(id,version(1))).isInstanceOf(ReservationNotFoundException.class);
        mvc.perform(post("/admin/reservas/{id}/propor",id).with(user(adminB.getEmail()).roles("ADMIN")).with(csrf()).param("dailyRate","invalid"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reservas/{id}/aceitar",id).with(user(other.getEmail()).roles("CLIENTE")).with(csrf()).param("version","invalid"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/reservas/{id}/aprovar",id).with(user(client.getEmail()).roles("CLIENTE")).with(csrf()).param("version","1"))
                .andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"propor","aprovar","recusar"})
    void shouldRequireCsrfForCompanyActions(String action) throws Exception {
        var id=request(client,itemA);mvc.perform(post("/admin/reservas/"+id+"/"+action).with(user(adminA.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);
    }
    @Test void shouldIgnoreQuoteTokenSuppliedInConsultationUrl() throws Exception {
        mvc.perform(get("/reservas/nova").with(user(client.getEmail()).roles("CLIENTE"))
                .param("equipmentId",itemA.getId().toString()).param("startDate",START.toString())
                .param("endDate",END.toString()).param("quoteToken","x".repeat(100)))
                .andExpect(status().isOk()).andExpect(result->{
                    var form=(ReservationForm)result.getModelAndView().getModel().get("reservationForm");
                    assertThat(form.getQuoteToken()).hasSize(36);
                });
    }
    @Test void shouldRequireCsrfForAcceptanceAndQuoteForSubmission() throws Exception {
        var id=request(client,itemA);
        mvc.perform(post("/reservas/{id}/aceitar",id).with(user(client.getEmail()).roles("CLIENTE")).param("version","1")).andExpect(status().isForbidden());
        mvc.perform(post("/reservas").with(user(other.getEmail()).roles("CLIENTE")).with(csrf()).param("equipmentId",itemA.getId().toString())
                .param("startDate",START.toString()).param("endDate",END.toString()).param("quoteToken",UUID.randomUUID().toString()).param("dailyRate","0.01"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void shouldRenderNegotiationAcceptThroughHttpAndProtectRecordedPrice() throws Exception {
        var id=request(client,itemA);
        mvc.perform(post("/admin/reservas/{id}/propor",id).with(user(adminA.getEmail()).roles("ADMIN")).with(csrf()).param("version","1")
                .param("dailyRate","40.00").param("terms","<script>texto</script>").param("companyId",companyB.toString()).param("acceptedAt","2020-01-01"))
                .andExpect(redirectedUrl("/admin/reservas/"+id));
        mvc.perform(get("/reservas/{id}",id).with(user(client.getEmail()).roles("CLIENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Aceitar proposta atual"))).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>texto"))));
        mvc.perform(post("/reservas/{id}/aceitar",id).with(user(client.getEmail()).roles("CLIENTE")).with(csrf()).param("version","2").param("dailyRate","1.00"))
                .andExpect(redirectedUrl("/reservas/"+id));
        mvc.perform(post("/admin/reservas/{id}/aprovar",id).with(user(adminA.getEmail()).roles("ADMIN")).with(csrf()).param("version","2").param("amount","0.01"))
                .andExpect(redirectedUrl("/admin/reservas/"+id));
        assertThat(saved(id).getContractAmount()).isEqualByComparingTo("120.00");
        mvc.perform(get("/reservas/{id}",id).with(user(client.getEmail()).roles("CLIENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Acordo aprovado"))).andExpect(content().string(containsString("Estimativa inicial preservada")));
    }
    @Test void shouldPaginateImmutableOfferHistory(){
        var id=request(client,itemA);auth(adminA);
        for(int i=1;i<=12;i++) negotiation.propose(id,proposal(i,"40.00","Condições "+i));
        var details=negotiation.details(id,true,1);assertThat(details.history().getTotalElements()).isEqualTo(13);assertThat(details.history().getContent()).hasSize(3);
        assertThat(details.current().version()).isEqualTo(13);assertThat(details.current().acceptedAt()).isNull();
    }
    @Test void shouldRollbackApprovalIfHistoryCannotBeWritten(){
        var id=request(client,itemA);auth(adminA);jdbc.execute("ALTER TABLE reservation_event ADD CONSTRAINT reject_approval CHECK (new_status<>'RESERVED')");
        try{assertThatThrownBy(()->negotiation.approve(id,version(1))).isInstanceOf(DataIntegrityViolationException.class);}
        finally{jdbc.execute("ALTER TABLE reservation_event DROP CONSTRAINT reject_approval");}
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);assertThat(saved(id).getContractAmount()).isNull();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
    }
    @Test void shouldRollbackOfferVersionIfProposalCannotBeWritten(){
        var id=request(client,itemA);auth(adminA);jdbc.execute("ALTER TABLE reservation_offer ADD CONSTRAINT reject_proposal CHECK (version<2)");
        try{assertThatThrownBy(()->negotiation.propose(id,proposal(1,"40","Novo"))).isInstanceOf(DataIntegrityViolationException.class);}
        finally{jdbc.execute("ALTER TABLE reservation_offer DROP CONSTRAINT reject_proposal");}
        assertThat(saved(id).getCurrentOfferVersion()).isEqualTo(1);assertThat(offers.count()).isEqualTo(1);
    }
    @Test void shouldCreateOnePendingRequestOnConcurrentDuplicateSubmission() throws Exception {
        var result=race(()->{request(client,itemA);return "created";},()->{request(client,itemA);return "created";});
        assertThat(result).containsExactly("created","created");assertThat(reservations.count()).isEqualTo(1);assertThat(offers.count()).isEqualTo(1);assertThat(eventCount()).isEqualTo(1);
    }
    @Test void shouldAllowOnlyOneOfTwoConcurrentOverlappingApprovals() throws Exception {
        var first=request(client,itemA);var second=request(other,itemA);
        var result=race(()->outcome(adminA,()->negotiation.approve(first,version(1))),()->outcome(adminA,()->negotiation.approve(second,version(1))));
        assertThat(result).containsExactlyInAnyOrder("success","invalid");
        assertThat(reservations.findAll()).extracting(Reservation::getStatus).containsExactlyInAnyOrder(ReservationStatus.RESERVED,ReservationStatus.REQUESTED);
        assertThat(eventCount()).isEqualTo(3);
    }
    @Test void shouldNotApproveAnObsoleteProposalDuringConcurrentCounterproposal() throws Exception {
        var id=request(client,itemA);
        var result=race(()->outcome(adminA,()->negotiation.approve(id,version(1))),()->outcome(adminA,()->negotiation.propose(id,proposal(1,"40","Novo"))));
        assertThat(result).containsExactlyInAnyOrder("success","invalid");
        if(saved(id).getStatus()==ReservationStatus.RESERVED){assertThat(saved(id).getContractOfferVersion()).isEqualTo(1);assertThat(offers.count()).isEqualTo(1);}
        else{assertThat(saved(id).getCurrentOfferVersion()).isEqualTo(2);assertThat(saved(id).getContractAmount()).isNull();}
    }
    @Test void shouldNeverCarryOldAcceptanceIntoConcurrentNewProposal() throws Exception {
        var id=request(client,itemA);auth(adminA);negotiation.propose(id,proposal(1,"40","Versão 2"));
        var result=race(()->outcome(client,()->negotiation.accept(id,version(2))),()->outcome(adminA,()->negotiation.propose(id,proposal(2,"35","Versão 3"))));
        assertThat(result.get(1)).isEqualTo("success");assertThat(result.getFirst()).isIn("success","invalid");
        assertThat(saved(id).getCurrentOfferVersion()).isEqualTo(3);assertThat(offers.findByReservationIdAndVersion(id,3).orElseThrow().getAcceptedAt()).isNull();
        auth(adminA);assertThatThrownBy(()->negotiation.approve(id,version(3))).isInstanceOf(ReservationRuleException.class);
    }
    @Test void shouldSerializeApprovalWithCustomerCancellation() throws Exception {
        var id=request(client,itemA);
        var result=race(()->outcome(adminA,()->negotiation.approve(id,version(1))),()->outcome(client,()->management.cancel(id,new CancellationForm(),false)));
        assertThat(result.get(1)).isEqualTo("success");assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(eventCount()).isEqualTo(result.getFirst().equals("success")?3:2);
    }
    @Test void shouldSerializeApprovalWithMaintenance() throws Exception {
        var id=request(client,itemA);var form=new EquipmentConditionForm();form.setCondition(EquipmentCondition.MAINTENANCE);
        var result=race(()->outcome(adminA,()->negotiation.approve(id,version(1))),()->outcome(adminA,()->equipmentService.changeCondition(itemA.getId(),form)));
        assertThat(result.get(1)).isEqualTo("success");assertThat(equipment.findById(itemA.getId()).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.MAINTENANCE);
        assertThat(saved(id).getStatus()).isEqualTo(result.getFirst().equals("success")?ReservationStatus.RESERVED:ReservationStatus.REQUESTED);
    }
    @ParameterizedTest
    @ValueSource(strings={"company","membership","admin","customer"})
    void shouldRecheckDeactivationCommittedWhileApprovalWaits(String target) throws Exception {
        var id=request(client,itemA);
        String sql=switch(target){
            case "company" -> "UPDATE company SET active=false WHERE id=1";
            case "membership" -> "UPDATE company_membership SET active=false WHERE user_id="+adminA.getId();
            case "admin" -> "UPDATE app_user SET active=false WHERE id="+adminA.getId();
            default -> "UPDATE app_user SET active=false WHERE id="+client.getId();
        };
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var deactivate=executor.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.update(sql);locked.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
                catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IllegalStateException(exception);}
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var approval=executor.submit(()->{
                auth(adminA);
                try{negotiation.approve(id,version(1));return "approved";}
                catch(AccessDeniedException exception){return "denied";}
                finally{SecurityContextHolder.clearContext();}
            });
            try{
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline){
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE 'select%')",Boolean.class);
                    if(waiting)break;
                    Thread.sleep(25);
                }
                assertThat(waiting).isTrue();
            }finally{release.countDown();}
            deactivate.get(10,TimeUnit.SECONDS);assertThat(approval.get(10,TimeUnit.SECONDS)).isEqualTo("denied");
        }
        assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);
        assertThat(saved(id).getContractAmount()).isNull();assertThat(eventCount()).isEqualTo(1);
    }
    @Test void shouldSerializeApprovalWithCommercialBlock() throws Exception {
        var id=request(client,itemA);
        var result=race(()->{
            auth(adminA);
            try{negotiation.approve(id,version(1));return "approved";}
            catch(AccessDeniedException exception){return "blocked";}
        },()->{
            auth(adminA);
            try{userService.deactivateCustomer(client.getId());return "blocked";}
            catch(PendingReservationsException exception){return "pending";}
        });
        if(result.getFirst().equals("approved")){
            assertThat(result.get(1)).isEqualTo("pending");
            assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(eventCount()).isEqualTo(2);
        }else{
            assertThat(result).containsExactly("blocked","blocked");
            assertThat(saved(id).getStatus()).isEqualTo(ReservationStatus.REQUESTED);
            assertThat(eventCount()).isEqualTo(1);
        }
    }
    @Test void shouldRejectInvalidOfferReferencesAndPartialAcceptanceInDatabase(){
        var id=request(client,itemA);
        assertThatThrownBy(()->jdbc.update("UPDATE reservation SET current_offer_version=99 WHERE id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE reservation_offer SET accepted_at=NULL WHERE reservation_id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE reservation SET contract_daily_rate=0,contract_amount=0 WHERE id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(saved(id).getCurrentOfferVersion()).isEqualTo(1);
        assertThat(saved(id).getContractAmount()).isNull();
    }
    private long eventCount(){return jdbc.queryForObject("SELECT count(*) FROM reservation_event",Long.class);}
    private ReturnForm newReturn(){var form=new ReturnForm();form.setMaintenance(false);return form;}
    private String outcome(User user,Runnable work){auth(user);try{work.run();return "success";}catch(ReservationRuleException exception){return "invalid";}}
    private List<String> race(Callable<String> a,Callable<String> b) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return a.call();}finally{SecurityContextHolder.clearContext();}});
            var second=executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return b.call();}finally{SecurityContextHolder.clearContext();}});
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
    }
}
