package io.github.notoboto.unison.sources

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * ed25519 signature of a repository index (SOURCES_DESIGN.md §6, §19.3): `index.json.sig` is the base64 signature of
 * the raw bytes of `index.json`. The app accepts plugins only from an index signed by one of these keys — any
 * repository URL can be added, but foreign repositories do not work (decision of §6).
 *
 * BouncyCastle's lightweight API: Android has Ed25519 only from API 33, the app starts at 26.
 */
object IndexSignature {
    /** Current and spare public keys (raw 32 bytes, base64). Private halves: GitHub secret + offline copy. */
    val PUBLIC_KEYS = listOf(
        "HYPpOUDjMyCJjd2rLpc4BXsrjlisLNIg8hnHLR7wnOc=",   // current
        "M+HRrE4huKpomrk9cvI292wfvqZe0q9XarsKP8rUPxA=",   // spare
    )

    fun verify(index: ByteArray, signatureBase64: String, keys: List<String> = PUBLIC_KEYS): Boolean {
        val sig = runCatching { Base64.decode(signatureBase64.trim(), Base64.DEFAULT) }.getOrNull() ?: return false
        if (sig.size != 64) return false
        return keys.any { k ->
            runCatching {
                val signer = Ed25519Signer()
                signer.init(false, Ed25519PublicKeyParameters(Base64.decode(k, Base64.DEFAULT), 0))
                signer.update(index, 0, index.size)
                signer.verifySignature(sig)
            }.getOrDefault(false)
        }
    }
}
