import kotlinx.coroutines.experimental.runBlocking
import java.util.*
import kotlin.math.abs

fun main(args: Array<String>) {
    runBlocking {
        val coins = CryptoCompare.coins()

        val timeseries = mutableListOf<DailyTimeSeries>()

        coins.forEach { coin ->
            val priceClose = CryptoCompare.priceClose(coin.symbol, "USD")
            val assetReturns = priceClose.returns()
            timeseries.add(DailyTimeSeries(coin.coinName, assetReturns.mapKeys { TimeSeriesDate(it.key) }))
        }

        timeseries.forEach({ x ->
            print(String.format("%20s ", x.name))
            timeseries.forEach({ y -> System.out.printf("%.2f ", x.cov(y)) })
            println()
        })
    }
}

private fun Map<Date, Double>.returns(): Map<Date, Double> {
    val sortedDates = keys.sorted()
    val daysAndPrev = mutableListOf<Pair<Date, Date>>()

    sortedDates.forEachIndexed { idx, date ->
        if (idx > 0) {
            daysAndPrev.add(Pair(date, sortedDates[idx - 1]))
        }
    }

    return daysAndPrev.map { (day, prev) ->
        val prevDayClose = get(prev)!!
        val thisDayClose = get(day)!!
        val assetReturn =  if (abs(prevDayClose) < 1e-9) 0.0 else thisDayClose / prevDayClose - 1
        Pair(day, assetReturn)
    }.toMap()
}
