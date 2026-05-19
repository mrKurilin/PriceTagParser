package ru.pricetagparser

const val SERVER_PORT = 8080
const val YANDEX_COMPUTE_API_BASE_URL = "https://compute.api.cloud.yandex.net/compute/v1"
const val BACKEND_STATUS_API_BASE_URL = YANDEX_COMPUTE_API_BASE_URL
const val FILE_PROCESSING_API_BASE_URL = "http://130.193.55.214:8080"
const val BACKEND_INSTANCE_API_PATH = "/instances"
const val BACKEND_START_ACTION = ":start"
const val BACKEND_STOP_ACTION = ":stop"
const val BACKEND_STATUS_API_PATH = "/api/backend/status"
const val BACKEND_START_API_PATH = "/api/backend/start"
const val BACKEND_STOP_API_PATH = "/api/backend/stop"
const val FILES_API_PATH = "/api/files"
const val GET_LOGS_API_PATH = "/api/getLogs"
const val FILE_UPLOAD_API_PATH = "/api/upload"
const val PRICE_FILE_PROCESSOR_LOG_FILE_NAME = "price-file-processor.log"
