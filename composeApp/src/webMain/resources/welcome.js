// Управление welcome-экраном и ленивой загрузкой Kotlin/Wasm entrypoint.
// Вынесено в отдельный файл, чтобы соответствовать CSP `script-src 'self'`
// (инлайновые <script> блокируются без `'unsafe-inline'`).
(function () {
    // В composeWebCompatibility файл composeApp.js — это JS-обёртка,
    // которая выполняет CSP-небезопасную проверку окружения. Поэтому грузим
    // только прямой wasm entrypoint, не откатываясь на compatibility-wrapper.
    // Реальный mount Compose может занять несколько секунд после "script onload".
    // Поэтому ждём появления <canvas> внутри #composeAppRoot poll-ом
    // и держим fallback-таймаут на случай ошибок инициализации wasm.
    var COMPOSE_SCRIPT_SRC = 'originWasmComposeApp.js';
    var READY_POLL_INTERVAL_MS = 150;
    var READY_TIMEOUT_MS = 60000;
    var LOADING_TEXT = 'Загружаем приложение…';
    var LOAD_FAILURE_MESSAGE = 'Не удалось загрузить приложение. Попробуйте ещё раз.';
    var START_FAILURE_MESSAGE = 'Не удалось запустить приложение. Проверьте подключение и попробуйте ещё раз.';

    var startButton = document.getElementById('welcomeStartButton');
    var welcome = document.getElementById('welcome');
    var loader = document.getElementById('composeLoader');
    var loaderText = document.getElementById('composeLoaderText');
    var composeRoot = document.getElementById('composeAppRoot');
    var loaded = false;
    var readyPollId = null;
    var readyTimeoutId = null;

    function stopWatching() {
        if (readyPollId !== null) {
            clearInterval(readyPollId);
            readyPollId = null;
        }
        if (readyTimeoutId !== null) {
            clearTimeout(readyTimeoutId);
            readyTimeoutId = null;
        }
    }

    function onReady() {
        stopWatching();
        loader.hidden = true;
    }

    function onFailure(message) {
        stopWatching();
        loader.hidden = true;
        welcome.hidden = false;
        loaded = false;
        loaderText.textContent = LOADING_TEXT;
        alert(message);
    }

    function watchForCompose() {
        readyPollId = setInterval(function () {
            if (composeRoot.querySelector('canvas') !== null) {
                onReady();
            }
        }, READY_POLL_INTERVAL_MS);

        readyTimeoutId = setTimeout(function () {
            onFailure(START_FAILURE_MESSAGE);
        }, READY_TIMEOUT_MS);
    }

    function loadComposeScript() {
        var script = document.createElement('script');
        script.src = COMPOSE_SCRIPT_SRC;
        script.async = true;
        script.onerror = function () {
            onFailure(LOAD_FAILURE_MESSAGE);
        };
        document.body.appendChild(script);
    }

    function startApp() {
        if (loaded) return;
        loaded = true;
        welcome.hidden = true;
        loader.hidden = false;
        loaderText.textContent = LOADING_TEXT;

        watchForCompose();
        loadComposeScript();
    }

    startButton.addEventListener('click', startApp);
})();
