import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.sqrt

data class TimeSeriesDate(val date: Date) {
    companion object {
        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }

    override fun toString() = dateFormatter.format(date.toInstant().atZone(ZoneOffset.UTC))
}

class DailyTimeSeries(val name: String, val data: Map<TimeSeriesDate, Double>) {
    val average = data.values.sum().div(data.size)
    val centeredData = data.map { (k, v) -> Pair(k, v - average) }.toMap()
    val stdDev = sqrt(centeredData.values.map { it * it }.sum().div(data.size - 1))

    fun cov(ts: DailyTimeSeries): Double {
        val keys = centeredData.keys.intersect(ts.centeredData.keys)

        val sum = keys.map { key ->
            centeredData.getValue(key) * ts.centeredData.getValue(key)
        }.sum()
        return sum.div(keys.size - 1)
                .div(stdDev * ts.stdDev)
    }
}

