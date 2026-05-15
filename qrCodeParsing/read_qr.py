import cv2
import re
from pathlib import Path

EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".webp"}

detector = cv2.QRCodeDetector()
current_dir = Path(__file__).parent


def qr_raw_to_query_string(raw_code: str) -> str | None:
    def find_value(*keys: str) -> str | None:
        pattern = rf"(?:^|&)({'|'.join(map(re.escape, keys))})=(.*?)(?=&|$)"
        match = re.search(pattern, raw_code)
        return match.group(2) if match else None

    def to_float_or_zero(value: str | None) -> float:
        try:
            return float(value) if value else 0.0
        except ValueError:
            return 0.0

    def to_int_or_zero(value: str | None) -> int:
        try:
            return int(value) if value else 0
        except ValueError:
            return 0

    barcode = find_value("barcode", "b")
    if not barcode or not (8 <= len(barcode) <= 14):
        return None

    price1_raw = find_value("price1", "p1")
    if price1_raw is None:
        return None

    try:
        price1 = float(price1_raw)
    except ValueError:
        return None

    data = {
        "qr_code_barcode": barcode,
        "price1_qr": price1,
        "price2_qr": to_float_or_zero(find_value("price2", "p2")),
        "price3_qr": to_float_or_zero(find_value("price3", "p3")),
        "price4_qr": to_float_or_zero(find_value("price4", "p4")),
        "wholesale_level_1_count": to_int_or_zero(find_value("wholesaleLevel1Count", "wL1C")),
        "wholesale_level_1_price": to_float_or_zero(find_value("wholesaleLevel1Price", "wL1P")),
        "wholesale_level_2_count": to_int_or_zero(find_value("wholesaleLevel2Count", "wL2C")),
        "wholesale_level_2_price": to_float_or_zero(find_value("wholesaleLevel2Price", "wL2P")),
        "action_price_qr": to_float_or_zero(find_value("actionPrice", "aP")),
        "action_code_qr": find_value("actionCode", "aC") or "",
    }

    return "&".join(f"{key}={value}" for key, value in data.items())


def preprocess_variants(img):
    variants = []

    variants.append(img)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    variants.append(gray)

    scaled = cv2.resize(gray, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    variants.append(scaled)

    equalized = cv2.equalizeHist(scaled)
    variants.append(equalized)

    threshold = cv2.adaptiveThreshold(
        equalized,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        2,
    )
    variants.append(threshold)

    return variants


def decode_qr(img):
    data, _, _ = detector.detectAndDecode(img)

    results = []

    if data:
        results.append(data)

    ok, decoded_info, _, _ = detector.detectAndDecodeMulti(img)

    if ok:
        for value in decoded_info:
            if value and value not in results:
                results.append(value)

    return results


def find_qr_values(img):
    found_values = []

    for variant in preprocess_variants(img):
        values = decode_qr(variant)

        for value in values:
            if value not in found_values:
                found_values.append(value)

    return found_values


for file in current_dir.iterdir():
    if file.suffix.lower() not in EXTENSIONS:
        continue

    img = cv2.imread(str(file))

    if img is None:
        print(f"{file.name}: не удалось прочитать изображение")
        continue

    raw_values = find_qr_values(img)

    if not raw_values:
        print(f"{file.name}: QR не найден")
        del img
        continue

    parsed_any = False

    for raw_value in raw_values:
        query_string = qr_raw_to_query_string(raw_value)

        if query_string:
            print(f"{file.name}: {query_string}")
            parsed_any = True

    if not parsed_any:
        print(f"{file.name}: QR найден, но не подходит под формат")

    del img