ARG PRICE_TAG_RUNTIME_IMAGE=python:3.12-slim-bookworm

FROM ${PRICE_TAG_RUNTIME_IMAGE}

ARG PYTORCH_INDEX_URL=
ARG PYTORCH_EXPECT_CUDA=false

ENV VIRTUAL_ENV=/opt/price-tag-venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /workspace

COPY . .

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    python-is-python3 \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    git-lfs \
    && rm -rf /var/lib/apt/lists/* \
    && python3 -m venv "$VIRTUAL_ENV" \
    && pip install --no-cache-dir --upgrade pip setuptools wheel

# Keep PyTorch installation configurable across CUDA and CPU-only machines.
# For CUDA builds set PYTORCH_INDEX_URL to a matching wheel index, e.g. https://download.pytorch.org/whl/cu124.
RUN if [ -n "$PYTORCH_INDEX_URL" ]; then \
        pip install --no-cache-dir --upgrade torch torchvision torchaudio --index-url "$PYTORCH_INDEX_URL"; \
    elif python -c "import torch, torchvision, torchaudio" >/dev/null 2>&1; then \
        echo "Using preinstalled PyTorch packages"; \
    else \
        pip install --no-cache-dir torch torchvision torchaudio; \
    fi; \
    PYTORCH_EXPECT_CUDA="$PYTORCH_EXPECT_CUDA" python -c "import os, torch, torchvision, torchaudio; expect_cuda = os.environ.get('PYTORCH_EXPECT_CUDA', 'false').lower() in ('1', 'true', 'yes'); print(f'Installed PyTorch {torch.__version__} with CUDA {torch.version.cuda}; torchvision {torchvision.__version__}; torchaudio {torchaudio.__version__}'); assert not expect_cuda or torch.version.cuda is not None, 'Installed PyTorch is CPU-only while PYTORCH_EXPECT_CUDA=true'"

RUN pip install --no-cache-dir -r /workspace/requirements.txt

RUN chmod +x /workspace/entrypoint.sh

CMD [ "/bin/bash" ]
