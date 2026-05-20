# Подготовка новой VM Ubuntu 24.04 для PriceTagParser API

Инструкция рассчитана на сервер обработки с Ubuntu 24.04 LTS / Debian-подобной ОС.

API-контейнер проекта использует NVIDIA GPU и CUDA PyTorch wheels, поэтому на хосте нужны:

- JDK 21;
- Git и Git LFS;
- Docker Engine;
- Docker Compose plugin;
- C/C++ compiler toolchain;
- NVIDIA driver;
- NVIDIA Container Toolkit для Docker;
- рабочий `nvidia-smi` на хосте и внутри Docker-контейнера.

## 1. Обновить систему

```bash
sudo apt update
sudo apt upgrade -y
```

## 2. Установить базовые пакеты, JDK 21, Git и C/C++ toolchain

```bash
sudo apt install -y \
  ca-certificates \
  curl \
  gnupg \
  lsb-release \
  software-properties-common \
  apt-transport-https \
  git \
  git-lfs \
  openjdk-21-jdk \
  build-essential \
  gcc \
  g++ \
  make \
  pkg-config
```

Проверить установку:

```bash
java -version
git --version
git lfs version
gcc --version
g++ --version
make --version
```

## 3. Быстро создать SSH-ключ и вывести его для копирования

Создать SSH-ключ для текущего пользователя. В `-C` можно указать email или понятный комментарий для VM:
Вывести публичный ключ в терминал для копирования в GitHub/GitLab/Bitbucket:

```bash
test -f "$HOME/.ssh/id_ed25518" || \
  ssh-keygen -t ed25519 -C "price-tag-parser-vm" -f "$HOME/.ssh/id_ed25518" -N ""

cat "$HOME/.ssh/id_ed25518.pub"
```

## 4. Установить Docker Engine и Docker Compose plugin

Добавить официальный Docker APT-репозиторий:

```bash
sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

Установить Docker:

```bash
sudo apt update

sudo apt install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin
```

Добавить текущего пользователя в группу `docker`:

```bash
sudo usermod -aG docker "$USER"
```

Применить группу без перелогина:

```bash
newgrp docker
```

Или перелогиниться в SSH-сессию.

Проверить Docker:

```bash
docker --version
docker compose version
docker run --rm hello-world
```

## 4. Установить NVIDIA-драйвер

Посмотреть рекомендуемый драйвер:

```bash
sudo apt install -y ubuntu-drivers-common
sudo ubuntu-drivers devices
```

Установить рекомендованный драйвер автоматически:

```bash
sudo ubuntu-drivers install
sudo reboot
```

После перезагрузки проверить GPU:

```bash
nvidia-smi
```

Для CUDA 12.4 желательно использовать свежий NVIDIA driver, обычно версии `550+`.

### Если `nvidia-smi: command not found`

Сначала проверь, видит ли система NVIDIA GPU:

```bash
lspci | grep -i nvidia
```

Проверь, установлены ли NVIDIA-пакеты:

```bash
dpkg -l | grep -E 'nvidia-driver|nvidia-utils|libnvidia'
```

Если драйвер ещё не установлен, поставь рекомендованный вариант через `ubuntu-drivers`:

```bash
sudo apt update
sudo apt install -y ubuntu-drivers-common
sudo ubuntu-drivers devices
sudo ubuntu-drivers install
sudo reboot
```

Если драйвер установлен, но отсутствует только команда `nvidia-smi`, установи matching `nvidia-utils` той же major-версии, что и драйвер. Для новой VM с CUDA 12.4 обычно можно брать свежую ветку, например `570` или `590` из списка APT:

```bash
sudo apt update
sudo apt install -y nvidia-utils-570
nvidia-smi
```

Альтернатива, если выбран драйвер ветки `590`:

```bash
sudo apt install -y nvidia-utils-590
nvidia-smi
```

Если после установки `nvidia-utils-*` `nvidia-smi` есть, но пишет ошибку связи с драйвером, перезагрузи VM:

```bash
sudo reboot
```

## 5. Установить NVIDIA Container Toolkit

Добавить репозиторий NVIDIA Container Toolkit:

```bash
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | \
  sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg

curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | \
  sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
  sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
```

Установить toolkit:

```bash
sudo apt update
sudo apt install -y nvidia-container-toolkit
```

Настроить NVIDIA runtime для Docker:

```bash
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

Проверить доступ Docker к GPU:

```bash
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

Если команда выводит таблицу `nvidia-smi`, Docker видит GPU.

## 6. Клонировать проект

```bash
git clone git@github.com:mrKurilin/PriceTagParser.git
cd PriceTagParser
```

## 7. Подготовить env для API

Перейти в папку сервера:

```bash
cd server
```

Создать `.env`:

```bash
touch .env
nano .env
```

Минимальный шаблон:

```env
YANDEX_IAM_TOKEN=<iam-token>
YANDEX_FOLDER_ID=<folder-id>
YANDEX_INSTANCE_ID=<instance-id>
API_HOST_PORT=8080
```

Если переменные Yandex Compute не нужны на конкретной VM, оставь только нужные переменные проекта.

## 8. Собрать и запустить API

Запуск с пересборкой в foreground:

```bash
docker compose -f docker-compose.api.yml up --build
```

Запуск с пересборкой в фоне:

```bash
docker compose -f docker-compose.api.yml up --build -d
```

Посмотреть состояние контейнеров:

```bash
docker compose -f docker-compose.api.yml ps
```

Смотреть логи API:

```bash
docker compose -f docker-compose.api.yml logs -f api
```

## 9. Остановить API

```bash
docker compose -f docker-compose.api.yml down
```

Остановить и удалить volume с Hugging Face cache, если нужно полностью очистить кеш моделей:

```bash
docker compose -f docker-compose.api.yml down -v
```

## Быстрая проверка всей установки

```bash
java -version && \
git --version && \
gcc --version && \
docker --version && \
docker compose version && \
nvidia-smi && \
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

## Примечания по текущему compose

`server/docker-compose.api.yml` уже настроен на NVIDIA GPU:

```yaml
deploy:
  resources:
    reservations:
      devices:
        - driver: nvidia
          count: all
          capabilities: [gpu]

environment:
  - NVIDIA_VISIBLE_DEVICES=all
  - NVIDIA_DRIVER_CAPABILITIES=compute,utility
```
