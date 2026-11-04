package com.coopercpu.reloj

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

data class DayForecast(val type: Int, val max: Int, val min: Int)
data class HourForecast(val type: Int, val temp: Int, val rain: Int)

data class WeatherNow(
    val type: Int,          // tipo de clima en la tabla de IDO
    val temp: Int,
    val max: Int,
    val min: Int,
    val humidity: Int,
    val rain: Int,          // probabilidad de lluvia %
    val uv: Int,
    val wind: Int,          // km/h
    val sunrise: Pair<Int, Int>,
    val sunset: Pair<Int, Int>,
    val days: List<DayForecast>,
    val hours: List<HourForecast>,
    val sunTimes: List<Pair<Pair<Int, Int>, Pair<Int, Int>>>
)

/**
 * Clima desde Open-Meteo: gratis, sin cuenta. Solo se envían las coordenadas de la ciudad.
 * Todas las llamadas son bloqueantes: usar desde un hilo de fondo.
 */
object WeatherClient {

    fun searchCity(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=es&format=json"))
        val arr = o.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            Place(
                name = r.optString("name"),
                detail = listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                lat = r.optDouble("latitude"),
                lon = r.optDouble("longitude")
            )
        }
    }

    fun forecast(lat: Double, lon: Double): WeatherNow {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val hourly = o.getJSONObject("hourly")
        val isDay = cur.optInt("is_day", 1) == 1

        val dCodes = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRise = daily.getJSONArray("sunrise")
        val dSet = daily.getJSONArray("sunset")
        val dUv = daily.optJSONArray("uv_index_max")
        val dRain = daily.optJSONArray("precipitation_probability_max")

        val days = (0 until dCodes.length()).map {
            DayForecast(idoType(dCodes.optInt(it), true), dMax.optDouble(it).toInt(), dMin.optDouble(it).toInt())
        }
        val sunTimes = (0 until dRise.length()).map { hm(dRise.optString(it)) to hm(dSet.optString(it)) }

        // Próximas horas desde la hora actual
        val hTimes = hourly.getJSONArray("time")
        val hTemp = hourly.getJSONArray("temperature_2m")
        val hCode = hourly.getJSONArray("weather_code")
        val hRain = hourly.getJSONArray("precipitation_probability")
        val nowHour = cur.optString("time").take(13)   // "2026-11-04T01"
        var start = 0
        for (i in 0 until hTimes.length()) if (hTimes.optString(i).take(13) == nowHour) { start = i; break }
        val hours = (start until minOf(start + 24, hTimes.length())).map {
            val h = hTimes.optString(it).substring(11, 13).toIntOrNull() ?: 12
            HourForecast(idoType(hCode.optInt(it), h in 7..19), hTemp.optDouble(it).toInt(), hRain.optInt(it))
        }

        return WeatherNow(
            type = idoType(cur.optInt("weather_code"), isDay),
            temp = Math.round(cur.optDouble("temperature_2m")).toInt(),
            max = days.firstOrNull()?.max ?: 0,
            min = days.firstOrNull()?.min ?: 0,
            humidity = cur.optInt("relative_humidity_2m"),
            rain = dRain?.optInt(0) ?: hours.firstOrNull()?.rain ?: 0,
            uv = dUv?.optDouble(0)?.toInt() ?: 0,
            wind = cur.optDouble("wind_speed_10m").toInt(),
            sunrise = sunTimes.firstOrNull()?.first ?: (7 to 0),
            sunset = sunTimes.firstOrNull()?.second ?: (20 to 0),
            days = days,
            hours = hours,
            sunTimes = sunTimes
        )
    }

    /** Códigos meteorológicos WMO → tipos de clima del reloj IDO. */
    fun idoType(wmo: Int, day: Boolean): Int = when (wmo) {
        0 -> if (day) 1 else 11
        1, 2 -> if (day) 2 else 12
        3 -> 3
        45, 48 -> 17
        51, 53, 55, 56, 57, 61, 63 -> 4
        80, 81 -> 18
        65, 82 -> 5
        66, 67 -> 8
        71, 73, 75, 77, 85, 86 -> 7
        95 -> 6
        96, 99 -> 49
        else -> 0
    }

    private fun hm(iso: String): Pair<Int, Int> {
        // "2026-11-04T06:42"
        val t = iso.substringAfter('T', "07:00")
        return (t.substring(0, 2).toIntOrNull() ?: 7) to (t.substring(3, 5).toIntOrNull() ?: 0)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "MiReloj/0.2")
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
