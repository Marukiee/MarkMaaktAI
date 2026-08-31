package nl.markmaaktmedia.markmaaktai.ai.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pulls the text out of a photo or a screenshot with the bundled ML Kit recogniser.
 *
 * Bundled on purpose: the Play services flavour of ML Kit downloads its model
 * through Google Play, which is exactly what is missing on GrapheneOS and on a
 * de-Googled ROM. This variant carries the model in the APK and works offline.
 *
 * Two jobs. It is the fallback when no vision model is loaded, and it is also used
 * next to a vision model, because feeding the recognised text along with the image
 * gets small models to read interfaces far more reliably than the pixels alone.
 */
@Singleton
class ImageTextExtractor @Inject constructor() {

    private val recogniser by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Returns the recognised text, or an empty string when there is none. */
    suspend fun extract(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        suspendCancellableCoroutine { continuation ->
            recogniser.process(image)
                .addOnSuccessListener { result ->
                    continuation.resume(result.text.trim())
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    /**
     * Same as [extract] but keeps the block layout, which reads far better for a
     * screenshot where position carries meaning (a header, a list, a button row).
     */
    suspend fun extractStructured(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        suspendCancellableCoroutine { continuation ->
            recogniser.process(image)
                .addOnSuccessListener { result ->
                    val text = result.textBlocks.joinToString("\n") { block ->
                        block.lines.joinToString("\n") { it.text }
                    }.trim()
                    continuation.resume(text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }
}
