package com.equiprent.chat;

import com.equiprent.company.CompanyPolicy;
import com.equiprent.reservation.Reservation;
import com.equiprent.reservation.ReservationNotFoundException;
import com.equiprent.reservation.ReservationRepository;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Validated
public class ChatService {
    public static final int PAGE_SIZE = 30;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final CompanyPolicy companies;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ChatService(ReservationRepository reservations, UserRepository users, CurrentUser currentUser,
                       CompanyPolicy companies, EntityManager entityManager, JdbcTemplate jdbc, Clock clock) {
        this.reservations = reservations;
        this.users = users;
        this.currentUser = currentUser;
        this.companies = companies;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private User actor(boolean admin) {
        if (admin) return currentUser.requireCompanyAccess().actor();
        var actor = currentUser.requireActiveUser();
        if (actor.getRole() != UserRole.CLIENTE) throw new AccessDeniedException("Acesso exclusivo de clientes.");
        return actor;
    }

    private Reservation accessible(Long id, User actor, boolean admin) {
        return (admin ? reservations.findByIdAndCompanyId(id, currentUser.requireCompanyAccess().companyId())
                : reservations.findByIdAndCustomerId(id, actor.getId())).orElseThrow(ReservationNotFoundException::new);
    }

    private ChatPermission permission(Reservation reservation) {
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT c.active AND u.active AND u.role='CLIENTE' AND coalesce(cc.active,true)
                FROM company c JOIN app_user u ON u.id=?
                LEFT JOIN company_customer cc ON cc.company_id=c.id AND cc.user_id=u.id
                WHERE c.id=?
                """, Boolean.class, reservation.getCustomer().getId(), reservation.getCompanyId()));
        if (!active) return new ChatPermission(false, "Conversa disponível somente para consulta devido a bloqueio ou desativação. O histórico foi preservado.");
        Instant endedAt = switch (reservation.getStatus()) {
            case CANCELLED -> reservation.getCancelledAt();
            case REJECTED -> reservation.getRejectedAt();
            case RETURNED -> reservation.getReturnedAt();
            default -> null;
        };
        if (endedAt == null) return new ChatPermission(true, "Converse sobre o aluguel. Valores e confirmação devem ser registrados pelas ações de proposta, aceite e aprovação.");
        var deadline = endedAt.plus(Duration.ofDays(7));
        if (!clock.instant().isBefore(deadline)) return new ChatPermission(false, "O prazo de sete dias após o encerramento terminou. Você pode consultar o histórico.");
        return new ChatPermission(true, "Novas mensagens permitidas até " + DISPLAY_TIME.format(deadline.atZone(clock.getZone())) + " (São Paulo), sete dias após o encerramento.");
    }

    @Transactional(readOnly = true)
    public ChatFeed read(Long id, boolean admin, Long before, Long after) {
        var actor = actor(admin);
        var reservation = accessible(id, actor, admin);
        if ((before != null && before <= 0) || (after != null && after < 0) || (before != null && after != null)) {
            throw new ChatRuleException(400, "Página de mensagens inválida.");
        }
        var arguments = new ArrayList<Object>();
        arguments.add(id);
        String cursor = "";
        if (before != null) { cursor = " AND m.id < ?"; arguments.add(before); }
        if (after != null) { cursor = " AND m.id > ?"; arguments.add(after); }
        arguments.add(PAGE_SIZE + 1);
        var messages = jdbc.query("""
                SELECT m.id,m.author_id,u.name,m.author_role,m.body,m.sent_at
                FROM chat_message m JOIN app_user u ON u.id=m.author_id
                WHERE m.reservation_id=?
                """ + cursor + " ORDER BY m.id " + (after == null ? "DESC" : "ASC") + " LIMIT ?", (rs, row) -> {
            var sentAt = rs.getTimestamp("sent_at").toInstant();
            return new ChatMessageView(rs.getString("id"), rs.getString("name"),
                    rs.getString("author_role").equals("ADMIN") ? "Empresa" : "Cliente", rs.getString("body"),
                    sentAt.toString(), DISPLAY_TIME.format(sentAt.atZone(clock.getZone())) + " (São Paulo)",
                    rs.getLong("author_id") == actor.getId());
        }, arguments.toArray());
        boolean more = messages.size() > PAGE_SIZE;
        if (more) messages.removeLast();
        if (after == null) Collections.reverse(messages);
        return new ChatFeed(List.copyOf(messages), more,
                messages.isEmpty() ? "0" : messages.getFirst().id(),
                messages.isEmpty() ? "0" : messages.getLast().id(), permission(reservation));
    }

    @Transactional(timeout = 10)
    public String send(Long id, boolean admin, @Valid ChatMessageForm form) {
        var actor = actor(admin);
        var initial = accessible(id, actor, admin);
        // Mesmo prefixo da negociação: empresa/vínculo/autor -> cliente -> reserva.
        // O chat não altera equipamento; nunca adquire esse bloqueio depois da reserva.
        companies.requireActiveCompany(initial.getCompanyId());
        var customer = users.findByIdForUpdate(initial.getCustomer().getId()).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        if (!customer.isActive() || customer.getRole() != UserRole.CLIENTE) throw new AccessDeniedException("Cliente inativo.");
        companies.checkCustomer(initial.getCompanyId(), customer.getId());
        var reservation = reservations.findByIdForUpdate(id).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        var repeated = jdbc.queryForList("SELECT id,body FROM chat_message WHERE reservation_id=? AND author_id=? AND submission_token=?",
                id, actor.getId(), form.getSubmissionToken());
        if (!repeated.isEmpty()) {
            if (!repeated.getFirst().get("body").equals(form.getBody())) throw new ChatRuleException(409, "Este envio já foi registrado com outro texto. Atualize a conversa antes de enviar uma nova mensagem.");
            return repeated.getFirst().get("id").toString();
        }
        var permission = permission(reservation);
        if (!permission.canSend()) throw new ChatRuleException(409, permission.explanation());
        var now = clock.instant();
        // A reserva serializa os envios: a contagem e a inclusão funcionam entre instâncias.
        var count = jdbc.queryForObject("SELECT count(*) FROM chat_message WHERE reservation_id=? AND author_id=? AND sent_at>?",
                Long.class, id, actor.getId(), Timestamp.from(now.minusSeconds(60)));
        if (count >= 10) throw new ChatRuleException(429, "Limite de dez mensagens por minuto nesta conversa. Aguarde um pouco e tente novamente.");
        return jdbc.queryForObject("""
                INSERT INTO chat_message(reservation_id,author_id,author_role,submission_token,body,sent_at)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, id, actor.getId(), actor.getRole().name(), form.getSubmissionToken(), form.getBody(), Timestamp.from(now)).toString();
    }

}
