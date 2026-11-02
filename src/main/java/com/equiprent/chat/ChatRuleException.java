package com.equiprent.chat;

public class ChatRuleException extends RuntimeException {
    private final int status;
    public ChatRuleException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
