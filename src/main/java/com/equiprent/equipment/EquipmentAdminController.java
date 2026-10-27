package com.equiprent.equipment;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/equipamentos")
public class EquipmentAdminController {

    private final EquipmentService equipment;

    public EquipmentAdminController(EquipmentService equipment) {
        this.equipment = equipment;
    }

    @InitBinder("equipmentForm")
    void bindEquipment(WebDataBinder binder) {
        binder.setAllowedFields("assetCode", "name", "description", "category", "dailyRate");
    }

    @InitBinder("conditionForm")
    void bindCondition(WebDataBinder binder) {
        binder.setAllowedFields("condition");
    }

    @ModelAttribute("categories")
    EquipmentCategory[] categories() {
        return EquipmentCategory.values();
    }

    @GetMapping("/novo")
    public String newEquipment(Model model) {
        model.addAttribute("equipmentForm", new EquipmentForm());
        return "equipment/form";
    }

    @GetMapping("/{id}/editar")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("equipmentForm", equipment.getEditForm(id));
        model.addAttribute("equipmentId", id);
        return "equipment/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute EquipmentForm equipmentForm, BindingResult binding,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "equipment/form";
        }
        try {
            var id = equipment.create(equipmentForm);
            redirect.addFlashAttribute("success", "Equipamento cadastrado com sucesso.");
            return "redirect:/equipamentos/" + id;
        } catch (DuplicateAssetCodeException exception) {
            binding.rejectValue("assetCode", "duplicate", exception.getMessage());
            return "equipment/form";
        }
    }

    @PostMapping("/{id}/editar")
    public String update(@PathVariable Long id, @Valid @ModelAttribute EquipmentForm equipmentForm,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        model.addAttribute("equipmentId", id);
        if (binding.hasErrors()) {
            return "equipment/form";
        }
        try {
            equipment.update(id, equipmentForm);
        } catch (EquipmentRuleException exception) {
            binding.rejectValue("assetCode", "immutable", exception.getMessage());
            return "equipment/form";
        }
        redirect.addFlashAttribute("success", "Equipamento atualizado com sucesso.");
        return "redirect:/equipamentos/" + id;
    }

    @PostMapping("/{id}/condicao")
    public String changeCondition(@PathVariable Long id, @Valid @ModelAttribute EquipmentConditionForm conditionForm,
                                  BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", "Selecione uma condição válida.");
            return "redirect:/equipamentos/" + id;
        }
        try {
            equipment.changeCondition(id, conditionForm);
            redirect.addFlashAttribute("success", "Condição do equipamento atualizada.");
        } catch (EquipmentRuleException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/equipamentos/" + id;
    }
}
