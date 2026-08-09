package uz.ex.sip2go.branding

object ServerUrls {
    fun httpBaseFromWs(wsUrl: String): String? {
        val trimmed = wsUrl.trim()
        if (trimmed.isEmpty()) return null
        val withoutPath = trimmed.substringBefore('?').trimEnd('/')
        val base = when {
            withoutPath.endsWith("/ws/device", ignoreCase = true) -> {
                withoutPath.removeSuffix("/ws/device").trimEnd('/')
            }
            else -> withoutPath
        }
        return when {
            base.startsWith("wss://", ignoreCase = true) ->
                "https://" + base.substringAfter("://")
            base.startsWith("ws://", ignoreCase = true) ->
                "http://" + base.substringAfter("://")
            base.startsWith("https://", ignoreCase = true) -> base
            base.startsWith("http://", ignoreCase = true) -> base
            else -> null
        }
    }
}
