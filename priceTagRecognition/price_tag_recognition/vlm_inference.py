import json
import os
import torch
import requests
from price_tag_recognition.parse_json import extract_json
from transformers import AutoProcessor, AutoModelForImageTextToText
from peft import PeftModel
from PIL import Image
from tqdm import tqdm
import numpy as np

HF_TOKEN_ENV = "HF_TOKEN"
BASE_MODEL_ID = "Qwen/Qwen3-VL-8B-Instruct"
LORA_MODEL_ID = "openfoodfacts/price-tag-extractor"
PROMPT_CONFIG_URL = "https://huggingface.co/datasets/openfoodfacts/price-tag-extraction/resolve/v1.1/config.json"
REQUEST_TIMEOUT_SECONDS = 30


def get_hf_token():
    return os.environ.get(HF_TOKEN_ENV) or None


def initialize_vlm(device="cpu"):
    hf_token = get_hf_token()

    processor = AutoProcessor.from_pretrained(BASE_MODEL_ID, token=hf_token)

    base_model = AutoModelForImageTextToText.from_pretrained(
        BASE_MODEL_ID,
        torch_dtype=torch.float16,
        device_map={"": device},
        token=hf_token,
    )

    model = PeftModel.from_pretrained(
        model=base_model,
        model_id=LORA_MODEL_ID,
        autocast_adapter_dtype=False,
        token=hf_token,
    )
    model.eval()

    return model, processor


def get_prompt():
    headers = {}
    hf_token = get_hf_token()
    if hf_token:
        headers["Authorization"] = f"Bearer {hf_token}"

    config = requests.get(
        PROMPT_CONFIG_URL,
        headers=headers,
        timeout=REQUEST_TIMEOUT_SECONDS,
    ).json()
    json_schema = config["json_schema"]
    instructions = config["instructions"]
    json_schema_str = json.dumps(json_schema)
    full_instructions = f"{instructions}\n\nResponse must be formatted as JSON, and follow this JSON schema:\n{json_schema_str}"
    return full_instructions


def run_vlm_batch(images, model, processor, batch_size=1):
    results = []

    full_instructions = get_prompt()

    for i in tqdm(range(0, len(images), batch_size)):
        batch_imgs = images[i:i+batch_size]

        # convert images to PIL format if they are numpy arrays
        batch_imgs = [
            Image.fromarray(img) 
            if isinstance(img, (np.ndarray, torch.Tensor)) else img 
            for img in batch_imgs]

        # Build messages per image
        messages_batch = []
        for img in batch_imgs:
            messages_batch.append(
                [
                    {
                        "role": "user",
                        "content": [
                            {"type": "image", "image": img},
                            {"type": "text", "text": full_instructions},
                        ],
                    }
                ]
            )

        # Build prompts
        prompts = [
            processor.apply_chat_template(m, add_generation_prompt=True)
            for m in messages_batch
        ]

        inputs = processor(
            text=prompts, images=batch_imgs, return_tensors="pt", padding=True
        )

        inputs = {k: v.to(model.device) for k, v in inputs.items()}

        with torch.no_grad(), torch.amp.autocast(model.device.type):
            outputs = model.generate(
                **inputs, 
                max_new_tokens=512,
                do_sample=False,
                temperature=0.0,
                eos_token_id=processor.tokenizer.eos_token_id,
            )

        decoded = processor.batch_decode(outputs, skip_special_tokens=True)

        parsed_batch = []

        for text in decoded:
            if "assistant" in text:
                text = text.split("assistant")[-1].strip()
            parsed_batch.append(text)

        results.extend(parsed_batch)

    return results
