package com.equiprent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class ChatMessageForm {
    @NotNull(message = "Atualize a conversa antes de enviar a mensagem.")
    private UUID submissionToken;
    @NotBlank(message = "Escreva uma mensagem.")
    @Size(max = 2000, message = "A mensagem deve ter no máximo 2000 caracteres.")
    @Pattern(regexp = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*", message = "A mensagem contém caracteres de controle inválidos.")
    private String body = "";

    public UUID getSubmissionToken() { return submissionToken; }
    public void setSubmissionToken(UUID submissionToken) { this.submissionToken = submissionToken; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body == null ? "" : body.strip(); }
}
