package uz.ex.sip2go.branding

object BrandingHolder {
    @Volatile
    var organizationName: String = uz.ex.sip2go.data.DeviceSettings.DEFAULT_ORGANIZATION_NAME

    fun update(name: String?) {
        organizationName = name?.trim()?.take(16)?.ifBlank {
            uz.ex.sip2go.data.DeviceSettings.DEFAULT_ORGANIZATION_NAME
        } ?: uz.ex.sip2go.data.DeviceSettings.DEFAULT_ORGANIZATION_NAME
    }
}
