package com.equiprent.chat;

import com.equiprent.equipment.*;
import com.equiprent.reservation.*;
import com.equiprent.user.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
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
@Import(ChatIT.TimeConfiguration.class)
class ChatIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("equiprent.bootstrap.enabled", () -> false);
    }
    static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    static class TestClock extends Clock {
        volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary TestClock clock() { return new TestClock(); } }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationService creation;
    @Autowired ReservationNegotiationService negotiation;
    @Autowired ReservationManagementService management;
    @Autowired ReservationOperationsService operations;
    @Autowired UserService userService;
    @Autowired ChatService chat;
    @Autowired TestClock clock;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    User client, other, adminA, colleague, adminB;
    Long requestA, requestB, companyB;

    @BeforeEach void prepare() {
        SecurityContextHolder.clearContext(); clock.now = NOW;
        jdbc.execute("TRUNCATE chat_message,reservation_offer,reservation_event,reservation,equipment,app_user,company_membership,company_customer RESTART IDENTITY");
        jdbc.update("DELETE FROM company WHERE code<>'INITIAL'"); jdbc.update("UPDATE company SET active=true WHERE id=1");
        companyB = jdbc.queryForObject("INSERT INTO company(code,name) VALUES ('B','Empresa B') RETURNING id", Long.class);
        client = account("Cliente", UserRole.CLIENTE); other = account("Outro", UserRole.CLIENTE);
        adminA = account("AdminA", UserRole.ADMIN); colleague = account("ColegaA", UserRole.ADMIN); adminB = account("AdminB", UserRole.ADMIN);
        jdbc.update("INSERT INTO company_membership(user_id,company_id) VALUES (?,1),(?,1),(?,?)", adminA.getId(), colleague.getId(), adminB.getId(), companyB);
        requestA = request(1L); requestB = request(companyB); auth(client);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private User account(String name, UserRole role) { return users.saveAndFlush(new User(name,name+"@example.test","hash-teste",role,NOW)); }
    private void auth(User actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getEmail(),null,List.of(new SimpleGrantedAuthority("ROLE_"+actor.getRole()))));
    }
    private Long request(Long company) {
        var item = equipment.saveAndFlush(new Equipment(company,"NB","Notebook","Descrição",EquipmentCategory.NOTEBOOK,new BigDecimal("50.00"),NOW));
        var form = new ReservationForm(); form.setEquipmentId(item.getId()); form.setStartDate(LocalDate.now(clock)); form.setEndDate(LocalDate.now(clock).plusDays(2));
        auth(client); return creation.create(form,creation.quote(form));
    }
    private ChatMessageForm form(String text) { var form=new ChatMessageForm();form.setBody(text);form.setSubmissionToken(UUID.randomUUID());return form; }
    private long count() { return jdbc.queryForObject("SELECT count(*) FROM chat_message",Long.class); }
    private OfferVersionForm version() { var form=new OfferVersionForm();form.setVersion(1);return form; }
    private void close(String state) {
        auth(adminA);
        switch(state) {
            case "CANCELLED" -> { auth(client);management.cancel(requestA,new CancellationForm(),false); }
            case "REJECTED" -> { var reason=new RejectionForm();reason.setReason("Encerrado.");negotiation.reject(requestA,reason); }
            case "RETURNED" -> { negotiation.approve(requestA,version());operations.pickUp(requestA);operations.returnEquipment(requestA,new ReturnForm()); }
        }
        auth(client);
    }

    @Test void shouldKeepAuthorTimeAndTextWithoutChangingBusinessRecords() {
        var before=jdbc.queryForList("SELECT * FROM reservation ORDER BY id");
        chat.send(requestA,false,form("  Olá!\nPosso retirar às 9h?  "));
        auth(adminA);chat.send(requestA,true,form("Pode, sim."));
        auth(colleague);chat.send(requestA,true,form("Eu farei a entrega."));
        auth(client);var feed=chat.read(requestA,false,null,null);
        assertThat(feed.messages()).extracting(ChatMessageView::body).containsExactly("Olá!\nPosso retirar às 9h?","Pode, sim.","Eu farei a entrega.");
        assertThat(feed.messages()).extracting(ChatMessageView::author).containsExactly("Cliente","AdminA","ColegaA");
        assertThat(feed.messages()).extracting(ChatMessageView::own).containsExactly(true,false,false);
        assertThat(feed.messages().getFirst().sentAtLabel()).isEqualTo("01/11/2026 09:00:00 (São Paulo)");
        assertThat(jdbc.queryForList("SELECT * FROM reservation ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_offer",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event",Long.class)).isEqualTo(2);
    }
    @Test void shouldIsolateCompanyCustomerAndConversationEvenForSharedCustomer() {
        chat.send(requestA,false,form("Somente A"));chat.send(requestB,false,form("Somente B"));
        auth(other);assertThatThrownBy(()->chat.read(requestA,false,null,null)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(()->chat.send(requestA,false,form("Invasão"))).isInstanceOf(ReservationNotFoundException.class);
        auth(adminB);assertThatThrownBy(()->chat.read(requestA,true,null,null)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(()->chat.send(requestA,true,form("Invasão"))).isInstanceOf(ReservationNotFoundException.class);
        assertThat(chat.read(requestB,true,null,null).messages()).extracting(ChatMessageView::body).containsExactly("Somente B");
        auth(adminA);assertThatThrownBy(()->chat.read(requestA,false,null,null)).isInstanceOf(AccessDeniedException.class);
        auth(client);assertThatThrownBy(()->chat.read(requestA,true,null,null)).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings={"company","membership","admin","client","commercial"})
    void shouldRespectDeactivationAndPreserveAccessibleHistory(String target) {
        chat.send(requestA,false,form("Histórico"));
        deactivate(target);auth(adminA);
        if(List.of("company","membership","admin").contains(target)) {
            assertThatThrownBy(()->chat.read(requestA,true,null,null)).isInstanceOf(AccessDeniedException.class);
        } else {
            assertThat(chat.read(requestA,true,null,null).permission().canSend()).isFalse();
        }
        assertThatThrownBy(()->chat.send(requestA,true,form("Não permitido"))).isInstanceOf(AccessDeniedException.class);
        if(!target.equals("client")) {
            auth(client);assertThat(chat.read(requestA,false,null,null).messages()).hasSize(1);
        }
        assertThat(count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"CANCELLED","REJECTED","RETURNED"})
    void shouldAllowSevenDaysAfterActualClosureThenKeepReadOnlyHistory(String state) {
        close(state);
        clock.now=NOW.plus(Duration.ofDays(7)).minusNanos(1);
        chat.send(requestA,false,form("Dúvida final"));
        auth(adminA);assertThat(chat.read(requestA,true,null,null).permission().canSend()).isTrue();
        clock.now=NOW.plus(Duration.ofDays(7));
        assertThat(chat.read(requestA,true,null,null).permission().canSend()).isFalse();
        assertThatThrownBy(()->chat.send(requestA,true,form("Fora do prazo"))).isInstanceOf(ChatRuleException.class).hasMessageContaining("sete dias");
        auth(client);assertThat(chat.read(requestA,false,null,null).messages()).hasSize(1);
    }
    @Test void shouldKeepConversationOpenWhilePendingEvenIfApprovalDatePassed() {
        clock.now=NOW.plus(Duration.ofDays(15));
        assertThat(chat.read(requestA,false,null,null).permission().canSend()).isTrue();
        chat.send(requestA,false,form("Vou cancelar e solicitar outras datas."));
        assertThat(count()).isEqualTo(1);
    }
    @Test void shouldKeepConfirmedAndInUseConversationOpenBeyondScheduledEnd() {
        auth(adminA);negotiation.approve(requestA,version());operations.pickUp(requestA);
        clock.now=NOW.plus(Duration.ofDays(15));
        chat.send(requestA,true,form("Precisamos combinar a devolução."));
        assertThat(chat.read(requestA,true,null,null).permission().canSend()).isTrue();
    }
    @Test void shouldDeduplicateRetryBeforeRateLimitAndAfterConversationCloses() {
        var message=form("Mensagem única");var id=chat.send(requestA,false,message);
        for(int i=0;i<9;i++)chat.send(requestA,false,form("Outro envio "+i));
        assertThat(chat.send(requestA,false,message)).isEqualTo(id);assertThat(count()).isEqualTo(10);
        close("CANCELLED");clock.now=NOW.plus(Duration.ofDays(8));
        assertThat(chat.send(requestA,false,message)).isEqualTo(id);
        message.setBody("Texto trocado");assertThatThrownBy(()->chat.send(requestA,false,message)).isInstanceOf(ChatRuleException.class);
        assertThat(count()).isEqualTo(10);
    }
    @Test void shouldScopeTokenToAuthorAndConversation() {
        var message=form("Mesmo token");chat.send(requestA,false,message);chat.send(requestB,false,message);
        auth(adminA);chat.send(requestA,true,message);assertThat(count()).isEqualTo(3);
    }
    @Test void shouldLimitTenPerRollingMinuteByParticipantAndConversation() {
        for(int i=0;i<10;i++)chat.send(requestA,false,form("Mensagem "+i));
        assertThatThrownBy(()->chat.send(requestA,false,form("Excesso"))).isInstanceOf(ChatRuleException.class).satisfies(e->assertThat(((ChatRuleException)e).status()).isEqualTo(429));
        chat.send(requestB,false,form("Outra conversa"));auth(adminA);chat.send(requestA,true,form("Resposta"));
        auth(client);clock.now=NOW.plusSeconds(59);assertThatThrownBy(()->chat.send(requestA,false,form("Ainda cedo"))).isInstanceOf(ChatRuleException.class);
        clock.now=NOW.plusSeconds(60);chat.send(requestA,false,form("Novo minuto"));assertThat(count()).isEqualTo(13);
    }
    @ParameterizedTest @ValueSource(strings={"", "   ", "\n\t", "\u0000", "\u0001"})
    void shouldRejectEmptyOrControlText(String body) {
        assertThatThrownBy(()->chat.send(requestA,false,form(body))).isInstanceOf(ConstraintViolationException.class);assertThat(count()).isZero();
    }
    @Test void shouldEnforceTextLengthAndSubmissionToken() {
        chat.send(requestA,false,form("x".repeat(2000)));
        assertThatThrownBy(()->chat.send(requestA,false,form("x".repeat(2001)))).isInstanceOf(ConstraintViolationException.class);
        var missing=form("Texto");missing.setSubmissionToken(null);
        assertThatThrownBy(()->chat.send(requestA,false,missing)).isInstanceOf(ConstraintViolationException.class);assertThat(count()).isEqualTo(1);
    }
    @Test void shouldPaginateChronologicallyAndPollWithoutMissingOrRepeatingMessages() {
        for(int i=1;i<=65;i++){clock.now=NOW.plusSeconds(i*7L);chat.send(requestA,false,form("Mensagem "+i));}
        var latest=chat.read(requestA,false,null,null);assertThat(latest.messages()).hasSize(30);assertThat(latest.more()).isTrue();
        assertThat(latest.messages().getFirst().body()).isEqualTo("Mensagem 36");assertThat(latest.messages().getLast().body()).isEqualTo("Mensagem 65");
        var previous=chat.read(requestA,false,Long.valueOf(latest.firstId()),null);assertThat(previous.messages()).hasSize(30);
        var oldest=chat.read(requestA,false,Long.valueOf(previous.firstId()),null);assertThat(oldest.messages()).hasSize(5);assertThat(oldest.more()).isFalse();
        var updates=chat.read(requestA,false,null,0L);assertThat(updates.messages().getFirst().body()).isEqualTo("Mensagem 1");assertThat(updates.more()).isTrue();
        var second=chat.read(requestA,false,null,Long.valueOf(updates.lastId()));assertThat(second.messages().getFirst().body()).isEqualTo("Mensagem 31");
        assertThat(chat.read(requestA,false,null,Long.valueOf(latest.lastId())).messages()).isEmpty();
        assertThatThrownBy(()->chat.read(requestA,false,1L,1L)).isInstanceOf(ChatRuleException.class);
        assertThatThrownBy(()->chat.read(requestA,false,-1L,null)).isInstanceOf(ChatRuleException.class);
    }
    @Test void shouldRenderEscapedMessagesAndSupportPostingWithoutJavaScript() throws Exception {
        var message=form("<script>alert('x')</script> & uma resposta");chat.send(requestA,false,message);
        mvc.perform(get("/reservas/"+requestA).with(user(client.getEmail()).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("/js/chat.js"))).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/admin/reservas/"+requestA+"/mensagens").with(user(adminA.getEmail()).roles("ADMIN")).with(csrf())
                .param("body","Resposta sem JavaScript").param("submissionToken",UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/admin/reservas/"+requestA+"#conversa"));
        mvc.perform(get("/reservas/"+requestA+"/conversa").with(user(client.getEmail()).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Resposta sem JavaScript")));
    }
    @Test void shouldExposeOnlyPublicAuthorFieldsAndIgnoreForgedFields() throws Exception {
        mvc.perform(post("/reservas/"+requestA+"/mensagens").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()).accept("application/json")
                .param("body","Mensagem").param("submissionToken",UUID.randomUUID().toString()).param("authorId",adminB.getId().toString())
                .param("companyId",companyB.toString()).param("reservationId",requestB.toString()).param("sentAt","2000-01-01T00:00:00Z").param("authorRole","ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"));
        mvc.perform(get("/admin/reservas/"+requestA+"/mensagens").with(user(adminA.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store")))
                .andExpect(jsonPath("$.messages[0].author").value("Cliente")).andExpect(jsonPath("$.messages[0].participant").value("Cliente"))
                .andExpect(jsonPath("$.messages[0].sentAt").value(NOW.toString())).andExpect(jsonPath("$.messages[0].email").doesNotExist())
                .andExpect(content().string(not(containsString("hash-teste"))));
        assertThat(jdbc.queryForObject("SELECT author_id FROM chat_message",Long.class)).isEqualTo(client.getId());
    }
    @Test void shouldRequireCsrfAndAuthenticationAndAuthorizeInvalidForms() throws Exception {
        mvc.perform(get("/reservas/"+requestA+"/mensagens").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/js/chat.js").with(anonymous())).andExpect(status().isOk());
        mvc.perform(post("/reservas/"+requestA+"/mensagens").with(user(client.getEmail()).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/reservas/"+requestA+"/mensagens").with(user(other.getEmail()).roles("CLIENTE")).with(csrf()).param("body",""))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/reservas/"+requestA+"/mensagens").with(user(adminB.getEmail()).roles("ADMIN")).with(csrf()).param("body",""))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/reservas/"+requestA+"/mensagens").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isForbidden());assertThat(count()).isZero();
    }
    @Test void shouldReturnUsefulValidationErrorsAndKeepDraftWithoutJavaScript() throws Exception {
        mvc.perform(post("/reservas/"+requestA+"/mensagens").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()).accept("application/json")
                .param("body","").param("submissionToken","invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("Escreva uma mensagem")));
        var draft="Texto preservado";
        mvc.perform(post("/reservas/"+requestA+"/mensagens").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()).param("body",draft))
                .andExpect(status().isOk()).andExpect(content().string(containsString(draft))).andExpect(model().attributeExists("chatError"));
        assertThat(count()).isZero();
    }
    @Test void shouldRejectEditAndDeleteAndEnforceDatabaseIntegrity() throws Exception {
        chat.send(requestA,false,form("Preservada"));
        mvc.perform(delete("/reservas/"+requestA+"/mensagens/1").with(user(client.getEmail()).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThatThrownBy(()->jdbc.update("DELETE FROM reservation WHERE id=?",requestA)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE chat_message SET body='' WHERE id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE chat_message SET author_role='VISITOR' WHERE id=1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
    }
    @Test void shouldDeduplicateConcurrentSubmissions() throws Exception {
        var message=form("Uma vez");var ids=race(()->{auth(client);return chat.send(requestA,false,message);},()->{auth(client);return chat.send(requestA,false,message);});
        assertThat(ids.getFirst()).isEqualTo(ids.getLast());assertThat(count()).isEqualTo(1);
    }
    @Test void shouldEnforceRateLimitDuringConcurrentSends() throws Exception {
        for(int i=0;i<9;i++)chat.send(requestA,false,form("Mensagem "+i));
        Callable<String> send=()->{auth(client);try{chat.send(requestA,false,form("Décima"));return "sent";}catch(ChatRuleException exception){return "limited";}};
        assertThat(race(send,send)).containsExactlyInAnyOrder("sent","limited");assertThat(count()).isEqualTo(10);
    }
    @Test void shouldSerializeSendWithCancellationAndPreserveBoth() throws Exception {
        var result=race(()->{auth(client);chat.send(requestA,false,form("Ainda tenho uma dúvida"));return "sent";},()->{
            auth(client);management.cancel(requestA,new CancellationForm(),false);return "cancelled";
        });
        assertThat(result).containsExactly("sent","cancelled");assertThat(count()).isEqualTo(1);
        assertThat(reservations.findById(requestA).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }
    @Test void shouldSerializeSendWithCommercialBlock() throws Exception {
        var result=race(()->{auth(client);try{chat.send(requestA,false,form("Dúvida"));return "sent";}catch(AccessDeniedException e){return "blocked";}},()->{
            auth(adminA);userService.deactivateCustomer(client.getId());return "blocked";
        });
        assertThat(result.getLast()).isEqualTo("blocked");assertThat(count()).isEqualTo(result.getFirst().equals("sent")?1:0);
        auth(client);assertThat(chat.read(requestA,false,null,null).permission().canSend()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"company","membership","admin","client"})
    void shouldRecheckActivityAfterWaitingForConcurrentDeactivation(String target) throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var deactivate=executor.submit(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
                deactivate(target);locked.countDown();await(release);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var send=executor.submit(()->{
                auth(adminA);try{chat.send(requestA,true,form("Após espera"));return "sent";}
                catch(AccessDeniedException exception){return "denied";}finally{SecurityContextHolder.clearContext();}
            });
            try {
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<until){
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE 'select%')",Boolean.class);
                    if(waiting)break;Thread.sleep(25);
                }
                assertThat(waiting).isTrue();
            }finally{release.countDown();}
            deactivate.get(10,TimeUnit.SECONDS);assertThat(send.get(10,TimeUnit.SECONDS)).isEqualTo("denied");
        }
        assertThat(count()).isZero();
    }
    @Test void shouldRecheckDeadlineAfterWaitingForReservationLock() throws Exception {
        close("CANCELLED");clock.now=NOW.plus(Duration.ofDays(7)).minusSeconds(1);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var block=executor.submit(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.queryForList("SELECT id FROM reservation WHERE id=? FOR UPDATE",requestA);locked.countDown();await(release);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var send=executor.submit(()->{auth(client);try{chat.send(requestA,false,form("Limite"));return "sent";}catch(ChatRuleException e){return "closed";}finally{SecurityContextHolder.clearContext();}});
            try {
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<until){waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock')",Boolean.class);if(waiting)break;Thread.sleep(25);}
                assertThat(waiting).isTrue();clock.now=NOW.plus(Duration.ofDays(7));
            } finally { release.countDown(); }
            block.get(10,TimeUnit.SECONDS);assertThat(send.get(10,TimeUnit.SECONDS)).isEqualTo("closed");
        }
        assertThat(count()).isZero();
    }
    @Test void shouldRollbackFailedInsertAndPermitRetry() {
        var message=form("Tentar novamente");jdbc.execute("ALTER TABLE chat_message ADD CONSTRAINT reject_message CHECK (body<>'Tentar novamente')");
        try{assertThatThrownBy(()->chat.send(requestA,false,message)).isInstanceOf(DataIntegrityViolationException.class);}
        finally{jdbc.execute("ALTER TABLE chat_message DROP CONSTRAINT reject_message");}
        assertThat(count()).isZero();chat.send(requestA,false,message);assertThat(count()).isEqualTo(1);
    }
    private void deactivate(String target) {
        switch(target){
            case "company" -> jdbc.update("UPDATE company SET active=false WHERE id=1");
            case "membership" -> jdbc.update("UPDATE company_membership SET active=false WHERE user_id=?",adminA.getId());
            case "admin" -> jdbc.update("UPDATE app_user SET active=false WHERE id=?",adminA.getId());
            case "client" -> jdbc.update("UPDATE app_user SET active=false WHERE id=?",client.getId());
            default -> jdbc.update("UPDATE company_customer SET active=false WHERE company_id=1 AND user_id=?",client.getId());
        }
    }
    private void await(CountDownLatch latch) {
        try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
        catch(InterruptedException exception){Thread.currentThread().interrupt();throw new IllegalStateException(exception);}
    }
    private List<String> race(Callable<String> a,Callable<String> b) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return a.call();}finally{SecurityContextHolder.clearContext();}});
            var second=executor.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return b.call();}finally{SecurityContextHolder.clearContext();}});
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
    }
}
