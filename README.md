## Что это

`PriceTagParser` — решение для обработки видео ценников из задачи Lenta Tech Life Hack.

Загружаешь видео → сервис распознаёт ценники через ML/CV → получаешь CSV с результатом.

### Режимы работы

- **Desktop** — Compose Desktop-приложение, запускается локально, не нужен сервер.
- **REST API** — Ktor-сервер в Docker, принимает видео и отдаёт CSV через HTTP.
- **Web UI** — Compose Web UI, работает поверх REST API в браузере.

---

## Архитектура

- **`composeApp`** — общий Compose UI для web и desktop.
- **`server`** — Ktor-сервер: принимает загрузки, хранит файлы, отдаёт список файлов и CSV.
- **`shared`** — общая JVM-логика обработки файла для server и desktop.
- **`generate_csv.sh`** — оркестратор обработки: запускает `priceTagRecognition/price_tag_recognition/demo_track.py` и создаёт CSV рядом с исходным файлом.
- **`priceTagRecognition`** — Python ML/CV модуль на PyTorch + YOLO.

### Логика обработки

1. Файл сохраняется в папку `files` (для desktop) или `server/files` (для Docker).
2. Запускается `generate_csv.sh <file>`, который вызывает Python-скрипт распознавания.
3. Рядом с исходным файлом появляется CSV с тем же базовым именем:
   - `example.mp4` → `example.csv`
4. Пока CSV не появился — статус **«Обработка»**. Список обновляется каждые 10 секунд.

---

## Быстрый старт: Desktop

Самый простой способ — запустить desktop-приложение локально.

### Требования

- **JDK 21**
- **Python 3** с установленными зависимостями из `priceTagRecognition/requirements.txt`
- **macOS, Windows или Linux**

### Запуск

```shell
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### Как пользоваться

1. Нажми кнопку **`+`**.
2. Выбери видео или фото файл.
3. Файл скопируется в папку `files` в корне проекта.
4. Статус изменится на **«Обработка»** — запустится `generate_csv.sh`.
5. Когда CSV появится, станут активны кнопки:
   - **«Открыть файл»** — открыть сгенерирова��ный CSV.
   - **«Открыть папку»** — открыть папку с CSV.

### Собрать дистрибутив

```shell
./gradlew :composeApp:packageDistributionForCurrentOS
```

Форматы: **macOS** → `dmg`, **Windows** → `msi`, **Linux** → `deb`.

---

## Быстрый старт: REST API в Docker

### Требования

- **Docker** и **Docker Compose**
- Для CPU-сборки (без GPU): достаточно обычного Docker.
- Для NVIDIA GPU: нужен Linux-хост с NVIDIA-драйвером и [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html).

### Шаг 1: клонировать репозиторий

```shell
git clone <repo-url>
cd PriceTagParser
```

### Шаг 2: создать `.env` (опционально, для управления Yandex Compute)

```shell
# server/.env
YANDEX_IAM_TOKEN=<iam-token>
YANDEX_FOLDER_ID=<folder-id>
YANDEX_INSTANCE_ID=<instance-id>
```

Если Yandex Compute не нужен — файл можно не создавать.

### Шаг 3: запустить сервер (всё в одном контейнере)

```shell
cd server

# Запустить REST API + Web UI на порту 8080
docker compose up --build

# Или в фоне
docker compose up --build -d
```

После старта:
- REST API: `http://localhost:8080/api/files`
- Web UI: `http://localhost:8080`

### Шаг 4: загрузить видео

```shell
curl -X POST http://localhost:8080/api/upload \
  -F "file=@/path/to/video.mp4"
```

Ответ:

```json
{"name":"video.mp4"}
```

### Шаг 5: проверить статус обработки

```shell
curl http://localhost:8080/api/files
```

Ответ (пример):

```json
[
  {"name":"video.mp4","hasCsv":false},
  {"name":"done.mp4","hasCsv":true}
]
```

Пока `hasCsv: false` — идёт обработка. Повторяй запрос каждые 10 секунд.

### Шаг 6: скачать CSV

```shell
curl -O http://localhost:8080/api/files/video.mp4/download
```

