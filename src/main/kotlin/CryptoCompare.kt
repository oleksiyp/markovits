import com.fasterxml.jackson.annotation.JsonProperty
import com.squareup.okhttp.Cache
import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.OkHttpClient
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.sqrt


object CryptoCompare {
    data class Coin(
            val id: String,
            val url: String,
            val imageUrl: String?,
            val name: String,
            val symbol: String,
            val coinName: String,
            val fullName: String,
            val algorithm: String,
            val proofType: String,
            val fullyPremined: String,
            val totalCoinSupply: String,
            val preMinedValue: String,
            val totalCoinsFreeFloat: String,
            val sortOrder: String,
            val sponsored: String)

    data class WatchList(val coinIs: String,
                         val sponsored: String)

    data class ConversionType(@JsonProperty("type") val type: String,
                              @JsonProperty("conversionSymbol") val conversionSymbol: String)

    data class CoinListResponse(val response: String,
                                val message: String,
                                val baseImageUrl: String,
                                val baseLinkUrl: String,
                                val defaultWatchlist: WatchList,
                                val type: String,
                                val data: Map<String, Coin>)

    data class DayPrice(@JsonProperty("time") val time: Long,
                        @JsonProperty("close") val close: Double,
                        @JsonProperty("high") val high: Double,
                        @JsonProperty("low") val low: Double,
                        @JsonProperty("open") val open: Double,
                        @JsonProperty("volumefrom") val volumeFrom: Double,
                        @JsonProperty("volumeto") val volumeTo: Double)

    data class DayHistoryResponse(val response: String,
                                  val type: String,
                                  val message: String?,
                                  val aggregated: Boolean,
                                  val timeTo: Long,
                                  val timeFrom: Long,
                                  val firstValueInArray: Boolean,
                                  val conversionType: ConversionType?,
                                  val data: List<DayPrice>)

    val httpClient: OkHttpClient

    init {
        val cacheDir = File("cache")
        cacheDir.mkdirs()
        val cache = Cache(cacheDir, (10 * 1024 * 1024).toLong()) // 10MB cache

        httpClient = OkHttpClient().setCache(cache)
    }


    suspend fun coins(): List<Coin> {
        val response = httpClient.getJson<CoinListResponse> {
            url("https://min-api.cryptocompare.com/data/all/coinlist")
        }
        val ids = response.defaultWatchlist.coinIs.split(",") + "4432"
        return response.data.values.toList().filter { ids.contains(it.id) }.sortedBy { it.name }
    }


    suspend fun priceClose(from: String, to: String): Map<Date, Double> {
        val response = httpClient.getJson<DayHistoryResponse> {
            url(HttpUrl.parse("https://min-api.cryptocompare.com/data/histoday")
                    .newBuilder()
                    .addQueryParameter("fsym", from)
                    .addQueryParameter("tsym", to)
                    .addQueryParameter("limit", "365")
                    .addQueryParameter("aggregate", "1")
                    .addQueryParameter("e", "CCCAGG")
                    .build())
        }
        return response.data.map { Pair(Date(it.time * 1000), it.close) }
                .sortedBy { it.first.time }.toMap()
    }
}