package cymru.vpn.util

/**
 * Utility for resolving country flags from server remarks or IP addresses.
 * Uses Unicode regional indicator symbols to render flag emojis natively.
 */
object CountryUtil {

    // Common country codes mapped to their names/aliases for matching in remarks
    private val countryPatterns = mapOf(
        "US" to listOf("united states", "usa", "us-", "us_", " us ", "america", "new york", "los angeles", "chicago", "dallas", "miami", "seattle", "atlanta", "san jose", "silicon valley", "washington", "virginia", "california", "texas", "florida", "oregon", "new jersey", "phoenix"),
        "GB" to listOf("united kingdom", "uk-", "uk_", " uk ", "england", "london", "manchester", "britain", "british"),
        "DE" to listOf("germany", "de-", "de_", " de ", "frankfurt", "berlin", "munich", "falkenstein", "nuremberg", "dusseldorf"),
        "FR" to listOf("france", "fr-", "fr_", " fr ", "paris", "marseille", "strasbourg", "gravelines"),
        "NL" to listOf("netherlands", "nl-", "nl_", " nl ", "amsterdam", "holland", "dutch", "rotterdam"),
        "JP" to listOf("japan", "jp-", "jp_", " jp ", "tokyo", "osaka"),
        "SG" to listOf("singapore", "sg-", "sg_", " sg "),
        "HK" to listOf("hong kong", "hk-", "hk_", " hk ", "hongkong"),
        "TW" to listOf("taiwan", "tw-", "tw_", " tw ", "taipei"),
        "KR" to listOf("korea", "kr-", "kr_", " kr ", "seoul"),
        "CA" to listOf("canada", "ca-", "ca_", " ca ", "toronto", "montreal", "vancouver"),
        "AU" to listOf("australia", "au-", "au_", " au ", "sydney", "melbourne"),
        "IN" to listOf("india", "in-", "in_", " in ", "mumbai", "bangalore", "chennai"),
        "BR" to listOf("brazil", "br-", "br_", " br ", "sao paulo"),
        "RU" to listOf("russia", "ru-", "ru_", " ru ", "moscow"),
        "TR" to listOf("turkey", "tr-", "tr_", " tr ", "istanbul", "turkiye"),
        "SE" to listOf("sweden", "se-", "se_", " se ", "stockholm"),
        "NO" to listOf("norway", "no-", "no_", " no ", "oslo"),
        "FI" to listOf("finland", "fi-", "fi_", " fi ", "helsinki"),
        "DK" to listOf("denmark", "dk-", "dk_", " dk ", "copenhagen"),
        "CH" to listOf("switzerland", "ch-", "ch_", " ch ", "zurich", "swiss"),
        "AT" to listOf("austria", "at-", "at_", " at ", "vienna"),
        "IT" to listOf("italy", "it-", "it_", " it ", "milan", "rome"),
        "ES" to listOf("spain", "es-", "es_", " es ", "madrid", "barcelona"),
        "PT" to listOf("portugal", "pt-", "pt_", " pt ", "lisbon"),
        "PL" to listOf("poland", "pl-", "pl_", " pl ", "warsaw"),
        "IE" to listOf("ireland", "ie-", "ie_", " ie ", "dublin"),
        "CZ" to listOf("czech", "cz-", "cz_", " cz ", "prague"),
        "RO" to listOf("romania", "ro-", "ro_", " ro ", "bucharest"),
        "BG" to listOf("bulgaria", "bg-", "bg_", " bg ", "sofia"),
        "UA" to listOf("ukraine", "ua-", "ua_", " ua ", "kyiv", "kiev"),
        "IL" to listOf("israel", "il-", "il_", " il ", "tel aviv"),
        "AE" to listOf("uae", "ae-", "ae_", " ae ", "dubai", "emirates"),
        "ZA" to listOf("south africa", "za-", "za_", " za ", "johannesburg", "cape town"),
        "MX" to listOf("mexico", "mx-", "mx_", " mx "),
        "AR" to listOf("argentina", "ar-", "ar_", " ar ", "buenos aires"),
        "CL" to listOf("chile", "cl-", "cl_", " cl ", "santiago"),
        "CO" to listOf("colombia", "co-", "co_", " co ", "bogota"),
        "PH" to listOf("philippines", "ph-", "ph_", " ph ", "manila"),
        "TH" to listOf("thailand", "th-", "th_", " th ", "bangkok"),
        "VN" to listOf("vietnam", "vn-", "vn_", " vn ", "hanoi", "ho chi minh"),
        "MY" to listOf("malaysia", "my-", "my_", " my ", "kuala lumpur"),
        "ID" to listOf("indonesia", "id-", "id_", " id ", "jakarta"),
        "PK" to listOf("pakistan", "pk-", "pk_", " pk ", "karachi", "lahore"),
        "BD" to listOf("bangladesh", "bd-", "bd_", " bd ", "dhaka"),
        "KZ" to listOf("kazakhstan", "kz-", "kz_", " kz "),
        "LV" to listOf("latvia", "lv-", "lv_", " lv ", "riga"),
        "LT" to listOf("lithuania", "lt-", "lt_", " lt ", "vilnius"),
        "EE" to listOf("estonia", "ee-", "ee_", " ee ", "tallinn"),
        "IS" to listOf("iceland", "is-", "is_", " is ", "reykjavik"),
        "LU" to listOf("luxembourg", "lu-", "lu_", " lu "),
        "MD" to listOf("moldova", "md-", "md_", " md ", "chisinau"),
        "RS" to listOf("serbia", "rs-", "rs_", " rs ", "belgrade"),
        "HR" to listOf("croatia", "hr-", "hr_", " hr ", "zagreb"),
        "HU" to listOf("hungary", "hu-", "hu_", " hu ", "budapest"),
        "SK" to listOf("slovakia", "sk-", "sk_", " sk ", "bratislava"),
        "GR" to listOf("greece", "gr-", "gr_", " gr ", "athens"),
        "CY" to listOf("cyprus", "cy-", "cy_", " cy ", "nicosia"),
    )

