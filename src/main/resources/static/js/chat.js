(() => {
    'use strict';
    const root = document.querySelector('[data-chat]');
    if (!root) return;
    const form = root.querySelector('[data-chat-form]');
    const input = form.querySelector('textarea');
    const sendButton = form.querySelector('button');
    const token = form.querySelector('[name="submissionToken"]');
    const messages = root.querySelector('[data-chat-messages]');
    const older = root.querySelector('[data-chat-older]');
    const status = root.querySelector('[data-chat-status]');
    const error = root.querySelector('[data-chat-error]');
    const refreshButton = root.querySelector('[data-chat-refresh]');
    const seen = new Set([...messages.children].map(item => item.dataset.messageId));
    let last = root.dataset.last;
    let first = root.dataset.first;
    let canSend = !sendButton.disabled;
    let sending = false, reading = false, stopped = false, loadingOlder = false;
    let attemptedText = null;
    let timer;

    function showError(message, source = 'send') { error.textContent = message; error.dataset.source = source; error.hidden = false; }
    function updatePermission(permission) {
        canSend = permission.canSend;
        const notice = root.querySelector('[data-chat-permission]');
        notice.textContent = permission.explanation;
        notice.className = `alert ${canSend ? 'alert-info' : 'alert-secondary'}`;
        sendButton.disabled = sending || !canSend || stopped;
        input.readOnly = sending || !canSend;
    }
    async function request(url, options = {}) {
        let response;
        try {
            response = await fetch(url, {credentials: 'same-origin', cache: 'no-store',
                signal: AbortSignal.timeout(15000), ...options, headers: {Accept: 'application/json', ...options.headers}});
        } catch {
            throw new Error('Falha de conexão. Seu rascunho permanece nesta página. Tente novamente.');
        }
        if (response.redirected || response.status === 401 || response.status === 403 || response.status === 404) {
            stopped = true;
            sendButton.disabled = true;
            root.querySelector('[data-chat-login]').hidden = false;
            throw new Error('A sessão ou a permissão mudou. Entre novamente para conferir o acesso. Seu rascunho permanece nesta página.');
        }
        if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Não foi possível atualizar a conversa. Tente novamente.');
        let data;
        try { data = await response.json(); }
        catch { throw new Error('Não foi possível interpretar a resposta. Seu texto foi preservado; tente novamente.'); }
        if (!response.ok) throw new Error(data.error || 'Não foi possível concluir agora. Tente novamente.');
        return data;
    }
    function render(items, prepend = false) {
        const previousHeight = messages.scrollHeight;
        const previousTop = messages.scrollTop;
        const nearBottom = previousHeight - previousTop - messages.clientHeight < 48;
        const fragment = document.createDocumentFragment();
        let added = 0;
        for (const message of items) {
            if (seen.has(message.id)) continue;
            seen.add(message.id); added++;
            const item = document.createElement('li');
            item.dataset.messageId = message.id;
            item.className = `chat-message p-3 mb-3 rounded border ${message.own ? 'chat-own' : 'bg-light'}`;
            const author = document.createElement('p');
            author.className = 'fw-semibold mb-1';
            author.textContent = `${message.author} · ${message.participant}${message.own ? ' · Você' : ''}`;
            const date = document.createElement('p'); date.className = 'small text-secondary mb-2';
            const time = document.createElement('time'); time.dateTime = message.sentAt; time.textContent = message.sentAtLabel;
            date.append(time);
            const body = document.createElement('p'); body.className = 'chat-body mb-0'; body.textContent = message.body;
            item.append(author, date, body); fragment.append(item);
        }
        if (prepend) messages.prepend(fragment); else messages.append(fragment);
        if (prepend) messages.scrollTop = previousTop + messages.scrollHeight - previousHeight;
        else if (nearBottom) messages.scrollTop = messages.scrollHeight;
        root.querySelector('[data-chat-empty]').hidden = seen.size > 0;
        return added;
    }
    function schedule(delay = 5000) {
        clearTimeout(timer);
        if (!document.hidden && !stopped && root.dataset.historical !== 'true') timer = setTimeout(refresh, delay);
    }
    async function refresh() {
        if (reading || stopped) return;
        reading = true;
        let more = false;
        try {
            const feed = await request(`${root.dataset.endpoint}?after=${encodeURIComponent(last)}`);
            updatePermission(feed.permission);
            if (error.dataset.source === 'read') error.hidden = true;
            const added = render(feed.messages);
            if (feed.messages.length) {
                if (first === '0') first = feed.firstId;
                last = feed.lastId;
            }
            if (added) status.textContent = `${added} nova(s) mensagem(ns) recebida(s).`;
            more = feed.more;
        } catch (problem) { showError(problem.message || 'Falha de conexão. Tente atualizar novamente.', 'read'); }
        finally { reading = false; schedule(more ? 200 : 5000); }
    }
    older.addEventListener('click', async event => {
        event.preventDefault();
        if (loadingOlder || stopped) return;
        loadingOlder = true; older.setAttribute('aria-disabled', 'true');
        try {
            const feed = await request(`${root.dataset.endpoint}?before=${encodeURIComponent(first)}`);
            render(feed.messages, true); updatePermission(feed.permission);
            if (feed.messages.length) first = feed.firstId;
            older.hidden = !feed.more;
            status.textContent = `${feed.messages.length} mensagem(ns) anterior(es) carregada(s).`;
        } catch (problem) { showError(problem.message); }
        finally { loadingOlder = false; older.removeAttribute('aria-disabled'); }
    });
    form.addEventListener('submit', async event => {
        if (root.dataset.historical === 'true') return;
        event.preventDefault();
        if (sending || !canSend || stopped || !form.reportValidity()) return;
        const body = input.value;
        // Mantém a chave ao repetir um envio incerto; texto novo usa outra chave.
        if (attemptedText !== null && attemptedText !== body) token.value = crypto.randomUUID();
        attemptedText = body;
        const payload = new URLSearchParams(new FormData(form));
        sending = true; input.readOnly = true; sendButton.disabled = true; error.hidden = true;
        try {
            await request(form.action, {method: 'POST', body: payload});
            input.value = ''; attemptedText = null; token.value = crypto.randomUUID();
            count(); status.textContent = 'Mensagem enviada.';
            // O cursor só avança na consulta: mensagens concorrentes não são puladas.
            await refresh();
        } catch (problem) { showError(problem.message || 'Não foi possível enviar. Seu texto foi preservado.'); }
        finally { sending = false; input.readOnly = !canSend; sendButton.disabled = !canSend || stopped; }
    });
    function count() { root.querySelector('[data-chat-count]').textContent = `${input.value.length}/2000 caracteres`; }
    input.addEventListener('input', count);
    refreshButton.hidden = false;
    refreshButton.addEventListener('click', refresh);
    document.addEventListener('visibilitychange', () => {
        clearTimeout(timer);
        if (!document.hidden && root.dataset.historical !== 'true') refresh();
    });
    window.addEventListener('pagehide', () => clearTimeout(timer));
    window.addEventListener('pageshow', event => {
        if (event.persisted && !document.hidden && root.dataset.historical !== 'true') refresh();
    });
    if (root.dataset.historical === 'true') {
        status.textContent = 'Você está consultando mensagens anteriores. Volte às recentes para acompanhar a conversa.';
        // Envio por formulário completo retorna aos detalhes e não mistura páginas do histórico.
        refreshButton.hidden = true;
    } else {
        status.textContent = 'Novas mensagens são consultadas a cada cinco segundos enquanto esta página está visível.';
        schedule();
    }
    if (root.dataset.historical !== 'true') messages.scrollTop = messages.scrollHeight;
    count();
})();