Файл `video.csv` будет сохра��ён в текущую папку.

---

## REST API: справочник эндпоинтов

| Метод  | Путь                           | Описание                                              |
|--------|--------------------------------|-------------------------------------------------------|
| `POST` | `/api/upload`                  | Загрузить файл (multipart, поле `file`, макс. 500 МБ) |
| `GET`  | `/api/files`                   | Список файлов и их статусов                           |
| `GET`  | `/api/files/{name}/download`   | Скачать CSV для файла `{name}`                        |
| `GET`  | `/api/getLogs`                 | Логи обработки (текстовый вывод `generate_csv.sh`)    |

---

## Пример полного цикла через curl

```shell
HOST=http://localhost:8080

# 1. Загрузить видео
curl -X POST "$HOST/api/upload" -F "file=@video.mp4"

# 2. Ждать появления CSV (повторять до hasCsv: true)
curl "$HOST/api/files"

# 3. Посмотреть логи обработки
curl "$HOST/api/getLogs"

# 4. Скачать CSV (после завершения обработки)
curl -O "$HOST/api/files/video.mp4/download"
```

---

## Docker: два отдельных сервера (production)

В production REST API и Web UI разворачиваются на разных машинах.

### Сервер обработки (REST API, нужен NVIDIA GPU)

```shell
cd server

export YANDEX_IAM_TOKEN='<iam-token>'
export YANDEX_FOLDER_ID='<folder-id>'
export YANDEX_INSTANCE_ID='<instance-id>'

# Собрать и запустить REST API с CUDA 12.4 PyTorch wheels
docker compose -f docker-compose.api.yml up --build

# В фоне
docker compose -f docker-compose.api.yml up --build -d

# Проверить, что переменные попали в контейнер
docker compose -f docker-compose.api.yml exec api printenv | grep -E 'YANDEX|NVIDIA'

# Остановить
docker compose -f docker-compose.api.yml down
```

REST API будет доступен по `http://<api-server-host>:8080/api/files`.

### Сервер сайта (Web UI)

```shell
cd server

docker compose -f docker-compose.site.yml up --build

# В фоне
docker compose -f docker-compose.site.yml up --build -d

# Остановить
docker compose -f docker-compose.site.yml down
```

Web UI будет доступен по `http://<site-server-host>:8081`.

### Локальная проверка двух серверов на одной машине

```shell
# Терминал 1: REST API
cd server && API_HOST_PORT=8080 docker compose -f docker-compose.api.yml up --build

# Терминал 2: Web UI
cd server && SITE_HOST_PORT=8081 docker compose -f docker-compose.site.yml up --build
```

Открыть: `http://localhost:8081`.

---

## Переменные окружения

### `docker-compose.yml` / `docker-compose.api.yml`

| Переменная                 | По умолчанию              | Описание                            |
|----------------------------|---------------------------|-------------------------------------|
| `API_HOST_PORT`            | `8080`                    | Порт REST API на хосте              |
| `SERVER_PORT`              | `8080`                    | Порт Ktor внутри контейнера         |
| `PRICE_TAG_PARSER_SCRIPT`  | `/app/generate_csv.sh`    | Путь к скрипту генерации CSV        |
| `YANDEX_IAM_TOKEN`         | —                         | IAM-токен Yandex Cloud              |
| `YANDEX_FOLDER_ID`         | —                         | Folder ID Yandex Cloud              |
| `YANDEX_INSTANCE_ID`       | —                         | ID инстанса Yandex Compute          |

### `docker-compose.site.yml`

| Переменная       | По умолчанию | Описание              |
|------------------|--------------|-----------------------|
| `SITE_HOST_PORT` | `8081`       | Порт Web UI на хосте  |

---

## Где хранятся файлы

- **Desktop**: папка `files/` в корне проекта.
- **Docker**: папка `server/files/` монтируется в контейнер как `/app/files`.

CSV создаётся рядом с исходным файлом в той же папке.

---

## Сборка без Docker

```shell
# Собрать server fat jar и Compose Web UI
./gradlew :server:buildFatJar :composeApp:composeCompatibilityBrowserDistribution
```
