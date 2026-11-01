package com.equiprent.company;

import com.equiprent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/empresa")
public class CompanyLocationController {
    private final CompanyLocationService location;
    private final CurrentUser currentUser;
    public CompanyLocationController(CompanyLocationService location, CurrentUser currentUser) {
        this.location = location;
        this.currentUser = currentUser;
    }
    @InitBinder("locationForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("city", "state"); }
    @ModelAttribute
    void attributes(Model model) {
        model.addAttribute("states", BrazilState.values());
        model.addAttribute("companyName", currentUser.requireCompanyAccess().companyName());
    }
    @GetMapping
    public String form(Model model) {
        model.addAttribute("locationForm", location.getForm());
        return "company/location";
    }
    @PostMapping
    public String update(@Valid @ModelAttribute("locationForm") CompanyLocationForm form,
                         BindingResult binding, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                location.update(form);
                redirect.addFlashAttribute("success", "Localização da empresa atualizada.");
                return "redirect:/admin/empresa";
            } catch (TransientDataAccessException exception) {
                binding.reject("retry", "Não foi possível salvar agora. Tente novamente.");
            }
        }
        return "company/location";
    }
}
