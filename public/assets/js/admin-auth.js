(() => {
    const button = document.querySelector('.admin-password-toggle');
    const input = document.querySelector('#admin-password');
    if (!button || !input) return;
    button.hidden = false;
    button.addEventListener('click', () => {
        const visible = input.type === 'password';
        input.type = visible ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(visible));
        button.setAttribute('aria-label', visible ? button.dataset.hideLabel : button.dataset.showLabel);
        button.textContent = visible ? button.dataset.hide : button.dataset.show;
        input.focus({ preventScroll: true });
    });
})();
