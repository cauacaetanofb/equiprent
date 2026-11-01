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
    private final ReservationNegotiationService negotiation;
    public ReservationManagementController(ReservationManagementService reservations, ReservationOperationsService operations,
                                          ReservationNegotiationService negotiation) {
        this.negotiation=negotiation;
        this.reservations = reservations;
        this.operations = operations;
    }

    @InitBinder("reservationFilter")
    void filter(WebDataBinder binder) { binder.setAllowedFields("q", "customer", "status", "fromDate", "toDate", "page", "size", "sort", "overdueOnly"); }
    @InitBinder("cancellationForm")
    void cancellation(WebDataBinder binder) { binder.setAllowedFields("reason"); }
    @InitBinder("returnForm")
    void returnForm(WebDataBinder binder) { binder.setAllowedFields("maintenance"); }
    @InitBinder("offerForm")
    void offer(WebDataBinder binder) { binder.setAllowedFields("version","dailyRate","terms"); }
    @InitBinder("offerVersionForm")
    void offerVersion(WebDataBinder binder) { binder.setAllowedFields("version"); }
    @InitBinder("rejectionForm")
    void rejection(WebDataBinder binder) { binder.setAllowedFields("reason"); }

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
    public String mineDetails(@PathVariable Long id, @RequestParam(defaultValue="0") int offerPage, Model model) { return details(id, model, false, offerPage); }
    @GetMapping("/admin/reservas/{id}")
    public String adminDetails(@PathVariable Long id, @RequestParam(defaultValue="0") int offerPage, Model model) { return details(id, model, true, offerPage); }

    private String details(Long id, Model model, boolean administrative) {
        return details(id,model,administrative,0);
    }
    private String details(Long id, Model model, boolean administrative, int offerPage) {
        model.addAttribute("details", reservations.details(id, administrative));
        var data=negotiation.details(id,administrative,offerPage);
        model.addAttribute("negotiation",data);
        if (!model.containsAttribute("offerForm")) {
            var form=new OfferForm();
            if (data.current()!=null) { form.setVersion(data.current().version()); form.setDailyRate(data.current().dailyRate()); form.setTerms(data.current().terms()); }
            model.addAttribute("offerForm",form);
        }
        if (!model.containsAttribute("offerVersionForm")) model.addAttribute("offerVersionForm",new OfferVersionForm());
        if (!model.containsAttribute("rejectionForm")) model.addAttribute("rejectionForm",new RejectionForm());
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
                redirect.addFlashAttribute("success", "Cancelamento registrado. Nenhum período permanece bloqueado por este registro.");
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

    @PostMapping("/admin/reservas/{id}/propor")
    public String propose(@PathVariable Long id, @Valid @ModelAttribute OfferForm offerForm, BindingResult binding,
                          Model model, RedirectAttributes redirect) {
        reservations.details(id,true);
        if (!binding.hasErrors()) return negotiate(id,true,model,redirect,() -> negotiation.propose(id,offerForm),"Proposta enviada. Aguarde o aceite do cliente.");
        return details(id,model,true);
    }
    @PostMapping("/reservas/{id}/aceitar")
    public String accept(@PathVariable Long id, @Valid @ModelAttribute OfferVersionForm offerVersionForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        reservations.details(id,false);
        if (!binding.hasErrors()) return negotiate(id,false,model,redirect,() -> negotiation.accept(id,offerVersionForm),"Proposta aceita. Aguarde a aprovação da empresa.");
        return details(id,model,false);
    }
    @PostMapping("/admin/reservas/{id}/aprovar")
    public String approve(@PathVariable Long id, @Valid @ModelAttribute OfferVersionForm offerVersionForm, BindingResult binding,
                          Model model, RedirectAttributes redirect) {
        reservations.details(id,true);
        if (!binding.hasErrors()) return negotiate(id,true,model,redirect,() -> negotiation.approve(id,offerVersionForm),"Reserva aprovada e confirmada para o período informado.");
        return details(id,model,true);
    }
    @PostMapping("/admin/reservas/{id}/recusar")
    public String reject(@PathVariable Long id, @Valid @ModelAttribute RejectionForm rejectionForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        reservations.details(id,true);
        if (!binding.hasErrors()) return negotiate(id,true,model,redirect,() -> negotiation.reject(id,rejectionForm),"Solicitação recusada. O motivo ficou registrado no histórico.");
        return details(id,model,true);
    }
    private String negotiate(Long id, boolean admin, Model model, RedirectAttributes redirect, Runnable action, String message) {
        try {
            action.run(); redirect.addFlashAttribute("success",message);
            return "redirect:"+(admin ? "/admin/reservas/" : "/reservas/")+id;
        } catch (ReservationRuleException exception) { model.addAttribute("operationError",exception.getMessage()); }
        catch (TransientDataAccessException exception) { model.addAttribute("operationError","Não foi possível concluir agora. Atualize a página e tente novamente."); }
        return details(id,model,admin);
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
