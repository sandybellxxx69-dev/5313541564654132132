package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

import okhttp3.HttpUrl.Companion.toHttpUrl

class SdmxApiService {

    private val cookieJar = object : CookieJar {
        private val cache = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { newCookie ->
                cache.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
                cache.add(newCookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cache
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    suspend fun login(userSdmx: String, passSdmx: String): Boolean = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("referrer", "")
            .add("username", userSdmx)
            .add("password", passSdmx)
            .add("login", "")
            .build()

        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/login")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36")
            .header("Origin", "https://sdmx.vip")
            .header("Referer", "https://sdmx.vip/resellers/login")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            // If logged in correctly, usually redirects or updates cookies.
            // Check if we got PHPSESSID in our cookieJar
            val cookies = cookieJar.loadForRequest("https://sdmx.vip".toHttpUrl())
            return@use cookies.any { it.name == "PHPSESSID" }
        }
    }

    suspend fun fetchSheets(): Pair<List<SheetsUser>, List<SheetsUser>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://script.google.com/macros/s/AKfycbxiBjtQmyOubnbyJQfJrT4Vs0DhJ94vSnPgfkCwUirMfcD3GRqGflKC--e1NXkHCl-V/exec?hoja=Permanentes")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al consultar Sheets: ${response.code}")
            val body = response.body?.string() ?: throw Exception("Respuesta vacía de Sheets")
            
            val json = JSONObject(body)
            if (json.optString("status") != "ok") throw Exception("Error en la respuesta JSON de Sheets")
            
