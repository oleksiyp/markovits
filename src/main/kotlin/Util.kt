import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.squareup.okhttp.*
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import java.io.IOException


suspend fun Call.await() = suspendCancellableCoroutine<Response> { cont ->
    enqueue(object : Callback {
        override fun onFailure(request: Request, e: IOException) {
            if (cont.isCancelled) return
            cont.resumeWithException(e)
        }

        override fun onResponse(response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCompletion {
        if (cont.isCancelled) {
            try {
                cancel()
            } catch (thr: Throwable) {
                // skip
            }
        }
    }
}

inline suspend fun <reified T : Any> OkHttpClient.getJson(namingStrategy: PropertyNamingStrategy? = PropertyNamingStrategy.UPPER_CAMEL_CASE,
                                                          noinline reuqestBuilder: Request.Builder.() -> Request.Builder): T {
    val response = httpCall(reuqestBuilder)
    val mapper = jacksonObjectMapper()
    mapper.propertyNamingStrategy = namingStrategy
    val input = response.body().byteStream()
    return mapper.readValue(input, T::class.java)
}

suspend fun OkHttpClient.httpCall(reuqestBuilder: Request.Builder.() -> Request.Builder): Response {
    val request = Request.Builder().reuqestBuilder().build()
    return newCall(request).await()
}


