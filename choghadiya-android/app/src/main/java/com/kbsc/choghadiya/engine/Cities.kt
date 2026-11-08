package com.kbsc.choghadiya.engine

import java.time.ZoneId

/** Pre-loaded places. Coordinates are city-centre values (degrees, N/E positive). */
object Cities {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    val all: List<Location> = listOf(
        // Gujarat
        Location("Ahmedabad (અમદાવાદ)", 23.0225, 72.5714, IST),
        Location("Gandhinagar (ગાંધીનગર)", 23.2156, 72.6369, IST),
        Location("Surat (સુરત)", 21.1702, 72.8311, IST),
        Location("Vadodara (વડોદરા)", 22.3072, 73.1812, IST),
        Location("Rajkot (રાજકોટ)", 22.3039, 70.8022, IST),
        Location("Bhavnagar (ભાવનગર)", 21.7645, 72.1519, IST),
        Location("Jamnagar (જામનગર)", 22.4707, 70.0577, IST),
        Location("Junagadh (જૂનાગઢ)", 21.5222, 70.4579, IST),
        Location("Anand (આણંદ)", 22.5645, 72.9289, IST),
        Location("Nadiad (નડિયાદ)", 22.6916, 72.8634, IST),
        Location("Mehsana (મહેસાણા)", 23.5880, 72.3693, IST),
        Location("Palanpur (પાલનપુર)", 24.1724, 72.4346, IST),
        Location("Bhuj (ભુજ)", 23.2420, 69.6669, IST),
        Location("Gandhidham (ગાંધીધામ)", 23.0753, 70.1337, IST),
        Location("Porbandar (પોરબંદર)", 21.6417, 69.6293, IST),
        Location("Dwarka (દ્વારકા)", 22.2442, 68.9685, IST),
        Location("Somnath (સોમનાથ)", 20.8880, 70.4013, IST),
        Location("Amreli (અમરેલી)", 21.6032, 71.2221, IST),
        Location("Morbi (મોરબી)", 22.8173, 70.8377, IST),
        Location("Surendranagar (સુરેન્દ્રનગર)", 22.7201, 71.6495, IST),
        Location("Bharuch (ભરૂચ)", 21.7051, 72.9959, IST),
        Location("Navsari (નવસારી)", 20.9467, 72.9520, IST),
        Location("Valsad (વલસાડ)", 20.5992, 72.9342, IST),
        Location("Vapi (વાપી)", 20.3893, 72.9106, IST),
        Location("Godhra (ગોધરા)", 22.7788, 73.6143, IST),
        Location("Dahod (દાહોદ)", 22.8379, 74.2531, IST),
        Location("Himmatnagar (હિંમતનગર)", 23.5980, 72.9661, IST),
        Location("Patan (પાટણ)", 23.8493, 72.1266, IST),
        // Rest of India
        Location("Mumbai (મુંબઈ)", 19.0760, 72.8777, IST),
        Location("Pune", 18.5204, 73.8567, IST),
        Location("Delhi", 28.6139, 77.2090, IST),
        Location("Jaipur", 26.9124, 75.7873, IST),
        Location("Udaipur", 24.5854, 73.7125, IST),
        Location("Indore", 22.7196, 75.8577, IST),
        Location("Bengaluru", 12.9716, 77.5946, IST),
        Location("Chennai", 13.0827, 80.2707, IST),
        Location("Hyderabad", 17.3850, 78.4867, IST),
        Location("Kolkata", 22.5726, 88.3639, IST),
        // Overseas (Gujarati diaspora)
        Location("London", 51.5074, -0.1278, ZoneId.of("Europe/London")),
        Location("Leicester", 52.6369, -1.1398, ZoneId.of("Europe/London")),
        Location("New York", 40.7128, -74.0060, ZoneId.of("America/New_York")),
        Location("New Jersey (Edison)", 40.5187, -74.4121, ZoneId.of("America/New_York")),
        Location("Chicago", 41.8781, -87.6298, ZoneId.of("America/Chicago")),
        Location("Houston", 29.7604, -95.3698, ZoneId.of("America/Chicago")),
        Location("Los Angeles", 34.0522, -118.2437, ZoneId.of("America/Los_Angeles")),
        Location("Toronto", 43.6532, -79.3832, ZoneId.of("America/Toronto")),
        Location("Dubai", 25.2048, 55.2708, ZoneId.of("Asia/Dubai")),
        Location("Nairobi", -1.2921, 36.8219, ZoneId.of("Africa/Nairobi")),
        Location("Singapore", 1.3521, 103.8198, ZoneId.of("Asia/Singapore")),
        Location("Sydney", -33.8688, 151.2093, ZoneId.of("Australia/Sydney")),
        Location("Auckland", -36.8485, 174.7633, ZoneId.of("Pacific/Auckland")),
    )

    val default: Location get() = all.first()
}
