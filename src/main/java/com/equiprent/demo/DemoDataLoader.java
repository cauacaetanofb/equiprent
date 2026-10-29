package com.equiprent.demo;

import com.equiprent.equipment.*;
import com.equiprent.reservation.*;
import com.equiprent.user.*;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Carga de fixtures locais. Não faz parte dos services usados nas operações do produto. */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Environment environment;
    private final Clock clock;

    public DemoDataLoader(JdbcTemplate jdbc, UserRepository users, EquipmentRepository equipment,
                          ReservationRepository reservations, ReservationEventRepository events,
                          PasswordEncoder encoder, Validator validator, Environment environment, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.equipment = equipment;
        this.reservations = reservations;
        this.events = events;
        this.encoder = encoder;
        this.validator = validator;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    @Transactional(timeout = 30)
    public void run(ApplicationArguments arguments) {
        if (!"equiprent_demo".equals(jdbc.queryForObject("SELECT current_database()", String.class))) {
            throw new IllegalStateException("A demonstração exige o banco dedicado equiprent_demo.");
        }
        // Duas inicializações simultâneas não podem criar duas cargas.
        jdbc.execute("SELECT pg_advisory_xact_lock(73849201)");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM demo_seed)", Boolean.class))) {
            log.info("Demonstração já preparada. Dados, datas e credenciais existentes foram preservados.");
            return;
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM app_user UNION ALL SELECT 1 FROM equipment
                    UNION ALL SELECT 1 FROM reservation UNION ALL SELECT 1 FROM reservation_event)
                """, Boolean.class))) {
            throw new IllegalStateException("A carga de demonstração exige um banco vazio; nenhum cadastro foi alterado.");
        }
        String password = environment.getProperty("equiprent.demo.password", "");
        var form = new UserForm();
        form.setName("Admin Demo");
        form.setEmail("admin@equiprent.example");
        form.setPassword(password);
        if (!validator.validate(form).isEmpty()) {
            throw new IllegalStateException("Defina DEMO_ACCOUNT_PASSWORD com 12 a 64 caracteres para as contas fictícias.");
        }

        LocalDate today = LocalDate.now(clock);
        Instant created = at(today.minusDays(30), 9);
        User admin = user("Admin Demo", "admin@equiprent.example", UserRole.ADMIN, password, created);
        User marina = user("Marina Demo", "marina@equiprent.example", UserRole.CLIENTE, password, created);
        User rafael = user("Rafael Demo", "rafael@equiprent.example", UserRole.CLIENTE, password, created);
        Equipment notebook = item("NB-001", "Notebook para trabalho", "Notebook de 14 polegadas, 16 GB de memória e SSD. Acompanha carregador e bolsa.", EquipmentCategory.NOTEBOOK, "89.90", created);
        Equipment camera = item("CAM-001", "Câmera para criação de conteúdo", "Câmera com lente 18–55 mm, bateria e cartão de memória. Indicada para fotos e vídeos.", EquipmentCategory.CAMERA, "120.00", created);
        Equipment drill = item("FER-001", "Furadeira de impacto", "Furadeira com maleta e conjunto de brocas. Conferência dos acessórios na retirada e devolução.", EquipmentCategory.TOOL, "35.00", created);
        Equipment inUse = item("NB-002", "Notebook compacto", "Unidade leve para estudos e apresentações, com carregador.", EquipmentCategory.NOTEBOOK, "69.90", created);
        Equipment overdue = item("CAM-002", "Câmera para eventos", "Kit com lente, duas baterias e bolsa. Exemplo de unidade com devolução pendente.", EquipmentCategory.CAMERA, "95.00", created);
        Equipment maintenance = item("FER-002", "Parafusadeira sem fio", "Kit com bateria, carregador e pontas. Unidade em revisão técnica.", EquipmentCategory.TOOL, "45.90", created);
        Equipment retired = item("NB-003", "Notebook de geração anterior", "Unidade aposentada, preservada para consulta administrativa.", EquipmentCategory.NOTEBOOK, "39.90", created);
        maintenance.changeCondition(EquipmentCondition.MAINTENANCE, at(today.minusDays(1), 10));
        retired.changeCondition(EquipmentCondition.RETIRED, at(today.minusDays(5), 10));

        Instant booked = at(today.minusDays(15), 10);
        reserve(marina, drill, today, today.plusDays(2), booked);
        pickUp(reserve(rafael, inUse, today.minusDays(1), today.plusDays(1), booked), admin, at(today.minusDays(1), 10));
        pickUp(reserve(marina, overdue, today.minusDays(4), today.minusDays(1), booked), admin, at(today.minusDays(4), 10));
        reserve(rafael, overdue, today.plusDays(2), today.plusDays(4), booked);
        reserve(marina, maintenance, today.plusDays(3), today.plusDays(4), booked);
        Reservation returned = reserve(marina, camera, today.minusDays(10), today.minusDays(8), booked);
        pickUp(returned, admin, at(today.minusDays(10), 10));
        Instant returnedAt = at(today.minusDays(8), 15);
        returned.returnEquipment(returnedAt);
        events.save(new ReservationEvent(returned, admin, ReservationStatus.IN_USE, ReservationStatus.RETURNED,
                returnedAt, "Devolução conferida. Equipamento operacional."));
        Reservation cancelled = reserve(rafael, notebook, today.plusDays(5), today.plusDays(7), booked);
        Instant cancelledAt = at(today.minusDays(7), 11);
        cancelled.cancel(cancelledAt);
        events.save(new ReservationEvent(cancelled, rafael, ReservationStatus.RESERVED, ReservationStatus.CANCELLED,
                cancelledAt, "Mudança de agenda do cliente fictício."));
        reservations.flush();
        jdbc.update("INSERT INTO demo_seed (id, seeded_at) VALUES (1, ?)", java.sql.Timestamp.from(clock.instant()));
        log.info("Demonstração preparada: três contas fictícias, sete equipamentos e sete reservas com histórico.");
    }

    private User user(String name, String email, UserRole role, String password, Instant created) {
        return users.save(new User(name, email, encoder.encode(password), role, created));
    }

    private Equipment item(String code, String name, String description, EquipmentCategory category,
                           String rate, Instant created) {
        return equipment.save(new Equipment(code, name, description, category, new BigDecimal(rate), created));
    }

    private Reservation reserve(User customer, Equipment item, LocalDate start, LocalDate end, Instant created) {
        BigDecimal total = item.getDailyRate().multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1));
        Reservation reservation = reservations.save(new Reservation(customer, item, start, end, item.getDailyRate(), total, created));
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.RESERVED, created, null));
        return reservation;
    }

    private void pickUp(Reservation reservation, User admin, Instant time) {
        reservation.pickUp(time);
        events.save(new ReservationEvent(reservation, admin, ReservationStatus.RESERVED, ReservationStatus.IN_USE, time, null));
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(clock.getZone()).toInstant();
    }
}
