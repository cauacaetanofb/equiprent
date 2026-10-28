package com.equiprent.reservation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReservationManagementController {
    private final ReservationManagementService reservations;
    private final ReservationOperationsService operations;
    public ReservationManagementController(ReservationManagementService reservations, ReservationOperationsService operations) {
        this.reservations = reservations;
        this.operations = operations;
    }

    @InitBinder("reservationFilter")
    void filter(WebDataBinder binder) { binder.setAllowedFields("q", "customer", "status", "fromDate", "toDate", "page", "size", "sort", "overdueOnly"); }
    @InitBinder("cancellationForm")
    void cancellation(WebDataBinder binder) { binder.setAllowedFields("reason"); }
    @InitBinder("returnForm")
    void returnForm(WebDataBinder binder) { binder.setAllowedFields("maintenance"); }

    @GetMapping("/reservas")
    public String mine(@Valid @ModelAttribute ReservationFilter reservationFilter, BindingResult binding, Model model) {
        return list(reservationFilter, binding, model, false);
    }

    @GetMapping("/admin/reservas")
    public String all(@Valid @ModelAttribute ReservationFilter reservationFilter, BindingResult binding, Model model) {
        return list(reservationFilter, binding, model, true);
    }

    private String list(ReservationFilter filter, BindingResult binding, Model model, boolean administrative) {
        reservations.requireAccess(administrative);
        if (!administrative) filter.setCustomer("");
        navigation(model, administrative);
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("reservationPage", Page.empty());
        if (!binding.hasErrors()) {
            try { model.addAttribute("reservationPage", reservations.search(filter, administrative)); }
            catch (ReservationRuleException exception) { binding.reject("period", exception.getMessage()); }
        }
        return "reservation/list";
    }

    @GetMapping("/reservas/{id}")
    public String mineDetails(@PathVariable Long id, Model model) { return details(id, model, false); }
    @GetMapping("/admin/reservas/{id}")
    public String adminDetails(@PathVariable Long id, Model model) { return details(id, model, true); }

    private String details(Long id, Model model, boolean administrative) {
        model.addAttribute("details", reservations.details(id, administrative));
        if (!model.containsAttribute("cancellationForm")) model.addAttribute("cancellationForm", new CancellationForm());
        if (!model.containsAttribute("returnForm")) model.addAttribute("returnForm", new ReturnForm());
        navigation(model, administrative);
        return "reservation/details";
    }

    @PostMapping("/reservas/{id}/cancelar")
    public String cancelMine(@PathVariable Long id, @Valid @ModelAttribute CancellationForm cancellationForm,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        return cancel(id, cancellationForm, binding, model, redirect, false);
    }
    @PostMapping("/admin/reservas/{id}/cancelar")
    public String cancelAsAdmin(@PathVariable Long id, @Valid @ModelAttribute CancellationForm cancellationForm,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        return cancel(id, cancellationForm, binding, model, redirect, true);
    }

    private String cancel(Long id, CancellationForm form, BindingResult binding, Model model,
            RedirectAttributes redirect, boolean administrative) {
        // Mesmo um formulário inválido precisa respeitar a autorização por registro.
        reservations.details(id, administrative);
        if (!binding.hasErrors()) {
            try {
                reservations.cancel(id, form, administrative);
                redirect.addFlashAttribute("success", "Reserva cancelada. O período foi liberado.");
                return "redirect:" + (administrative ? "/admin/reservas/" : "/reservas/") + id;
            } catch (ReservationRuleException exception) {
                binding.reject("state", exception.getMessage());
            } catch (TransientDataAccessException exception) {
                binding.reject("retry", "Não foi possível cancelar agora. Confira a reserva e tente novamente.");
            }
        }
        return details(id, model, administrative);
    }

    private void navigation(Model model, boolean administrative) {
        model.addAttribute("administrative", administrative);
        model.addAttribute("basePath", administrative ? "/admin/reservas" : "/reservas");
    }

    @PostMapping("/admin/reservas/{id}/retirar")
    public String pickUp(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        reservations.requireAccess(true);
        try {
            operations.pickUp(id);
            redirect.addFlashAttribute("success", "Retirada registrada. O equipamento está em uso.");
            return "redirect:/admin/reservas/" + id;
        } catch (ReservationRuleException exception) {
            model.addAttribute("operationError", exception.getMessage());
        } catch (TransientDataAccessException exception) {
            model.addAttribute("operationError", "Não foi possível registrar a retirada agora. Confira a reserva e tente novamente.");
        }
        return details(id, model, true);
    }

    @PostMapping("/admin/reservas/{id}/devolver")
    public String returnEquipment(@PathVariable Long id, @Valid @ModelAttribute ReturnForm returnForm,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        reservations.details(id, true);
        if (!binding.hasErrors()) {
            try {
                operations.returnEquipment(id, returnForm);
                redirect.addFlashAttribute("success", "Devolução registrada. Os valores contratados foram preservados.");
                return "redirect:/admin/reservas/" + id;
            } catch (ReservationRuleException exception) {
                binding.reject("state", exception.getMessage());
            } catch (TransientDataAccessException exception) {
                binding.reject("retry", "Não foi possível registrar a devolução agora. Confira a reserva e tente novamente.");
            }
        }
        return details(id, model, true);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(HttpServletRequest request, Model model) {
        model.addAttribute("signedIn", request.getUserPrincipal() != null);
        model.addAttribute("isAdmin", request.isUserInRole("ADMIN"));
        return "error/404";
    }
}
