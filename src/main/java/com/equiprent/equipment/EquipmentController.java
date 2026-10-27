package com.equiprent.equipment;

import com.equiprent.security.CurrentUser;
import com.equiprent.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/equipamentos")
public class EquipmentController {

    private final EquipmentService equipment;
    private final CurrentUser currentUser;

    public EquipmentController(EquipmentService equipment, CurrentUser currentUser) {
        this.equipment = equipment;
        this.currentUser = currentUser;
    }

    @InitBinder("filter")
    void bindFilter(WebDataBinder binder) {
        binder.setAllowedFields("q", "category", "condition", "page", "size", "sort");
    }

    @GetMapping
    public String list(@Valid @ModelAttribute("filter") EquipmentFilter filter, BindingResult binding, Model model) {
        var user = currentUser.requireActiveUser();
        model.addAttribute("categories", EquipmentCategory.values());
        model.addAttribute("conditions", user.getRole() == UserRole.ADMIN
                ? List.of(EquipmentCondition.values())
                : List.of(EquipmentCondition.OPERATIONAL, EquipmentCondition.MAINTENANCE));
        model.addAttribute("equipmentPage", binding.hasErrors() ? Page.empty() : equipment.search(filter));
        return "equipment/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, @RequestParam(defaultValue = "0") int affectedPage, Model model) {
        var details = equipment.getDetails(id);
        model.addAttribute("equipment", details);
        var conditionForm = new EquipmentConditionForm();
        conditionForm.setCondition(details.condition());
        model.addAttribute("conditionForm", conditionForm);
        model.addAttribute("conditions", EquipmentCondition.values());
        if (currentUser.requireActiveUser().getRole() == UserRole.ADMIN) {
            model.addAttribute("affectedReservations", equipment.getAffectedReservations(id, affectedPage));
        }
        return "equipment/details";
    }
}
