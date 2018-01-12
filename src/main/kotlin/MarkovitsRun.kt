import kotlinx.coroutines.experimental.runBlocking
import org.ojalgo.finance.portfolio.FinancePortfolio
import org.ojalgo.finance.portfolio.MarkowitzModel
import org.ojalgo.matrix.PrimitiveMatrix
import java.util.*
import kotlin.math.abs


class Markovits {
    var optimalWeights: DoubleArray? = null

    fun optimize(riskAversion: Double,
                 returnCov: Array<DoubleArray>,
                 assetReturn: DoubleArray,
                 shortingAllowed: Boolean = false) {

        val model = MarkowitzModel(
                returnCov.toMatrix(),
                arrayOf(assetReturn).toMatrix())

        model.isShortingAllowed = shortingAllowed
        model.setRiskAversion(riskAversion.toBigDecimal())

        optimalWeights = model.weights.map { it.toDouble() }.toDoubleArray()
    }

    private fun Array<DoubleArray>.toMatrix(): PrimitiveMatrix {
        val builder = PrimitiveMatrix.FACTORY.getBuilder(size, get(0).size)
        repeat(builder.countRows().toInt()) { row ->
            repeat(builder.countColumns().toInt()) { col ->
                builder.add(row.toLong(), col.toLong(), get(row).get(col))
            }
        }
        return builder.build()
    }
}



fun main(args: Array<String>) {
    runBlocking {
        val coins = CryptoCompare.coins()

        val timeseries = mutableListOf<DailyTimeSeries>()

        coins.forEach { coin ->
            try {
                val priceClose = CryptoCompare.priceClose(coin.symbol, "USD")
                        .filterValues { it -> it > 1e-6 }
                val assetReturns = priceClose.returns()
                if (assetReturns.size == 15) {
                    val ts = DailyTimeSeries(coin.coinName, assetReturns.mapKeys { TimeSeriesDate(it.key) })
                    if (abs(ts.stdDev) > 1e-6 && ts.stdDev < 0.30) {
                        timeseries.add(ts)
                        println("DATA ${coin.coinName} ${assetReturns.size}")
                    }
                }
            } catch(ex: Throwable) {
                // skip
            }
        }

        val n = timeseries.size

        val means = timeseries.map { it.average }.toDoubleArray()
        val devs = timeseries.map { it.stdDev }.toDoubleArray()
        val cv = (1..n).map { i ->
            val row = (1..n).map { j ->
                timeseries[i - 1].cov(timeseries[j - 1])
            }.toDoubleArray()
            println("CV ${timeseries[i - 1].name} ${Arrays.toString(row)}")
            row
        }.toTypedArray()

        fun optimizeForRisk(risk: Double, shorts: Boolean) {
            val markovits = Markovits()
            markovits.optimize(risk, cv, devs, shorts)

            val optimalReturn = means.zip(markovits.optimalWeights!!).map { (x, y) -> x * y }.sum()
            val optimalRisk = devs.zip(markovits.optimalWeights!!).map { (x, y) -> x * y }.sum()
            println(String.format("### Risk aversion factor=%.2f Risk=%.2f%% Return=%.2f%%${if(shorts) " Shorts allowed" else ""}",
                    risk,
                    optimalRisk * 100,
                    optimalReturn * 100))

            println("```")
            repeat(n) {
                if (abs(markovits.optimalWeights!![it]) < 1e-6) {
                    return@repeat
                }

                println(String.format("%20s W=%7.2f%% Avg=%7.2f%% StdDev=%7.2f%%",
                        timeseries[it].name,
                        markovits.optimalWeights!![it] * 100,
                        timeseries[it].average * 100,
                        timeseries[it].stdDev * 100))
            }
            println("```")
            println()
        }

        for (riskAversion in (1000 downTo 101).step(100)) {
            optimizeForRisk(riskAversion / 100.0, false)
        }

        for (riskAversion in (100 downTo 1).step(1)) {
            optimizeForRisk(riskAversion / 100.0, false)
        }

//        for (riskAversion in (1000 downTo 101).step(100)) {
//            optimizeForRisk(riskAversion / 100.0, true)
//        }
//
//        for (riskAversion in (100 downTo 1).step(1)) {
//            optimizeForRisk(riskAversion / 100.0, true)
//        }
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
        val assetReturn =  thisDayClose / prevDayClose - 1
        Pair(day, assetReturn)
    }.toMap()
}
