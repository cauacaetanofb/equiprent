package com.equiprent.chat;

import com.equiprent.reservation.ReservationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat = chat; }

    @InitBinder("chatMessageForm")
    void bind(WebDataBinder binder) { binder.setAllowedFields("body", "submissionToken"); }

    private boolean admin(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/");
    }
    private String base(Long id, boolean admin) { return (admin ? "/admin/reservas/" : "/reservas/") + id; }

    @GetMapping({"/reservas/{id}/mensagens", "/admin/reservas/{id}/mensagens"})
    @ResponseBody
    public ResponseEntity<ChatFeed> messages(@PathVariable Long id, @RequestParam(required = false) Long before,
                                            @RequestParam(required = false) Long after, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.read(id, admin(request), before, after));
    }

    @GetMapping({"/reservas/{id}/conversa", "/admin/reservas/{id}/conversa"})
    public String page(@PathVariable Long id, @RequestParam(required = false) Long before, HttpServletRequest request, Model model) {
        return page(id, admin(request), before, model);
    }

    private String page(Long id, boolean admin, Long before, Model model) {
        model.addAttribute("chatFeed", chat.read(id, admin, before, null));
        model.addAttribute("chatBase", base(id, admin));
        model.addAttribute("chatHistorical", before != null);
        if (!model.containsAttribute("chatMessageForm")) {
            var form = new ChatMessageForm(); form.setSubmissionToken(UUID.randomUUID());
            model.addAttribute("chatMessageForm", form);
        }
        return "chat/page";
    }

    @PostMapping({"/reservas/{id}/mensagens", "/admin/reservas/{id}/mensagens"})
    public Object send(@PathVariable Long id, @Valid @ModelAttribute ChatMessageForm chatMessageForm, BindingResult binding,
                       HttpServletRequest request, Model model, RedirectAttributes redirect) {
        boolean admin = admin(request);
        // Autoriza o registro mesmo quando o formulário é inválido.
        chat.read(id, admin, null, null);
        int status = 400;
        String error;
        if (binding.hasErrors()) {
            error = binding.getAllErrors().stream().map(value -> value.getDefaultMessage()).distinct().collect(Collectors.joining(" "));
        } else {
            try {
                var messageId = chat.send(id, admin, chatMessageForm);
                if (json(request)) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("id", messageId));
                redirect.addFlashAttribute("chatSuccess", "Mensagem enviada.");
                return "redirect:" + base(id, admin) + "#conversa";
            } catch (ChatRuleException exception) {
                status = exception.status(); error = exception.getMessage();
            } catch (TransientDataAccessException exception) {
                status = 503; error = "Não foi possível enviar agora. Seu texto foi preservado; tente novamente.";
            }
        }
        if (json(request)) return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error", error));
        model.addAttribute("chatError", error);
        return page(id, admin, null, model);
    }

    private boolean json(HttpServletRequest request) {
        var accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseBody
    public ResponseEntity<?> missing() {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(Map.of("error", "Conversa não encontrada."));
    }

    @ExceptionHandler(ChatRuleException.class)
    @ResponseBody
    public ResponseEntity<?> invalid(ChatRuleException exception) {
        return ResponseEntity.status(exception.status()).cacheControl(CacheControl.noStore()).body(Map.of("error", exception.getMessage()));
    }
}
