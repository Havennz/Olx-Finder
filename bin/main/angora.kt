import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// === CONFIG ===
const val WEBHOOK_URL = "https://discord.com/api/webhooks/1363606767765819503/ahU60WWHSOaQI29nghwEZF8hPZyXIkvcwIlRY9ffOIbP6zsAWFuNA-ueYzsXbs8q9L8C"
const val SEARCH_URL = "https://www.olx.com.br/animais-de-estimacao/gatos?q=angor%C3%A1&region=piracicaba"
const val CACHE_FILE = "sent_ads.json"
const val MAX_PRICE = 3000.0

@Serializable
data class DiscordMessage(val content: String)

@Serializable
data class Ad(
    val subject: String? = null,
    val priceValue: String? = null,
    val friendlyUrl: String? = null,
    val location: String? = null,
    val listId: Long? = null,
    val properties: List<AdProperty> = emptyList(),
    val advertisingId: String? = null
)

@Serializable
data class AdProperty(
    val name: String,
    val label: String,
    val value: String
)

@Serializable
data class PageProps(
    val ads: List<Ad>
)

@Serializable
data class Props(
    val pageProps: PageProps
)

@Serializable
data class NextData(
    val props: Props
)

class AngoraMonitor {
    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // === HELPERS ===
    private fun loadCache(): MutableSet<Long> {
        val file = File(CACHE_FILE)
        return if (file.exists()) {
            try {
                val content = file.readText()
                json.decodeFromString<List<Long>>(content).toMutableSet()
            } catch (e: Exception) {
                println("Erro ao carregar cache: ${e.message}")
                mutableSetOf()
            }
        } else {
            mutableSetOf()
        }
    }

    private fun saveCache(cache: Set<Long>) {
        try {
            val file = File(CACHE_FILE)
            val jsonString = json.encodeToString(cache.toList())
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("Erro ao salvar cache: ${e.message}")
        }
    }

    private fun extractPrice(priceValue: String?): Double {
        if (priceValue == null) return Double.POSITIVE_INFINITY

        val regex = Regex("""R\$\s*([\d.,]+)""")
        val match = regex.find(priceValue) ?: return Double.POSITIVE_INFINITY

        val priceStr = match.groupValues[1].replace(".", "").replace(",", ".")

        return try {
            priceStr.toDouble()
        } catch (_: NumberFormatException) {
            Double.POSITIVE_INFINITY
        }
    }

    private fun isValidAd(ad: Ad): Boolean {
        return ad.subject != null &&
                ad.friendlyUrl != null &&
                ad.location != null &&
                ad.listId != null &&
                ad.advertisingId == null &&
                ad.subject.isNotBlank() &&
                ad.friendlyUrl.isNotBlank()
    }

    private fun isDonation(ad: Ad): Boolean {
        return ad.properties.any {
            it.name == "donate" && it.value == "Sim"
        } || ad.priceValue == "R$ 0" || ad.priceValue == null
    }

    private suspend fun sendDiscordNotification(ad: Ad, price: Double) {
        val priceText = if (price == 0.0) "**DOAÇÃO**" else "R$ %.2f".format(price)
        val message = DiscordMessage(
            "🐱 **Novo gato Angorá encontrado!**\n" +
                    "**${ad.subject ?: "Sem título"}**\n" +
                    "💰 $priceText\n" +
                    "📍 ${ad.location ?: "Localização não informada"}\n" +
                    "🔗 ${ad.friendlyUrl ?: ""}"
        )

        val jsonBody = json.encodeToString(message)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .post(requestBody)
            .build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        println("Notificação enviada para Discord!")
                    } else {
                        println("Erro ao enviar notificação: ${response.code}")
                    }
                }
            }
        } catch (e: IOException) {
            println("❌ Erro de conexão ao enviar notificação: ${e.message}")
        }
    }

    // === MAIN FUNCTION ===
    suspend fun checkAds() {
        println("Verificando novos anúncios...")
        val alreadySent = loadCache()

        try {
            val request = Request.Builder()
                .url(SEARCH_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                println("Erro ao acessar OLX: ${response.code}")
                return
            }

            val html = response.body?.string() ?: return
            val doc = Jsoup.parse(html)

            val scriptElement = doc.selectFirst("script#__NEXT_DATA__")
            if (scriptElement == null) {
                println("Não foi possível encontrar dados JSON na página")
                return
            }

            val jsonContent = scriptElement.html()
            val nextData = try {
                json.decodeFromString<NextData>(jsonContent)
            } catch (e: Exception) {
                println("Erro ao parsear JSON: ${e.message}")
                return
            }

            val ads = nextData.props.pageProps.ads
            println("Encontrados ${ads.size} anúncios")

            var newAdsFound = 0

            for (ad in ads) {
                if (!isValidAd(ad)) {
                    continue
                }
                if (ad.location?.endsWith("SP") == false){
                    continue
                }

                val price = extractPrice(ad.priceValue)
                val isDonation = isDonation(ad)

                if (ad.listId!! !in alreadySent &&
                    (isDonation || price <= MAX_PRICE)) {

                    println("🎯 Novo anúncio encontrado: ${ad.subject} - ${if (isDonation) "DOAÇÃO" else "R$ %.2f".format(price)}")
                    sendDiscordNotification(ad, if (isDonation) 0.0 else price)
                    alreadySent.add(ad.listId)
                    newAdsFound++
                }
            }

            if (newAdsFound == 0) {
                println("Nenhum novo anúncio encontrado")
            } else {
                println("$newAdsFound novo(s) anúncio(s) enviado(s)!")
            }

            saveCache(alreadySent)

        } catch (e: Exception) {
            println("Erro durante verificação: ${e.message}")
            e.printStackTrace()
        }
    }
}

suspend fun main() {
    val monitor = AngoraMonitor()

    // Executar uma vez
    monitor.checkAds()

    // Ou executar em loop (descomente as linhas abaixo)
    /*
    while (true) {
        monitor.checkAds()
        println("⏰ Aguardando 5 minutos...")
        delay(5 * 60 * 1000) // 5 minutos
    }
    */
}