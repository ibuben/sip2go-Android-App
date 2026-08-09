package uz.ex.sip2go.data

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.find { it.tag == tag } ?: SYSTEM
    }
}
