package com.equiprent.reservation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReservationQuoteSession {
    private static final String KEY = "reservation.quotes";
    private ReservationQuoteSession() { }
    @SuppressWarnings("unchecked")
    public static void save(HttpServletRequest request, ReservationQuote quote) {
        var session=request.getSession();
        synchronized(session) {
            var previous=(Map<String,ReservationQuote>)session.getAttribute(KEY);
            var quotes=previous == null ? new LinkedHashMap<String,ReservationQuote>() : new LinkedHashMap<>(previous);
            quotes.put(quote.token().toString(),quote);
            while(quotes.size()>10) quotes.remove(quotes.keySet().iterator().next());
            session.setAttribute(KEY,quotes);
        }
    }
    public static ReservationQuote get(HttpServletRequest request, String token) {
        var session=request.getSession(false);
        if(session==null || token==null) return null;
        synchronized(session) {
            return session.getAttribute(KEY) instanceof Map<?,?> quotes && quotes.get(token) instanceof ReservationQuote quote ? quote : null;
        }
    }
}
