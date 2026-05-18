// Управление welcome-экраном и ленивой загрузкой composeApp.js.
// Вынесено в отдельный файл, чтобы соответствовать CSP `script-src 'self'`
// (инлайновые <script> блокируются без `'unsafe-inline'`).
(function () {
    // composeApp.js в compatibility-режиме — это лишь обёртка-выбиралка,
    // которая сама догружает originWasmComposeApp.js + .wasm. Реальный
    // mount Compose может занять несколько секунд после "script onload".
    // Поэтому ждём появления <canvas> внутри #composeAppRoot poll-ом
    // и держим fallback-таймаут на случай ошибок инициализации wasm.
    var READY_POLL_INTERVAL_MS = 150;
    var READY_TIMEOUT_MS = 60000;

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
        loaderText.textContent = 'Загружаем приложение…';
        alert(message);
    }

    function watchForCompose() {
        readyPollId = setInterval(function () {
            if (composeRoot.querySelector('canvas') !== null) {
                onReady();
            }
        }, READY_POLL_INTERVAL_MS);

        readyTimeoutId = setTimeout(function () {
            onFailure('Не удалось запустить приложение. Проверьте подключение и попробуйте ещё раз.');
        }, READY_TIMEOUT_MS);
    }

    function startApp() {
        if (loaded) return;
        loaded = true;
        welcome.hidden = true;
        loader.hidden = false;
        loaderText.textContent = 'Загружаем приложение…';

        watchForCompose();

        var script = document.createElement('script');
        script.src = 'composeApp.js';
        script.async = true;
        script.onerror = function () {
            onFailure('Не удалось загрузить приложение. Попробуйте ещё раз.');
        };
        document.body.appendChild(script);
    }

    startButton.addEventListener('click', startApp);
})();
