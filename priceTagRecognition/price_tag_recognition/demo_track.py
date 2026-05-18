import argparse
import cv2
import torch
import csv
import numpy as np

from loguru import logger
from ultralytics import YOLO
from tqdm import tqdm

import sys
import os
path = os.path.dirname(os.path.dirname(__file__))
logger.info(f"Adding {path} to sys.path")
sys.path.append(path)

from price_tag_recognition.vlm_inference import run_vlm_batch, initialize_vlm
from price_tag_recognition.utils.visualize import plot_tracking
from trackers.ocsort_tracker.ocsort import OCSort
from trackers.tracking_utils.timer import Timer

from price_tag_recognition.undistort import DistortionCorrector, CAM_SETTINGS, CAM_DISTORT_COEFFS
from price_tag_recognition.image_processing import detect_qr_code, extract_qr_code, post_processing

DEFAULT_CSV_PATH = "result.csv"
DEFAULT_MAX_FRAMES = 100
FRAME_LOG_INTERVAL = 20
VLM_BATCH_SIZE = 1
DETECTION_COLUMNS = 5
CSV_HEADER = ["id", "text", "qr_code_data", "qr_error"]


def crop_quality(img):
    if img is None or img.size == 0:
        return 0.0

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # --- Sharpness (blur detection) ---
    lap_var = cv2.Laplacian(gray, cv2.CV_64F).var()
    sharpness = np.tanh(lap_var / 500.0)  # normalize to ~0–1

    # --- Contrast (useful additional signal) ---
    contrast = gray.std() / 64.0  # normalize roughly to 0–1
    contrast = np.clip(contrast, 0, 1)

    # --- Area (normalized, optional) ---
    h, w = gray.shape
    area = (h * w) / (512 * 512)  # relative to reference size
    area = min(area, 1.0)

    # --- Final weighted score ---
    score = 0.6 * sharpness + 0.3 * contrast + 0.1 * area
    return float(score)


class YOLOPredictor:
    def __init__(self, model_path, device="cpu", conf=0.1, rotate=False):
        logger.info(f"Loading YOLO model from {model_path} on device {device}")
        self.model = YOLO(model_path)
        self.device = device
        self.conf = conf
        self.rotate = rotate

    def inference(self, frame, timer):
        height, width = frame.shape[:2]

        timer.tic()

        results = self.model.predict(
            frame,
            conf=self.conf,
            device=self.device,
            verbose=False
        )

        timer.toc()

        dets = []

        if results and results[0].boxes is not None:
            boxes = results[0].boxes

            xyxy = boxes.xyxy.cpu().numpy()
            scores = boxes.conf.cpu().numpy()

            for i in range(len(xyxy)):
                x1, y1, x2, y2 = xyxy[i]
                dets.append([x1, y1, x2, y2, float(scores[i])])

        dets = np.array(dets) if len(dets) > 0 else None

        img_info = {
            "height": height,
            "width": width,
            "raw_img": frame
        }

        return [dets], img_info

def get_hw_after_rotation(video_path):
    cap = cv2.VideoCapture(video_path)
    ret, frame = cap.read()
    cap.release()

    if not ret:
        raise ValueError("Could not read video")

    frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)

    return frame.shape[0], frame.shape[1]

