package com.equiprent.reservation;

import com.equiprent.equipment.EquipmentNotFoundException;
import com.equiprent.equipment.EquipmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/reservas")
public class ReservationController {
    private final ReservationService reservations;
    private final EquipmentService equipment;
    private final RentalPeriod period;

    public ReservationController(ReservationService reservations, EquipmentService equipment, RentalPeriod period) {
        this.reservations = reservations;
        this.equipment = equipment;
        this.period = period;
    }

    @InitBinder("reservationForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("equipmentId", "startDate", "endDate", "quoteToken"); }

    @ModelAttribute
    void dates(Model model, HttpServletRequest request) {
        model.addAttribute("today", period.today());
        model.addAttribute("catalogBackUrl", com.equiprent.catalog.CatalogJourney.searchUrl(request));
    }

    @GetMapping("/nova")
    public String form(@ModelAttribute ReservationForm reservationForm, BindingResult binding, Model model, HttpServletRequest request) {
        reservations.requireCustomer();
        // A consulta sempre gera uma nova estimativa; tokens recebidos na URL não são reutilizados.
        reservationForm.setQuoteToken(null);
        if (reservationForm.getEquipmentId() == null || reservationForm.getEquipmentId() <= 0) {
            binding.rejectValue("equipmentId", "equipment", "Selecione um equipamento no catálogo.");
        } else {
            model.addAttribute("equipment", equipment.getDetails(reservationForm.getEquipmentId()));
        }
        if (!binding.hasErrors() && (reservationForm.getStartDate() != null || reservationForm.getEndDate() != null)) {
            try {
                period.days(reservationForm.getStartDate(), reservationForm.getEndDate());
                var quote=reservations.quote(reservationForm);
                ReservationQuoteSession.save(request,quote);
                reservationForm.setQuoteToken(quote.token().toString());
                model.addAttribute("estimate", quote.estimate());
            } catch (ReservationRuleException exception) {
                binding.reject("unavailable", exception.getMessage());
            }
        }
        return "reservation/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute ReservationForm reservationForm, BindingResult binding,
                         HttpServletRequest request, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        reservations.requireCustomer();
        if (!binding.hasErrors()) {
            try {
                var id=reservations.create(reservationForm,ReservationQuoteSession.get(request,reservationForm.getQuoteToken()));
                redirect.addFlashAttribute("success","Solicitação registrada. Confira a situação e a proposta abaixo.");
                return "redirect:/reservas/" + id;
            } catch (ReservationRuleException exception) {
                binding.reject("unavailable", exception.getMessage());
            } catch (TransientDataAccessException exception) {
                binding.reject("retry", "Não foi possível solicitar agora. Consulte a estimativa e tente novamente.");
            }
        }
        return "reservation/form";
    }

    @GetMapping("/{id}/confirmacao")
    public String confirmation(@PathVariable Long id, Model model) {
        model.addAttribute("reservation", reservations.confirmation(id));
        return "reservation/confirmation";
    }

    @ExceptionHandler({ReservationNotFoundException.class, EquipmentNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(HttpServletRequest request, Model model) {
        model.addAttribute("signedIn", request.getUserPrincipal() != null);
        model.addAttribute("isAdmin", request.isUserInRole("ADMIN"));
        return "error/404";
    }
}
