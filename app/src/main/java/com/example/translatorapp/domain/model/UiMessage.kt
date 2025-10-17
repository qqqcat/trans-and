package com.example.translatorapp.domain.model

import java.util.UUID

enum class UiAction {
    Retry,
    OpenSettings,
    CheckPermissions,
    Dismiss
}

enum class UiMessageLevel {
    Info,
    Success,
    Warning,
    Error
}

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val message: String,
    val level: UiMessageLevel = UiMessageLevel.Info,
    val actionLabel: String? = null,
    val action: UiAction? = null
)

fun UiAction.defaultLabel(): String = when (this) {
    UiAction.Retry -> "重试"
    UiAction.OpenSettings -> "打开设置"
    UiAction.CheckPermissions -> "检查权限"
    UiAction.Dismiss -> "知道了"
}
