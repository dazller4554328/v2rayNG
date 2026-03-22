package cymru.vpn.dto

data class IPAPIInfo(
    var ip: String? = null,
    var clientIp: String? = null,
    var ip_addr: String? = null,
    var query: String? = null,
    var country: String? = null,
    var country_name: String? = null,
    var country_code: String? = null,
    var countryCode: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var location: LocationBean? = null
) {
    data class LocationBean(
        var country_code: String? = null
    )

    fun resolveIp(): String? = listOf(ip, clientIp, ip_addr, query).firstOrNull { !it.isNullOrBlank() }

    fun resolveCountryCode(): String? = listOf(
        country_code, countryCode, location?.country_code
    ).firstOrNull { !it.isNullOrBlank() }

    fun resolveCountryName(): String? = listOf(
        country_name, country
    ).firstOrNull { !it.isNullOrBlank() }
}