def imageflow_demo(predictor, args):

    cap = cv2.VideoCapture(args.video_path)
    if not cap.isOpened():
        raise ValueError(f"Could not open video: {args.video_path}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    max_frames = min(args.max_frames, frame_count) if frame_count > 0 else args.max_frames
    logger.info(
        f"Video opened: path={args.video_path}, size={width}x{height}, "
        f"fps={fps:.2f}, frames_total={frame_count}, frames_to_process={max_frames}"
    )

    if args.rotate:
        height, width = get_hw_after_rotation(args.video_path)

    if args.out_path:
        writer = cv2.VideoWriter(
            args.out_path,
            cv2.VideoWriter_fourcc(*"mp4v"),
            fps,
            (width, height)
        )
    else:
        writer = None

    tracker = OCSort(
        det_thresh=args.track_thresh,
        iou_threshold=args.iou_thresh,
        use_byte=args.use_byte
    )

    best_crops = {}  # {id: (quality, crop)}

    distCorrector = DistortionCorrector(CAM_SETTINGS, CAM_DISTORT_COEFFS)

    timer = Timer()
    frame_id = 0

    logger.info("Starting CV detection and tracking")

    while True:
        if frame_id >= args.max_frames:
            break
            
        ret, frame = cap.read()
        if not ret:
            break

        # frame = distCorrector.undistort_frame(frame)

        frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        if args.rotate:
            frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)

        outputs, img_info = predictor.inference(frame, timer)

        dets = outputs[0]
        if dets is None or len(dets) == 0:
            dets = np.empty((0, DETECTION_COLUMNS), dtype=np.float32)
        else:
            dets = np.asarray(dets, dtype=np.float32).reshape(-1, DETECTION_COLUMNS)

        online_targets = tracker.update(
            dets,
            [img_info["height"], img_info["width"]],
            [img_info["height"], img_info["width"]]
        )

        online_tlwhs = []
        online_ids = []

        for t in online_targets:
            x1, y1, x2, y2, tid = t[:5]

            tlwh = [x1, y1, x2 - x1, y2 - y1]

            if tlwh[2] * tlwh[3] > args.min_box_area:
                online_tlwhs.append(tlwh)
                online_ids.append(tid)

                x1_, y1_, x2_, y2_ = map(int, [x1, y1, x2, y2])
                crop = frame[y1_:y2_, x1_:x2_]
                q = crop_quality(crop)

                if tid not in best_crops or q > best_crops[tid][0]:
                    best_crops[tid] = (q, crop.copy())

        timer.toc()

        vis_frame = plot_tracking(
            img_info["raw_img"],
            online_tlwhs,
            online_ids,
            frame_id=frame_id + 1,
            fps=1.0 / max(timer.average_time, 1e-6)
        )

        if writer:
            writer.write(vis_frame)

        frame_id += 1

        if frame_id % FRAME_LOG_INTERVAL == 0:
            logger.info(
                f"Processed frames: {frame_id}/{max_frames}, "
                f"tracked_price_tags={len(best_crops)}"
            )

    cap.release()
    if writer:
        writer.release()

    logger.info(f"CV detection and tracking finished: tracked_price_tags={len(best_crops)}")

    ids = []
    images = []
    qr_results = []

    logger.info("Starting QR parsing for best crops")
    for tid, (_, crop) in tqdm(best_crops.items(), desc="Processing crops and qrs"):
        if crop is None:
            continue
        
        if crop.shape[0] == 0 or crop.shape[1] == 0:
            continue

        ids.append(tid)
        images.append(crop)
        qr_results.append(parse_qr_code(crop))

    logger.info(f"QR parsing finished: valid_crops={len(images)}")

    if images:
        logger.info(f"Initializing VLM model on device={args.device}")
        vlm_model, vlm_processor = initialize_vlm(device=args.device)
        logger.info(f"Starting VLM batch inference: crops={len(images)}")
        texts = run_vlm_batch(
            images,
            vlm_model,
            vlm_processor,
            batch_size=VLM_BATCH_SIZE
        )
        logger.info("VLM batch inference finished")
    else:
        logger.warning("No valid crops found. CSV will contain only header")
        texts = []

    output_csv = args.csv_path
    output_dir = os.path.dirname(output_csv)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    logger.info(f"Writing CSV: {output_csv}")
    with open(output_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(CSV_HEADER)

        for tid, text, qr in zip(ids, texts, qr_results):
            writer.writerow([
                tid,
                text,
                qr.get("qr_code_data"),
                qr.get("qr_error")
            ])

    logger.info(f"CSV writing finished: {output_csv}, rows={len(texts)}")


def parse_qr_code(frame):
    try:
        # Detect the QR code in the image
        qr_code_normal = detect_qr_code(frame)

        if qr_code_normal is None:
            raise ValueError("No QR code detected")

        # Extract the QR code
        qr_code = extract_qr_code(qr_code_normal)

        if qr_code is None:
            raise ValueError("QR code extraction failed")

        # Post processing
        output_qr = post_processing(qr_code)

        if output_qr is None:
            raise ValueError("QR code post-processing failed")

        # Decode the QR code using pyzbar
        decoder = cv2.QRCodeDetector()
        data, _, _ = decoder.detectAndDecode(output_qr)

        # Create the header
        result = result = {
            "qr_code_data": data,
            "qr_error": None
        }
    except Exception as e:
        logger.exception("QR code parsing failed")
        result = result = {
            "qr_code_data": None,
            "qr_error": str(e)
        }

    return result


def main(args):
    args.device = "cuda" if torch.cuda.is_available() else "cpu"
    logger.info(
        f"Starting price tag recognition: video_path={args.video_path}, "
        f"csv_path={args.csv_path}, ckpt={args.ckpt}, device={args.device}"
    )

    predictor = YOLOPredictor(
        model_path=args.ckpt,
        device=args.device,
        conf=args.conf,
        rotate=args.rotate
    )

    imageflow_demo(predictor, args)
    logger.info(f"Price tag recognition finished: csv_path={args.csv_path}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--ckpt", type=str, required=True)
    parser.add_argument("--video_path", type=str, required=True)
    parser.add_argument("--csv_path", type=str, default=DEFAULT_CSV_PATH)
    parser.add_argument("--out_path", type=str, default=None)
    parser.add_argument("--conf", type=float, default=0.1)
    parser.add_argument("--track_thresh", type=float, default=0.5)
    parser.add_argument("--iou_thresh", type=float, default=0.3)
    parser.add_argument("--min_box_area", type=float, default=10)
    parser.add_argument("--max_frames", type=int, default=DEFAULT_MAX_FRAMES)
    parser.add_argument("--use_byte", action="store_true")
    parser.add_argument("--rotate", action="store_true")
    args = parser.parse_args()
    main(args)