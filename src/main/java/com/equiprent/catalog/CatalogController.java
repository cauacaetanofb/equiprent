package com.equiprent.catalog;

import com.equiprent.company.BrazilState;
import com.equiprent.equipment.*;
import com.equiprent.reservation.RentalPeriod;
import com.equiprent.reservation.ReservationRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/catalogo")
public class CatalogController {
    private final CatalogService catalog;
    private final RentalPeriod period;
    public CatalogController(CatalogService catalog, RentalPeriod period) { this.catalog = catalog; this.period = period; }
    @InitBinder("filter")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("q", "category", "city", "state", "startDate", "endDate", "page", "size", "sort");
    }
    @ModelAttribute
    void attributes(Model model) {
        model.addAttribute("categories", EquipmentCategory.values());
        model.addAttribute("states", BrazilState.values());
        model.addAttribute("today", period.today());
    }
    @GetMapping
    public String search(@Valid @ModelAttribute("filter") CatalogFilter filter, BindingResult binding,
                         HttpServletRequest request, Model model) {
        model.addAttribute("items", Page.empty());
        if (!binding.hasErrors()) {
            try {
                model.addAttribute("items", catalog.search(filter));
                CatalogJourney.rememberSearch(request, filter);
            } catch (ReservationRuleException exception) { binding.reject("period", exception.getMessage()); }
        }
        return "catalog/list";
    }
    @GetMapping("/{id}")
    public String details(@PathVariable Long id, @Valid @ModelAttribute("filter") CatalogFilter filter,
                          BindingResult binding, HttpServletRequest request, Model model) {
        model.addAttribute("item", catalog.details(id));
        if (!binding.hasErrors()) {
            CatalogJourney.rememberDetails(request, id, filter);
            if (filter.hasPeriod()) {
                try { model.addAttribute("estimate", catalog.estimate(id, filter)); }
                catch (ReservationRuleException exception) { binding.reject("period", exception.getMessage()); }
            }
        }
        return "catalog/details";
    }
    @GetMapping("/{id}/continuar")
    public String next(@PathVariable Long id, @Valid @ModelAttribute("filter") CatalogFilter filter,
                       BindingResult binding, HttpServletRequest request, Model model) {
        model.addAttribute("item", catalog.details(id));
        if (!binding.hasErrors()) {
            try {
                catalog.estimate(id, filter);
                CatalogJourney.continueAfterLogin(request, id, filter);
                if (request.getUserPrincipal() == null) return "redirect:/login";
                if (request.isUserInRole("CLIENTE")) return "redirect:" + CatalogJourney.reservationUrl(id, filter);
                binding.reject("role", "Use uma conta de cliente para fazer uma reserva.");
            } catch (ReservationRuleException exception) { binding.reject("period", exception.getMessage()); }
        }
        return "catalog/details";
    }
    @ExceptionHandler(EquipmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() { return "error/404"; }
}
