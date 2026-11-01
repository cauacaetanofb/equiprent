package com.equiprent.catalog;

import com.equiprent.equipment.*;
import com.equiprent.reservation.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.Locale;

@Service
@Validated
public class CatalogService {
    private final EquipmentRepository equipment;
    private final ReservationRepository reservations;
    private final RentalPeriod period;
    public CatalogService(EquipmentRepository equipment, ReservationRepository reservations, RentalPeriod period) {
        this.equipment = equipment;
        this.reservations = reservations;
        this.period = period;
    }
    @Transactional(readOnly = true)
    public Page<CatalogItem> search(@Valid CatalogFilter filter) {
        Long days = filter.hasPeriod() ? period.days(filter.getStartDate(), filter.getEndDate()) : null;
        var sort = switch (filter.getSort()) {
            case "priceAsc" -> Sort.by("dailyRate");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "dailyRate");
            default -> Sort.by("name");
        };
        return equipment.searchPublic(like(filter.getQ()), filter.getCategory(),
                filter.getCity().isBlank() ? "" : like(filter.getCity()),
                filter.getState() == null ? null : filter.getState().name(), days != null,
                filter.getStartDate(), filter.getEndDate(), period.today(),
                PageRequest.of(filter.getPage(), filter.getSize(), sort.and(Sort.by("id"))))
                .map(item -> CatalogItem.from(item, days == null ? null : period.total(item.getDailyRate(), days), days));
    }
    @Transactional(readOnly = true)
    public CatalogItem details(Long id) {
        return CatalogItem.from(publicEquipment(id), null, null);
    }
    @Transactional(readOnly = true)
    public CatalogItem estimate(Long id, @Valid CatalogFilter filter) {
        var item = publicEquipment(id);
        long days = period.days(filter.getStartDate(), filter.getEndDate());
        if (reservations.hasBlockingReservation(id, filter.getStartDate(), filter.getEndDate(), period.today())) {
            throw new ReservationRuleException("Este equipamento não está disponível para o período escolhido. Consulte outras datas ou volte aos resultados.");
        }
        return CatalogItem.from(item, period.total(item.getDailyRate(), days), days);
    }
    private Equipment publicEquipment(Long id) {
        return equipment.findById(id).filter(item -> item.getCompany().isActive()
                && item.getCondition() == EquipmentCondition.OPERATIONAL).orElseThrow(EquipmentNotFoundException::new);
    }
    private String like(String text) {
        return "%" + text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
