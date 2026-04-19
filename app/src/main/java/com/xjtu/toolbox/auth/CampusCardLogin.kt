package com.xjtu.toolbox.auth

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 校园卡系统登录（ncard.xjtu.edu.cn）
 *
 * 新链路：
 * - 统一走新 CAS（login.xjtu.edu.cn），不再使用旧 cas.xjtu.edu.cn。
 * - 入口页面：/plat/shouyeUser
 * - 账单页面：/campus-card/billing/list
 */
class CampusCardLogin(
    existingClient: OkHttpClient? = null,
    visitorId: String? = null
) : XJTULogin(
    loginUrl = LOGIN_URL,
    existingClient = existingClient,
    visitorId = visitorId
) {
    companion object {
        private const val TAG = "CampusCardLogin"
        const val BASE_URL = "https://ncard.xjtu.edu.cn"
        const val HOME_URL = "$BASE_URL/plat/shouyeUser"
        const val BILLING_URL = "$BASE_URL/campus-card/billing/list"
        const val LOGIN_URL = HOME_URL
    }

    /** 校园卡账号（非学号） */
    var cardAccount: String? = null
        internal set

    /** 系统是否就绪 */
    var systemReady: Boolean = false
        private set

    override fun postLogin(response: Response) {
        val finalUrl = response.request.url.toString()
        val body = lastResponseBody
        Log.d(TAG, "postLogin: finalUrl=$finalUrl, bodyLen=${body.length}")
        if (tryExtractInfo(body, finalUrl)) return

        // 补探测首页和账单页，触发 ncard 会话建立
        if (tryVisit(HOME_URL)) return
        if (tryVisit(BILLING_URL)) return

        Log.w(TAG, "postLogin: ncard system not ready")
    }

    private fun tryExtractInfo(html: String, url: String): Boolean {
        // 提取账号（尽量宽松匹配，兼容前端变更）
        val patterns = listOf(
            Regex("\"account\"\\s*:\\s*\"(\\d{5,20})\""),
            Regex("\"_account\"\\s*:\\s*\"(\\d{5,30})\""),
            Regex("account=([0-9]{5,20})")
        )
        patterns.asSequence().mapNotNull { it.find(html)?.groupValues?.getOrNull(1) }.firstOrNull()
            ?.let {
                cardAccount = it
                Log.d(TAG, "Found cardAccount from ncard page: $cardAccount")
            }

        val host = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" }
        val isNcardHost = host.equals("ncard.xjtu.edu.cn", ignoreCase = true)
        val isCasPage = url.contains("login.xjtu.edu.cn/cas", ignoreCase = true)
        val looksLikeError = html.contains("404") && html.length < 2000

        if (isNcardHost && !isCasPage && !looksLikeError) {
            systemReady = true
            Log.d(TAG, "ncard page reached (host=$host), system ready")
            return true
        }

        Log.d(TAG, "tryExtractInfo: host='$host', bodyLen=${html.length}, cas=$isCasPage")
        return false
    }

    private fun tryVisit(url: String): Boolean {
        return try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.use { it.string() } ?: ""
            val finalUrl = resp.request.url.toString()
            Log.d(TAG, "tryVisit: $url -> code=${resp.code}, finalUrl=$finalUrl, bodyLen=${body.length}")
            tryExtractInfo(body, finalUrl)
        } catch (e: Exception) {
            Log.w(TAG, "tryVisit failed: $url, msg=${e.message}")
            false
        }
    }

    /**
     * 重新认证（会话过期时调用）
     * 仅保留新 CAS + ncard 链路。
     */
    private val reAuthLock = Any()
    fun reAuthenticate(): Boolean = synchronized(reAuthLock) {
        Log.d(TAG, "reAuthenticate: refreshing ncard session...")
        systemReady = false
        try {
            if (tryVisit(HOME_URL)) return true
            if (tryVisit(BILLING_URL)) return true
            Log.w(TAG, "reAuthenticate: all methods failed")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "reAuthenticate failed", e)
            return false
        }
    }
}
