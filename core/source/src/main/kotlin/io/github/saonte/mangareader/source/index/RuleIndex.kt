package io.github.saonte.mangareader.source.index

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest
import java.util.Base64

@Serializable
data class RuleIndexEntry(
    val id: String,
    val name: String,
    val version: String,
    val url: String,
    val sha256: String,
    @SerialName("minEngine") val minEngine: Int = 1,
    val adult: Boolean = false,
    val revoked: Boolean = false,
)

@Serializable
data class RuleIndexSignature(
    val algorithm: String = "ed25519",
    @SerialName("keyId") val keyId: String,
    val value: String,
)

@Serializable
data class RuleIndex(
    val formatVersion: Int = 1,
    val generatedAt: Long = 0,
    val name: String = "",
    val rules: List<RuleIndexEntry> = emptyList(),
    val signature: RuleIndexSignature? = null,
)

sealed interface IndexResult {
    data class Verified(val index: RuleIndex, val entries: List<RuleIndexEntry>) : IndexResult
    data class Rejected(val reason: String) : IndexResult
}

/**
 * 索引验签：签名覆盖「除 signature 外的整棵树」的 JCS 规范化字节。
 * 公钥按 keyId 内置，支持轮换。
 */
class RuleIndexVerifier(
    private val publicKeys: Map<String, ByteArray>,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun parseAndVerify(raw: String): IndexResult {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { return IndexResult.Rejected("索引不是合法 JSON 对象") }

        val signatureObject = root["signature"] as? JsonObject
            ?: return IndexResult.Rejected("缺少 signature")
        val signature = runCatching {
            RuleIndexSignature(
                algorithm = signatureObject["algorithm"]?.jsonPrimitive?.content ?: "ed25519",
                keyId = signatureObject["keyId"]?.jsonPrimitive?.content ?: "",
                value = signatureObject["value"]?.jsonPrimitive?.content ?: "",
            )
        }.getOrElse { return IndexResult.Rejected("signature 字段格式错误") }

        if (signature.algorithm != "ed25519") {
            return IndexResult.Rejected("不支持的签名算法：${signature.algorithm}")
        }
        val key = publicKeys[signature.keyId]
            ?: return IndexResult.Rejected("未知签名 keyId：${signature.keyId}")

        val payload = IndexSigner.signingPayload(root)
        val signatureBytes = runCatching { Base64.getDecoder().decode(signature.value) }
            .getOrElse { return IndexResult.Rejected("签名不是合法 base64") }

        if (!Ed25519.verify(key, payload, signatureBytes)) {
            return IndexResult.Rejected("签名校验失败（可能被篡改）")
        }

        val index = runCatching { json.decodeFromJsonElement(RuleIndex.serializer(), root) }
            .getOrElse { return IndexResult.Rejected("索引字段不符合规范：${it.message}") }
        validate(index)?.let { return IndexResult.Rejected(it) }
        return IndexResult.Verified(index, index.rules)
    }

    private fun validate(index: RuleIndex): String? {
        if (index.formatVersion != 1) return "不支持的索引版本：${index.formatVersion}"
        if (index.rules.isEmpty()) return "索引为空"
        val duplicate = index.rules.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()
        if (duplicate != null) return "规则 id 重复：$duplicate"
        index.rules.forEach { rule ->
            if (!rule.id.matches(ID_PATTERN)) return "非法规则 id：${rule.id}"
            if (!rule.url.startsWith("https://")) return "规则下载地址必须为 https：${rule.url}"
            if (!rule.sha256.matches(SHA256_PATTERN)) return "非法 sha256：${rule.id}"
        }
        return null
    }

    private companion object {
        val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{2,64}$")
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}

object RuleContentVerifier {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun verify(bytes: ByteArray, expectedSha256: String): Boolean =
        sha256(bytes).equals(expectedSha256, ignoreCase = true)
}

object Ed25519 {
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        val spec = EdDSANamedCurveTable.getByName("Ed25519")
        val key = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec))
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initVerify(key)
        engine.update(message)
        engine.verify(signature)
    }.getOrDefault(false)
}

/** 对 JSON（去除 signature）做 JCS 得到签名载荷。 */
object IndexSigner {
    fun signingPayload(root: JsonObject): ByteArray = JsonCanonicalizer.canonicalize(
        buildJsonObject { root.forEach { (k, v) -> if (k != "signature") put(k, v) } },
    ).toByteArray(Charsets.UTF_8)
}
