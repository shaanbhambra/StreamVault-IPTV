package com.streamvault.domain.model

enum class ChannelNumberingMode(val storageValue: String) {
    GROUP("group"),
    PROVIDER("provider");

    companion object {
        fun fromStorage(value: String?): ChannelNumberingMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: GROUP
    }
}
