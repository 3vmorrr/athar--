package org.athar.app.core.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Envelope(val data: JsonElement?, val error: RemoteError?)
data class RemoteError(val code: String?, val message: String?, val fields: JsonObject?)
class ApiException(val status: Int, val code: String, override val message: String, val fields: JsonObject? = null) : IOException(message)

interface ApiTransport {
    suspend fun request(method: String, path: String, body: Map<String, Any?> = emptyMap(), query: Map<String, String> = emptyMap(), bearer: String? = null, language: String = "ar"): JsonElement
}

private interface RetrofitApi {
    @GET suspend fun get(@Url path: String, @Header("Authorization") authorization: String?, @Header("Accept-Language") language: String, @QueryMap query: Map<String, String>): Response<Envelope>
    @POST suspend fun post(@Url path: String, @Header("Authorization") authorization: String?, @Header("Accept-Language") language: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Envelope>
    @PUT suspend fun put(@Url path: String, @Header("Authorization") authorization: String?, @Header("Accept-Language") language: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Envelope>
    @PATCH suspend fun patch(@Url path: String, @Header("Authorization") authorization: String?, @Header("Accept-Language") language: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Envelope>
    @HTTP(method = "DELETE", hasBody = true) suspend fun delete(@Url path: String, @Header("Authorization") authorization: String?, @Header("Accept-Language") language: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Envelope>
}

class RetrofitTransport(baseUrl: String, private val gson: Gson = Gson()) : ApiTransport {
    private val api = Retrofit.Builder().baseUrl(baseUrl)
        .client(OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create(gson)).build().create(RetrofitApi::class.java)

    override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
        require(!path.startsWith("http") && !path.startsWith("/") && !path.contains("..")) { "API paths must stay within the configured origin." }
        val authorization = bearer?.let { "Bearer $it" }
        val response = when (method) {
            "GET" -> api.get(path, authorization, language, query)
            "POST" -> api.post(path, authorization, language, body)
            "PUT" -> api.put(path, authorization, language, body)
            "PATCH" -> api.patch(path, authorization, language, body)
            "DELETE" -> api.delete(path, authorization, language, body)
            else -> error("Unsupported request method")
        }
        val envelope = if (response.isSuccessful) response.body() else {
            val raw = response.errorBody()?.string().orEmpty()
            runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull()
        }
        if (!response.isSuccessful || envelope?.error != null) {
            throw ApiException(response.code(), envelope?.error?.code ?: "service_unavailable", envelope?.error?.message ?: "", envelope?.error?.fields)
        }
        return envelope?.data ?: JsonNull.INSTANCE
    }
}
