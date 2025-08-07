import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

// === CONFIG ===
const val WEBHOOK_URL =
        "https://discord.com/api/webhooks/1363606767765819503/ahU60WWHSOaQI29nghwEZF8hPZyXIkvcwIlRY9ffOIbP6zsAWFuNA-ueYzsXbs8q9L8C"
const val SEARCH_URL =
        "https://www.olx.com.br/animais-e-acessorios/gatos?q=angor%C3%A1&region=piracicaba"
const val CACHE_FILE = "sent_ads.json"

@Serializable data class DiscordMessage(val content: String)

class AngoraMonitor {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // === HELPERS ===
    private fun loadCache(): MutableSet<String> {
        val file = File(CACHE_FILE)
        return if (file.exists()) {
            try {
                val content = file.readText()
                json.decodeFromString<List<String>>(content).toMutableSet()
            } catch (e: Exception) {
                println("Erro ao carregar cache: ${e.message}")
                mutableSetOf()
            }
        } else {
            mutableSetOf()
        }
    }

    private fun saveCache(cache: Set<String>) {
        try {
            val file = File(CACHE_FILE)
            val jsonString = json.encodeToString(cache.toList())
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("Erro ao salvar cache: ${e.message}")
        }
    }

    private fun extractPrice(text: String): Double {
        val regex = Regex("""R\$\s*([\d.,]+)""")
        val match = regex.find(text) ?: return Double.POSITIVE_INFINITY

        val priceStr = match.groupValues[1].replace(".", "").replace(",", ".")

        return try {
            priceStr.toDouble()
        } catch (e: NumberFormatException) {
            Double.POSITIVE_INFINITY
        }
    }

    private suspend fun sendDiscordNotification(title: String, link: String, price: Double) {
        val message =
                DiscordMessage(
                        "🐱 **Novo gato Angorá abaixo de R$300!**\n**$title**\n💰 R$%.2f\n🔗 $link".format(
                                price
                        )
                )

        val jsonBody = json.encodeToString(message)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url(WEBHOOK_URL).post(requestBody).build()

        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        println("✅ Notificação enviada para Discord!")
                    } else {
                        println("❌ Erro ao enviar notificação: ${response.code}")
                    }
                }
            }
        } catch (e: IOException) {
            println("❌ Erro de conexão ao enviar notificação: ${e.message}")
        }
    }

    // === MAIN FUNCTION ===
    suspend fun checkAds() {
        println("🔍 Verificando novos anúncios...")
        val alreadySent = loadCache()

        try {
            val request =
                    Request.Builder()
                            .url(SEARCH_URL)
                            .addHeader(
                                    "User-Agent",
                                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
                            )
                            .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            if (!response.isSuccessful) {
                println("❌ Erro ao acessar OLX: ${response.code}")
                return
            }

            val html = response.body?.string() ?: return
            val doc = Jsoup.parse(html)
            println(doc)
            val ads = doc.select("li.sc-1fcmfeb-2")

            println("📋 Encontrados ${ads.size} anúncios")

            var newAdsFound = 0

            for (ad in ads) {
                val aTag = ad.selectFirst("a") ?: continue
                val link = aTag.attr("href")
                val title = ad.text().trim()

                val price = extractPrice(ad.text())

                if ("gato" in title.lowercase() && price < 5000.0 && link !in alreadySent) {
                    println("🎯 Novo anúncio encontrado: $title - R$%.2f".format(price))
                    sendDiscordNotification(title, link, price)
                    alreadySent.add(link)
                    newAdsFound++
                }
            }

            if (newAdsFound == 0) {
                println("📭 Nenhum novo anúncio encontrado")
            } else {
                println("✨ $newAdsFound novo(s) anúncio(s) enviado(s)!")
            }

            saveCache(alreadySent)
        } catch (e: Exception) {
            println("❌ Erro durante verificação: ${e.message}")
        }
    }
}

suspend fun main() {
    val monitor = AngoraMonitor()
    monitor.checkAds()
}
