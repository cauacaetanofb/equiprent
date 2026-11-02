package com.equiprent.chat;

// IDs são texto no navegador para evitar perda de precisão de bigint em JavaScript.
public record ChatMessageView(String id, String author, String participant, String body,
                              String sentAt, String sentAtLabel, boolean own) { }
