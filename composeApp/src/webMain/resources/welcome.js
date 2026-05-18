// Управление welcome-экраном и ленивой загрузкой Compose compatibility entrypoint.
// Вынесено в отдельный файл, чтобы соответствовать CSP `script-src 'self'`
// (инлайновые <script> блокируются без `'unsafe-inline'`).
(function () {
    // В composeWebCompatibility файл composeApp.js выбирает Wasm или JS fallback.
    // Реальный mount Compose может занять несколько секунд после "script onload".
    // Поэтому ждём появления <canvas> внутри #composeAppRoot через animation frame
    // и держим дедлайн на случай ошибок инициализации wasm.
    var COMPOSE_SCRIPT_SRC = 'composeApp.js';
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
    var readyFrameId = null;
    var readyDeadlineMs = 0;

    function stopWatching() {
        if (readyFrameId !== null) {
            cancelAnimationFrame(readyFrameId);
            readyFrameId = null;
        }
        readyDeadlineMs = 0;
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
        readyDeadlineMs = performance.now() + READY_TIMEOUT_MS;

        function checkComposeReady(nowMs) {
            if (composeRoot.querySelector('canvas') !== null) {
                onReady();
                return;
            }

            if (nowMs >= readyDeadlineMs) {
                onFailure(START_FAILURE_MESSAGE);
                return;
            }

            readyFrameId = requestAnimationFrame(checkComposeReady);
        }

        readyFrameId = requestAnimationFrame(checkComposeReady);
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
