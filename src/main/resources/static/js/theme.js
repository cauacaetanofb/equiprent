(() => {
    'use strict';
    const key = 'equiprent.theme';
    const root = document.documentElement;
    const valid = value => value === 'dark' ? 'dark' : 'light';
    let preference = 'light';
    try { preference = valid(localStorage.getItem(key)); } catch { /* Escolha em memória nesta página. */ }

    function apply() {
        root.dataset.theme = preference;
        root.dataset.bsTheme = preference;
        document.querySelectorAll('[data-theme-select]').forEach(select => { select.value = preference; });
    }
    // Aplica a escolha antes dos estilos. O tema só muda por ação do usuário.
    apply();
    window.addEventListener('storage', event => {
        if (event.key === key || event.key === null) { preference = valid(event.newValue); apply(); }
    });
    window.addEventListener('pageshow', event => {
        if (!event.persisted) return;
        try { preference = valid(localStorage.getItem(key)); } catch { /* Mantém a escolha em memória. */ }
        apply();
    });
    document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('[data-theme-control]').forEach(control => { control.hidden = false; });
        document.querySelectorAll('[data-theme-select]').forEach(select => {
            select.value = preference;
            select.addEventListener('change', () => {
                preference = valid(select.value);
                try { localStorage.setItem(key, preference); } catch { /* A troca funciona sem armazenamento. */ }
                apply();
            });
        });
    });
})();
