## 0. Общее описание

`PriceTagParser` — решение для обработки фото и видео ценников из задачи Lenta Tech Life Hack.

Приложение позволяет:

- **загружать файлы** с фото или видео ценников;
- **отслеживать статус обработки**: загрузка, обработка, готовый CSV;
- **получать CSV-файл** с результатом обработки;
- **работать в двух режимах**:
  - desktop-приложение на Compose Desktop;
  - web-приложение с Ktor-сервером и Compose Multiplatform UI.

### Архитектура проекта

- **`composeApp`** — общий Compose UI для web и desktop.
- **`server`** — Ktor-сервер: принимает загрузки, хранит файлы, отдаёт список файлов и CSV.
- **`shared`** — общая JVM-логика обработки файла для server и desktop.
- **`files` / `server/files`** — папки с исходными файлами и сгенерированными CSV.
- **`generate_csv.sh`** — скрипт обработки: создаёт CSV рядом с выбранным файлом с тем же базовым именем.

### Логика обработки

После загрузки или выбора файла приложение запускает общий обработчик `PriceFileProcessor`.
Он вызывает `generate_csv.sh`, который создаёт файл вида:

- исходный файл: `example.mp4`
- результат: `example.csv`

После выбора файла в web-приложении UI показывает статус **«Загрузка N%»** с прогрессом отправки файла на сервер. Прогресс считается на стороне браузера через `XMLHttpRequest.upload.onprogress` и отображается в общем Compose UI как **«Загрузка 0%» ... «Загрузка 100%»**.

После завершения загрузки запускается обработка файла. Пока CSV не появился, UI показывает статус **«Обработка»**. Список файлов автоматически обновляется каждые **10 секунд**, а верхний progress bar показывает время до следующего обновления.

## 1. Инструкция по запуску локально desktop-версии

### Требования

- **JDK 21**
- **macOS, Windows или Linux**
- доступ к shell-скрипту `generate_csv.sh`

### Команды desktop-версии

Все команды выполняются из корня проекта:

```shell
# Запустить desktop-приложение на macOS/Linux
./gradlew :composeApp:run

# Запустить desktop-приложение на Windows
.\gradlew.bat :composeApp:run

# Собрать desktop-дистрибутив для текущей ОС
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Как пользоваться

1. Запустите desktop-приложение.
2. Нажмите кнопку **`+`**.
3. Выберите фото или видео файл.
4. Файл будет скопирован в папку `files` в корне проекта.
5. После загрузки файл перейдёт в статус **«Обработка»**.
6. После появления CSV станут доступны кнопки:
   - **«Открыть файл»** — открыть сгенерированный CSV;
   - **«Открыть папку»** — открыть папку с CSV.

Поддерживаемые desktop-форматы в проекте:

- **macOS**: `dmg`
- **Windows**: `msi`
- **Linux**: `deb`

## 2. Инструкция по разворачиванию web-приложения и REST API на разных серверах

### Требования

- **Docker**
- **Docker Compose**
- **NVIDIA Container Toolkit** на сервере обработки, если обработка должна использовать GPU

Проект подготовлен для запуска на двух отдельных серверах:

- **сервер сайта** — отдаёт собранный Compose Web UI как статические файлы;
- **сервер обработки** — запускает REST API на Ktor, хранит файлы и выполняет обработку через `generate_csv.sh`.

### Запуск REST API сервера обработки с GPU

Команды выполняются на сервере обработки из папки `server`:

```shell
cd server

# Собрать и запустить REST API
API_HOST_PORT=8080 docker compose -f docker-compose.api.yml up --build

# Или запустить REST API в фоне
API_HOST_PORT=8080 docker compose -f docker-compose.api.yml up --build -d

# Остановить REST API
docker compose -f docker-compose.api.yml down
```

После старта REST API будет доступен по адресу:

```text
http://<api-server-host>:8080/api/files
```

В `docker-compose.api.yml` включён доступ контейнера к GPU через `gpus: all`. На сервере должен быть установлен и настроен NVIDIA Container Toolkit.

### Запуск сервера сайта

Команды выполняются на сервере сайта из папки `server`.

```shell
cd server

# Собрать и запустить сайт
SITE_HOST_PORT=8081 docker compose -f docker-compose.site.yml up --build

# Или запустить сайт в фоне
SITE_HOST_PORT=8081 docker compose -f docker-compose.site.yml up --build -d

# Проверить состояние контейнера и логи
docker compose -f docker-compose.site.yml ps
docker compose -f docker-compose.site.yml logs site

# Остановить сайт
docker compose -f docker-compose.site.yml down
```

После старта сайт будет доступен по адресу:

```text
http://<site-server-host>:8081
```

Контейнер сайта запускает Ktor-сервер на внутреннем порту `8080` и отдаёт собранный Compose Web UI из `/app/web`. Внешний порт задаётся переменной `SITE_HOST_PORT`, по умолчанию используется `8081`.

Web UI обращается к Ktor REST API напрямую через `Ktor Client`; на сервере обработки включён CORS для браузерных запросов.

### Локальная проверка двух серверов на одной машине

В двух разных терминалах из папки `server`:

```shell
# Терминал 1: REST API
API_HOST_PORT=8080 docker compose -f docker-compose.api.yml up --build

# Терминал 2: сайт
SITE_HOST_PORT=8081 docker compose -f docker-compose.site.yml up --build
```

Откройте сайт:

```text
http://localhost:8081
```

### Где хранятся файлы

В `docker-compose.api.yml` папка `server/files` на сервере обработки подключается в контейнер как `/app/files`.

Это значит:

- при выборе файла web UI показывает **«Загрузка N%»** до завершения отправки файла на REST API;
- загруженные через web файлы сохраняются в `server/files` на сервере обработки;
- CSV создаётся рядом с исходным файлом;
- состояние **«Обработка»** определяется отсутствием CSV;
- состояние готовности определяется наличием CSV с тем же базовым именем.

### Полезные переменные окружения

В `server/docker-compose.api.yml` используются:

- **`API_HOST_PORT=8080`** — порт REST API на хосте;
- **`SERVER_PORT=8080`** — порт Ktor REST API внутри контейнера;
- **`PRICE_TAG_PARSER_SCRIPT=/app/generate_csv.sh`** — путь к скрипту генерации CSV внутри контейнера;
- **`NVIDIA_VISIBLE_DEVICES=all`** — доступные GPU;
- **`NVIDIA_DRIVER_CAPABILITIES=compute,utility`** — возможности NVIDIA runtime для обработки.

В `server/docker-compose.site.yml` используется:

- **`SITE_HOST_PORT=8081`** — порт сайта на хосте.

### Проверка production-сборки без Docker

Из корня проекта можно собрать server fat jar и web UI:

```shell
./gradlew :server:buildFatJar :composeApp:composeCompatibilityBrowserDistribution
```
