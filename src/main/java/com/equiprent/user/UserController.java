package com.equiprent.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/clientes")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @InitBinder("userForm")
    void bindAllowedFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "email", "password");
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("customers", users.listCustomers(page));
        return "user/list";
    }

    @GetMapping("/novo")
    public String newCustomer(Model model) {
        model.addAttribute("userForm", new UserForm());
        return "user/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute UserForm userForm, BindingResult binding,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "user/form";
        }
        try {
            users.createCustomer(userForm);
        } catch (DuplicateEmailException exception) {
            binding.rejectValue("email", "duplicate", exception.getMessage());
            return "user/form";
        }
        redirect.addFlashAttribute("success", "Cliente cadastrado com sucesso.");
        return "redirect:/admin/clientes";
    }

    @PostMapping("/{id}/desativar")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            users.deactivateCustomer(id);
            redirect.addFlashAttribute("success", "Cliente desativado. O histórico foi preservado.");
        } catch (PendingReservationsException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/clientes";
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String customerNotFound() {
        return "error/404";
    }
}