            val datos = json.getJSONArray("datos")
            val vigentes = mutableListOf<SheetsUser>()
            val noVigentes = mutableListOf<SheetsUser>()
            
            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            for (i in 0 until datos.length()) {
                val obj = datos.getJSONObject(i)
                val user = SheetsUser(
                    usuario = obj.optString("usuario"),
                    password = obj.optString("password"),
                    vencimiento = obj.optString("vencimiento"),
                    id = obj.optString("id")
                )
                
                try {
                    // Limpiamos la cadena por si acaso trae espacios extra o formatos raros
                    val fechaLimpia = user.vencimiento.trim().substringBefore("T")
                    
                    val isYyyyMmDd = fechaLimpia.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
                    val formatter = if (isYyyyMmDd) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    } else {
                        SimpleDateFormat("dd-MM-yyyy", Locale.US)
                    }
                    val fechaVec = formatter.parse(fechaLimpia)
                    
                    if (fechaVec != null && !fechaVec.before(todayCal)) {
                        vigentes.add(user)
                    } else {
                        noVigentes.add(user)
                    }
                } catch (e: Exception) {
                    noVigentes.add(user) // Si hay error parseando fecha, no lo tocamos
                }
            }
            return@use Pair(vigentes, noVigentes)
        }
    }

    suspend fun deleteUser(id: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/api?action=line&sub=delete&user_id=$id")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Host", "sdmx.vip")
            .header("Referer", "https://sdmx.vip/resellers/lines?order=0&dir=desc")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        }
    }

    suspend fun createUser(user: SheetsUser) = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("action", "line")
            .add("trial", "1")
            .add("bouquets_selected", "")
            .add("username", user.usuario)
            .add("password", user.password)
            .add("package", "150")
            .add("package_cost", "0")
            .add("package_duration", "24 hours")
            .add("max_connections", "2")
            // Exp_date usually isn't required to be exact if the system recalculates, 
            // but we add what the user provided.
            .add("exp_date", "2026-04-15 21:24") 
            .add("contact", "")
            .add("reseller_notes", "")
            .add("isp_clear", "")
            .add("bouquets_selected[]", "19")
            .add("bouquets_selected[]", "24")
            .add("bouquets_selected[]", "21")
            .add("bouquets_selected[]", "8")
            .add("bouquets_selected[]", "23")
            .add("bouquets_selected[]", "96")
            .build()

        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/post.php?action=line")
            .header("Accept", "*/*")
            .header("Host", "sdmx.vip")
            .header("Origin", "https://sdmx.vip")
            .header("Referer", "https://sdmx.vip/resellers/line?trial=1")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Requested-With", "XMLHttpRequest")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        }
    }

    suspend fun getUpdatedTable(vigentes: List<SheetsUser>): Map<String, String> = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val request = Request.Builder()
            .url("http://sdmx.vip:8080/resellers/table?draw=1&columns[0][data]=0&columns[0][name]=&columns[0][searchable]=true&columns[0][orderable]=true&columns[0][search][value]=&columns[0][search][regex]=false&columns[1][data]=1&columns[1][name]=&columns[1][searchable]=true&columns[1][orderable]=true&columns[1][search][value]=&columns[1][search][regex]=false&columns[2][data]=2&columns[2][name]=&columns[2][searchable]=true&columns[2][orderable]=true&columns[2][search][value]=&columns[2][search][regex]=false&columns[3][data]=3&columns[3][name]=&columns[3][searchable]=true&columns[3][orderable]=true&columns[3][search][value]=&columns[3][search][regex]=false&columns[4][data]=4&columns[4][name]=&columns[4][searchable]=true&columns[4][orderable]=true&columns[4][search][value]=&columns[4][search][regex]=false&columns[5][data]=5&columns[5][name]=&columns[5][searchable]=true&columns[5][orderable]=false&columns[5][search][value]=&columns[5][search][regex]=false&columns[6][data]=6&columns[6][name]=&columns[6][searchable]=true&columns[6][orderable]=true&columns[6][search][value]=&columns[6][search][regex]=false&columns[7][data]=7&columns[7][name]=&columns[7][searchable]=true&columns[7][orderable]=false&columns[7][search][value]=&columns[7][search][regex]=false&columns[8][data]=8&columns[8][name]=&columns[8][searchable]=true&columns[8][orderable]=true&columns[8][search][value]=&columns[8][search][regex]=false&columns[9][data]=9&columns[9][name]=&columns[9][searchable]=true&columns[9][orderable]=true&columns[9][search][value]=&columns[9][search][regex]=false&columns[10][data]=10&columns[10][name]=&columns[10][searchable]=true&columns[10][orderable]=true&columns[10][search][value]=&columns[10][search][regex]=false&columns[11][data]=11&columns[11][name]=&columns[11][searchable]=true&columns[11][orderable]=false&columns[11][search][value]=&columns[11][search][regex]=false&order[0][column]=0&order[0][dir]=desc&start=0&length=-1&search[value]=&search[regex]=false&id=lines&filter=&reseller=&_=$ts")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Host", "sdmx.vip")
            .header("Referer", "http://sdmx.vip:8080/resellers/lines?order=0&dir=desc")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al obtener tabla: HTTP ${response.code}")
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@use emptyMap()
            
            val newUserIdMap = mutableMapOf<String, String>()
            
            for (i in 0 until dataArray.length()) {
                val row = dataArray.getJSONArray(i)
                // Extract plain text to avoid HTML tags
                val firstCol = row.optString(0, "").replace(Regex("<.*?>"), "").trim()
                val secondCol = row.optString(1, "").replace(Regex("<.*?>"), "").trim()
                val thirdCol = row.optString(2, "").replace(Regex("<.*?>"), "").trim()

                val matchedUser = vigentes.find { it.usuario == secondCol || it.usuario == thirdCol }
                if (matchedUser != null) {
                    newUserIdMap[matchedUser.usuario] = firstCol
                }
            }
            return@use newUserIdMap
        }
    }

    suspend fun updateSheetsIds(updates: List<SheetsUser>) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("hoja", "Permanentes")
        
        val datosArray = JSONArray()
        updates.forEach {
            val userObj = JSONObject()
            userObj.put("id", it.id)
            userObj.put("usuario", it.usuario)
            userObj.put("password", it.password)
            userObj.put("vencimiento", it.vencimiento)
            datosArray.put(userObj)
        }
        root.put("datos", datosArray)

        val body = root.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://script.google.com/macros/s/AKfycbxiBjtQmyOubnbyJQfJrT4Vs0DhJ94vSnPgfkCwUirMfcD3GRqGflKC--e1NXkHCl-V/exec")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error al actualizar Sheets: HTTP ${response.code}")
        }
    }
}
