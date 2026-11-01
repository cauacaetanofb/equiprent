package com.equiprent.catalog;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.util.UriComponentsBuilder;

/** Guarda apenas destinos relativos construídos pelo servidor; nunca aceita returnUrl do navegador. */
public final class CatalogJourney {
    private static final String SEARCH = "catalog.search";
    private static final String DETAILS = "catalog.details";
    private static final String LOGIN_TARGET = "catalog.loginTarget";
    private CatalogJourney() { }

    public static void rememberSearch(HttpServletRequest request, CatalogFilter filter) {
        var session = request.getSession();
        var url = filter.searchUrl(filter.getPage());
        session.setAttribute(SEARCH, url);
        session.setAttribute(LOGIN_TARGET, url);
    }
    public static void rememberDetails(HttpServletRequest request, Long id, CatalogFilter filter) {
        rememberSearch(request, filter);
        request.getSession().setAttribute(DETAILS, filter.detailsUrl(id));
        request.getSession().setAttribute(LOGIN_TARGET, filter.detailsUrl(id));
    }
    public static String reservationUrl(Long id, CatalogFilter filter) {
        return UriComponentsBuilder.fromPath("/reservas/nova").queryParam("equipmentId", id)
                .queryParam("startDate", filter.getStartDate()).queryParam("endDate", filter.getEndDate())
                .build().encode().toUriString();
    }
    public static void continueAfterLogin(HttpServletRequest request, Long id, CatalogFilter filter) {
        rememberDetails(request, id, filter);
        request.getSession().setAttribute(LOGIN_TARGET, reservationUrl(id, filter));
    }
    public static String searchUrl(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && session.getAttribute(SEARCH) instanceof String url ? url : "/catalogo";
    }
    public static String consumeLoginTarget(HttpServletRequest request, Authentication auth) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(LOGIN_TARGET) instanceof String target)) return "/conta";
        session.removeAttribute(LOGIN_TARGET);
        boolean customer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        if (target.startsWith("/reservas/nova") && !customer) {
            return session.getAttribute(DETAILS) instanceof String details ? details : "/catalogo";
        }
        return target;
    }
}
