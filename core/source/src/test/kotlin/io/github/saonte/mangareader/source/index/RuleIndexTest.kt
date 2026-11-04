package io.github.saonte.mangareader.source.index

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSAGenParameterSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64

class RuleIndexTest {

    private val json = Json

    private fun keyPair(): Pair<EdDSAPrivateKey, ByteArray> {
        val generator = KeyPairGenerator()
        generator.initialize(EdDSAGenParameterSpec("Ed25519"), java.security.SecureRandom())
        val pair = generator.generateKeyPair()
        return (pair.private as EdDSAPrivateKey) to (pair.public as EdDSAPublicKey).abyte
    }

    private fun sign(privateKey: EdDSAPrivateKey, payload: ByteArray): ByteArray {
        val spec = EdDSANamedCurveTable.ED_25519_CURVE_SPEC
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(privateKey)
        engine.update(payload)
        return engine.sign()
    }

    private fun unsignedIndex(name: String = "Test Index"): JsonObject = buildJsonObject {
        put("formatVersion", 1)
        put("generatedAt", 1_767_225_600_000L)
        put("name", name)
        put(
            "rules",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("id", "community.example")
                        put("name", "示例源")
                        put("version", "1.0.0")
                        put("url", "https://rules.example.org/community.example.json")
                        put("sha256", "a".repeat(64))
                    },
                )
            },
        )
    }

    private fun signedIndex(
        privateKey: EdDSAPrivateKey,
        keyId: String = "test-key",
        name: String = "Test Index",
    ): String {
        val base = unsignedIndex(name)
        val signature = Base64.getEncoder().encodeToString(sign(privateKey, IndexSigner.signingPayload(base)))
        val root = buildJsonObject {
            base.forEach { (k, v) -> put(k, v) }
            put(
                "signature",
                buildJsonObject {
                    put("algorithm", "ed25519")
                    put("keyId", keyId)
                    put("value", signature)
                },
            )
        }
        return root.toString()
    }

    @Test
    fun `canonical json sorts keys and escapes`() {
        val element = json.parseToJsonElement(
            """{"b":1,"a":{"d":"x","c":[true,null,"q\"w"]},"n":1.0}""",
        )
        assertThat(JsonCanonicalizer.canonicalize(element))
            .isEqualTo("""{"a":{"c":[true,null,"q\"w"],"d":"x"},"b":1,"n":1}""")
    }

    @Test
    fun `signed index is accepted`() {
        val (privateKey, publicKey) = keyPair()
        val verifier = RuleIndexVerifier(mapOf("test-key" to publicKey))

        val result = verifier.parseAndVerify(signedIndex(privateKey))

        assertThat(result).isInstanceOf(IndexResult.Verified::class.java)
        assertThat((result as IndexResult.Verified).entries.first().id).isEqualTo("community.example")
    }

    @Test
    fun `tampered index is rejected`() {
        val (privateKey, publicKey) = keyPair()
        val verifier = RuleIndexVerifier(mapOf("test-key" to publicKey))
        val tampered = signedIndex(privateKey).replace("Test Index", "Evil Index")

        val result = verifier.parseAndVerify(tampered)

        assertThat(result).isInstanceOf(IndexResult.Rejected::class.java)
        assertThat((result as IndexResult.Rejected).reason).contains("签名校验失败")
    }

    @Test
    fun `unknown key id is rejected`() {
        val (privateKey, _) = keyPair()
        val verifier = RuleIndexVerifier(mapOf("other-key" to ByteArray(32)))

        val result = verifier.parseAndVerify(signedIndex(privateKey))

        assertThat(result).isInstanceOf(IndexResult.Rejected::class.java)
        assertThat((result as IndexResult.Rejected).reason).contains("未知签名 keyId")
    }

    @Test
    fun `missing signature is rejected`() {
        val verifier = RuleIndexVerifier(emptyMap())

        val result = verifier.parseAndVerify(unsignedIndex().toString())

        assertThat(result).isInstanceOf(IndexResult.Rejected::class.java)
        assertThat((result as IndexResult.Rejected).reason).contains("缺少 signature")
    }

    @Test
    fun `content sha256 verification`() {
        val bytes = """{"schemaVersion":1}""".toByteArray()
        val digest = RuleContentVerifier.sha256(bytes)

        assertThat(RuleContentVerifier.verify(bytes, digest)).isTrue()
        assertThat(RuleContentVerifier.verify(bytes, digest.uppercase())).isTrue()
        assertThat(RuleContentVerifier.verify(bytes + byteArrayOf(' '.code.toByte()), digest)).isFalse()
    }
}
