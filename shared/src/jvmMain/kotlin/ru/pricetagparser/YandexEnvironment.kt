package ru.pricetagparser

private const val YANDEX_IAM_TOKEN_ENV = "YANDEX_IAM_TOKEN"
private const val YANDEX_FOLDER_ID_ENV = "YANDEX_FOLDER_ID"
private const val YANDEX_INSTANCE_ID_ENV = "YANDEX_INSTANCE_ID"

private val yandexEnvironmentNames = listOf(
    YANDEX_IAM_TOKEN_ENV,
    YANDEX_FOLDER_ID_ENV,
    YANDEX_INSTANCE_ID_ENV,
)

data class YandexEnvironment(
    val iamToken: String,
    val folderId: String,
    val instanceId: String,
)

fun loadYandexEnvironment(): YandexEnvironment {
    val values = yandexEnvironmentNames.associateWith { name ->
        System.getenv(name)?.takeIf { it.isNotBlank() }
    }
    val missingNames = values
        .filterValues { value -> value == null }
        .keys

    check(missingNames.isEmpty()) {
        "No access to required Yandex environment variables: ${missingNames.joinToString()}. " +
            "Configure ${yandexEnvironmentNames.joinToString()} before using Yandex Compute API."
    }

    return YandexEnvironment(
        iamToken = values.getValue(YANDEX_IAM_TOKEN_ENV).orEmpty(),
        folderId = values.getValue(YANDEX_FOLDER_ID_ENV).orEmpty(),
        instanceId = values.getValue(YANDEX_INSTANCE_ID_ENV).orEmpty(),
    )
}
