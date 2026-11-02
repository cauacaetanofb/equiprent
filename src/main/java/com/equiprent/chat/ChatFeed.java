package com.equiprent.chat;

import java.util.List;

public record ChatFeed(List<ChatMessageView> messages, boolean more, String firstId, String lastId,
                       ChatPermission permission) { }
