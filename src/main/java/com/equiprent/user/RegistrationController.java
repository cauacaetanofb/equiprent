package com.equiprent.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cadastro")
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration) { this.registration = registration; }
    @InitBinder("registrationForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("name", "email", "password", "passwordConfirmation"); }
    @GetMapping
    public String form(@ModelAttribute("registrationForm") RegistrationForm form) { return "security/register"; }
    @PostMapping
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult binding, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                registration.register(form);
                redirect.addFlashAttribute("registered", true);
                return "redirect:/login";
            } catch (DuplicateEmailException exception) {
                binding.rejectValue("email", "duplicate", "Não foi possível cadastrar este e-mail. Se você já tem uma conta, entre com suas credenciais.");
            }
        }
        return "security/register";
    }
}