    /**
     * Converts a 2-letter ISO country code to its Unicode flag emoji.
     * Works by converting each letter to its regional indicator symbol.
     */
    fun countryCodeToFlag(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val upper = countryCode.uppercase()
        val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        return String(first) + String(second)
    }

    /**
     * Extracts a country code from server remarks string.
     * Checks for:
     * 1. Existing flag emojis in the remarks
     * 2. Explicit 2-letter country codes at word boundaries
     * 3. Country name/city matches
     */
    fun extractCountryCode(remarks: String): String? {
        if (remarks.isBlank()) return null
        val lower = " ${remarks.lowercase()} "

        // Check for existing flag emoji in remarks (regional indicator pairs)
        val flagRegex = Regex("[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]")
        flagRegex.find(remarks)?.let { match ->
            val flag = match.value
            val codePoints = flag.codePoints().toArray()
            if (codePoints.size == 2) {
                val c1 = (codePoints[0] - 0x1F1E6 + 'A'.code).toChar()
                val c2 = (codePoints[1] - 0x1F1E6 + 'A'.code).toChar()
                return "$c1$c2"
            }
        }

        // Check for 2-letter country code patterns in remarks (e.g., "GB-Server", "US_East")
        val codeRegex = Regex("(?:^|[^A-Za-z])([A-Z]{2})(?:[^A-Za-z]|$)")
        for (match in codeRegex.findAll(remarks.uppercase())) {
            val code = match.groupValues[1]
            if (countryPatterns.containsKey(code)) {
                return code
            }
        }

        // Check for country name / city matches
        for ((code, patterns) in countryPatterns) {
            for (pattern in patterns) {
                if (lower.contains(pattern)) {
                    return code
                }
            }
        }

        return null
    }

    /**
     * Gets the flag emoji for a server profile based on its remarks or address.
     * Returns the flag emoji string, or empty string if country cannot be determined.
     */
    fun getFlagForProfile(remarks: String, serverAddress: String? = null): String {
        val code = extractCountryCode(remarks)
            ?: serverAddress?.let { extractCountryCode(it) }
            ?: return ""
        return countryCodeToFlag(code)
    }

    // Country code to English name mapping
    private val countryNames = mapOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "DE" to "Germany",
        "FR" to "France",
        "NL" to "Netherlands",
        "JP" to "Japan",
        "SG" to "Singapore",
        "HK" to "Hong Kong",
        "TW" to "Taiwan",
        "KR" to "South Korea",
        "CA" to "Canada",
        "AU" to "Australia",
        "IN" to "India",
        "BR" to "Brazil",
        "RU" to "Russia",
        "TR" to "Turkey",
        "SE" to "Sweden",
        "NO" to "Norway",
        "FI" to "Finland",
        "DK" to "Denmark",
        "CH" to "Switzerland",
        "AT" to "Austria",
        "IT" to "Italy",
        "ES" to "Spain",
        "PT" to "Portugal",
        "PL" to "Poland",
        "IE" to "Ireland",
        "CZ" to "Czech Republic",
        "RO" to "Romania",
        "BG" to "Bulgaria",
        "UA" to "Ukraine",
        "IL" to "Israel",
        "AE" to "UAE",
        "ZA" to "South Africa",
        "MX" to "Mexico",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "PH" to "Philippines",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "MY" to "Malaysia",
        "ID" to "Indonesia",
        "PK" to "Pakistan",
        "BD" to "Bangladesh",
        "KZ" to "Kazakhstan",
        "LV" to "Latvia",
        "LT" to "Lithuania",
        "EE" to "Estonia",
        "IS" to "Iceland",
        "LU" to "Luxembourg",
        "MD" to "Moldova",
        "RS" to "Serbia",
        "HR" to "Croatia",
        "HU" to "Hungary",
        "SK" to "Slovakia",
        "GR" to "Greece",
        "CY" to "Cyprus",
    )

    /**
     * Converts a 2-letter ISO country code to its English display name.
     * Returns the code itself if no name mapping exists.
     */
    fun getCountryName(countryCode: String): String {
        return countryNames[countryCode.uppercase()] ?: countryCode.uppercase()
    }
}
