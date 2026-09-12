package nl.dejongduke.service.data

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Build-time helper, not part of the app a service engineer uses.
 *
 * The balloon numbers on the exploded drawings are pixels, not text, so their
 * positions have to be read once. Rather than putting an OCR stack on the
 * development machine, this reuses the recogniser the app already carries:
 * push the drawings at full resolution, run this once, keep the JSON.
 *
 *   adb push txt/hires/. /sdcard/Android/data/nl.dejongduke.service.debug/files/ocr-in/
 *   (tik "Tekeningen indexeren" in Instellingen)
 *   adb pull /sdcard/Android/data/nl.dejongduke.service.debug/files/hotspots.json
 */
object DrawingIndexer {

    /** A number found on a drawing, in fractions of the image. */
    data class Spot(val label: String, val x: Float, val y: Float, val r: Float)

    suspend fun run(context: Context, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            // The folder has to be created by the app itself: one made over adb
            // belongs to the shell user and the app is then denied access.
            val input = File(context.getExternalFilesDir(null), "ocr-in")
            input.mkdirs()
            val files = input.listFiles { f -> f.extension.lowercase() == "png" }?.sorted()
            if (files.isNullOrEmpty()) {
                return@withContext "Geen PNG's in ${input.absolutePath}"
            }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val all = JSONObject()
            files.forEachIndexed { index, file ->
                onProgress("${index + 1}/${files.size}  ${file.name}")
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                val spots = read(recognizer, bitmap)
                bitmap.recycle()
                val array = JSONArray()
                spots.forEach { s ->
                    array.put(JSONObject().apply {
                        put("n", s.label)
                        put("x", String.format("%.4f", s.x).toDouble())
                        put("y", String.format("%.4f", s.y).toDouble())
                        put("r", String.format("%.4f", s.r).toDouble())
                    })
                }
                all.put(file.nameWithoutExtension, array)
            }
            recognizer.close()
            val out = File(context.getExternalFilesDir(null), "hotspots.json")
            out.writeText(all.toString())
            "Klaar: ${files.size} tekeningen -> ${out.absolutePath}"
        }

    private suspend fun read(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: android.graphics.Bitmap,
    ): List<Spot> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                val spots = mutableListOf<Spot>()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val text = element.text.trim()
                            // balloons hold a position: digits, sometimes with a
                            // letter suffix like 10a
                            if (!text.matches(Regex("\\d{1,2}[a-cA-C]?"))) continue
                            val box = element.boundingBox ?: continue
                            spots += Spot(
                                label = text.lowercase(),
                                x = (box.exactCenterX()) / w,
                                y = (box.exactCenterY()) / h,
                                r = (maxOf(box.width(), box.height()) / 2f) / w,
                            )
                        }
                    }
                }
                cont.resume(spots)
            }
            .addOnFailureListener { cont.resume(emptyList()) }
    }
}
