package com.equiprent.equipment;

import com.equiprent.reservation.ReservationRepository;
import com.equiprent.reservation.ReservationStatus;
import com.equiprent.reservation.RentalPeriod;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Service
@Validated
public class EquipmentService {

    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final RentalPeriod rentalPeriod;

    public EquipmentService(EquipmentRepository equipment, ReservationRepository reservations,
                            CurrentUser currentUser, Clock clock, RentalPeriod rentalPeriod) {
        this.equipment = equipment;
        this.reservations = reservations;
        this.currentUser = currentUser;
        this.clock = clock;
        this.rentalPeriod = rentalPeriod;
    }

    @Transactional(readOnly = true)
    public Page<EquipmentSummary> search(@Valid EquipmentFilter filter) {
        var user = currentUser.requireActiveUser();
        if (filter.getStartDate() != null || filter.getEndDate() != null) {
            rentalPeriod.days(filter.getStartDate(), filter.getEndDate());
        }
        var sort = switch (filter.getSort()) {
            case "priceAsc" -> Sort.by("dailyRate");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "dailyRate");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by("name");
        };
        // Escapa os curingas do LIKE: buscar "%" ou "_" procura o caractere literal.
        var search = "%" + filter.getQ().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return equipment.search(search, filter.getCategory(), filter.getCondition(), user.getRole() == UserRole.ADMIN,
                filter.getStartDate() != null, filter.getStartDate(), filter.getEndDate(), rentalPeriod.today(),
                PageRequest.of(filter.getPage(), filter.getSize(), sort.and(Sort.by("id"))))
                .map(EquipmentSummary::from);
    }

    @Transactional(readOnly = true)
    public EquipmentSummary getDetails(Long id) {
        var user = currentUser.requireActiveUser();
        var item = equipment.findById(id).orElseThrow(EquipmentNotFoundException::new);
        if (item.getCondition() == EquipmentCondition.RETIRED && user.getRole() != UserRole.ADMIN) {
            throw new EquipmentNotFoundException();
        }
        return EquipmentSummary.from(item);
    }

    @Transactional(readOnly = true)
    public EquipmentForm getEditForm(Long id) {
        currentUser.requireAdmin();
        return EquipmentForm.from(EquipmentSummary.from(
                equipment.findById(id).orElseThrow(EquipmentNotFoundException::new)));
    }

    @Transactional(readOnly = true)
    public Page<AffectedReservation> getAffectedReservations(Long id, int page) {
        currentUser.requireAdmin();
        var item = equipment.findById(id).orElseThrow(EquipmentNotFoundException::new);
        if (item.getCondition() != EquipmentCondition.MAINTENANCE && !reservations.existsByEquipmentIdAndStatusAndEndDateBefore(
                id, ReservationStatus.IN_USE, rentalPeriod.today())) {
            return Page.empty();
        }
        return reservations.findAllByEquipmentIdAndStatusAndEndDateGreaterThanEqual(id, ReservationStatus.RESERVED, rentalPeriod.today(),
                        PageRequest.of(Math.max(0, Math.min(Integer.MAX_VALUE / 10, page)), 10, Sort.by("startDate", "id")))
                .map(reservation -> new AffectedReservation(reservation.getId(),
                        reservation.getStartDate(), reservation.getEndDate()));
    }

    @Transactional
    public Long create(@Valid EquipmentForm form) {
        currentUser.requireAdmin();
        if (equipment.existsByAssetCode(form.getAssetCode())) {
            throw new DuplicateAssetCodeException();
        }
        var item = new Equipment(form.getAssetCode(), form.getName(), form.getDescription(),
                form.getCategory(), form.getDailyRate(), clock.instant());
        try {
            return equipment.saveAndFlush(item).getId();
        } catch (DataIntegrityViolationException exception) {
            if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains("uk_equipment_asset_code")) {
                throw new DuplicateAssetCodeException();
            }
            throw exception;
        }
    }

    @Transactional(timeout = 10)
    public void update(Long id, @Valid EquipmentForm form) {
        currentUser.requireAdmin();
        var item = equipment.findByIdForUpdate(id).orElseThrow(EquipmentNotFoundException::new);
        if (!item.getAssetCode().equals(form.getAssetCode())) {
            throw new EquipmentRuleException("O código patrimonial não pode ser alterado após o cadastro.");
        }
        item.updateDetails(form.getName(), form.getDescription(), form.getCategory(), form.getDailyRate(), clock.instant());
    }

    @Transactional(timeout = 10)
    public void changeCondition(Long id, @Valid EquipmentConditionForm form) {
        currentUser.requireAdmin();
        var item = equipment.findByIdForUpdate(id).orElseThrow(EquipmentNotFoundException::new);
        var target = form.getCondition();
        if (item.getCondition() == target) {
            return;
        }
        if (item.getCondition() == EquipmentCondition.RETIRED) {
            throw new EquipmentRuleException("Um equipamento aposentado não pode voltar à operação.");
        }
        if (target == EquipmentCondition.MAINTENANCE && reservations.existsByEquipmentIdAndStatusIn(
                id, List.of(ReservationStatus.IN_USE))) {
            throw new EquipmentRuleException("Registre a devolução antes de colocar o equipamento em manutenção.");
        }
        if (target == EquipmentCondition.RETIRED && reservations.existsByEquipmentIdAndStatusIn(
                id, List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE))) {
            throw new EquipmentRuleException("Resolva as reservas pendentes antes de aposentar o equipamento.");
        }
        item.changeCondition(target, clock.instant());
    }
}
