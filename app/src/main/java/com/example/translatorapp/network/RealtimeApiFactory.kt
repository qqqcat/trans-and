import com.example.translatorapp.network.AzureOpenAIConfig
import com.example.translatorapp.network.RealtimeApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Singleton
class RealtimeApiFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val azureConfig: AzureOpenAIConfig
) {
    fun create(): RealtimeApi {
        return RealtimeApi(okHttpClient, json, azureConfig)
    }
}
