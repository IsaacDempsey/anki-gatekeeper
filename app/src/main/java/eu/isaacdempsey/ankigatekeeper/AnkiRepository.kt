package eu.isaacdempsey.ankigatekeeper

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.ichi2.anki.FlashCardsContract

data class CardInfo(
    val noteId: Long,
    val cardOrd: Int,
    val question: String,
    val answer: String,
    val css: String,
)

data class DeckInfo(val id: Long, val name: String)

sealed class CardFetchResult {
    data class Success(val card: CardInfo) : CardFetchResult()
    object NoDue : CardFetchResult()        // empty cursor — genuinely no cards due
    object Error : CardFetchResult()        // null cursor or exception — provider unreachable
    object NotInstalled : CardFetchResult()
    object PermissionDenied : CardFetchResult()
}

object AnkiRepository {

    private val DECKS_URI = Uri.parse("content://com.ichi2.anki.flashcards/decks")
    const val ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private const val ANKI_PACKAGE = "com.ichi2.anki"
    private const val TAG = "AnkiGatekeeper"

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context): Boolean {
        val installed = try {
            context.packageManager.getPackageInfo(ANKI_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        Log.d(TAG, "isInstalled=$installed")
        return installed
    }

    fun hasPermission(context: Context): Boolean {
        val granted = context.checkSelfPermission(ANKI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "hasPermission=$granted")
        return granted
    }

    // Must be called on a background thread.
    fun fetchDecks(context: Context): List<DeckInfo> {
        Log.d(TAG, "fetchDecks: start")
        if (!hasPermission(context)) return emptyList()
        return try {
            val cursor = context.contentResolver.query(DECKS_URI, null, null, null, null)
            Log.d(TAG, "fetchDecks: cursor count=${cursor?.count}")
            cursor?.use {
                buildList {
                    while (it.moveToNext()) {
                        val id   = it.getLong(it.getColumnIndexOrThrow("deck_id"))
                        val name = it.getString(it.getColumnIndexOrThrow("deck_name")) ?: continue
                        Log.d(TAG, "fetchDecks: deck id=$id name=$name")
                        add(DeckInfo(id, name))
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "fetchDecks: exception", e)
            emptyList()
        }
    }

    // Must be called on a background thread.
    // Pass deckId = 0L to fetch from all decks (no filter).
    // Step 1: if a specific deck is selected, permanently select it via DECK_SELECTED so the
    // reviewInfo queue is scoped to that deck. Querying with deckID=? would only temporarily
    // select the deck and restore the original afterward — leaving the wrong deck selected when
    // answerCard later calls getQueuedCards, causing FSRS state mismatches ("card was modified").
    // Step 2: query reviewInfo (limit=1, no deckID) to get the top card from the selected deck.
    // This also registers the card in AnkiDroid's internal review queue, which submitAnswer needs.
    // Step 3: query notes/{noteId}/cards/{cardOrd} to get the rendered question/answer.
    fun fetchDueCard(context: Context, deckId: Long = 0L): CardFetchResult {
        Log.d(TAG, "fetchDueCard: deckId=$deckId")
        if (!isInstalled(context)) return CardFetchResult.NotInstalled
        if (!hasPermission(context)) return CardFetchResult.PermissionDenied

        return try {
            if (deckId != 0L) {
                val deckValues = ContentValues().apply {
                    put(FlashCardsContract.Deck.DECK_ID, deckId)
                }
                context.contentResolver.update(FlashCardsContract.Deck.CONTENT_SELECTED_URI, deckValues, null, null)
                Log.d(TAG, "fetchDueCard: selected deck $deckId")
            }

            val reviewCursor = context.contentResolver.query(
                FlashCardsContract.ReviewInfo.CONTENT_URI, null, "limit=?", arrayOf("1"), null
            )
            Log.d(TAG, "fetchDueCard: reviewCursor count=${reviewCursor?.count}")
            if (reviewCursor == null) {
                Log.w(TAG, "fetchDueCard: null reviewCursor")
                return CardFetchResult.Error
            }
            reviewCursor.use { rc ->
                if (!rc.moveToFirst()) {
                    Log.d(TAG, "fetchDueCard: empty → NoDue")
                    return CardFetchResult.NoDue
                }
                val noteId  = rc.getLong(rc.getColumnIndexOrThrow(FlashCardsContract.ReviewInfo.NOTE_ID))
                val cardOrd = rc.getInt(rc.getColumnIndexOrThrow(FlashCardsContract.ReviewInfo.CARD_ORD))
                Log.d(TAG, "fetchDueCard: noteId=$noteId cardOrd=$cardOrd")

                val cardUri = Uri.withAppendedPath(
                    Uri.withAppendedPath(
                        Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId.toString()),
                        "cards"
                    ),
                    cardOrd.toString()
                )
                val cardCursor = context.contentResolver.query(cardUri, null, null, null, null)
                Log.d(TAG, "fetchDueCard: cardCursor count=${cardCursor?.count}")
                if (cardCursor == null) {
                    Log.w(TAG, "fetchDueCard: null cardCursor")
                    return CardFetchResult.Error
                }
                cardCursor.use { cc ->
                    if (!cc.moveToFirst()) {
                        Log.w(TAG, "fetchDueCard: cardCursor empty")
                        return CardFetchResult.Error
                    }
                    val question = cc.getString(cc.getColumnIndexOrThrow("question")) ?: ""
                    val answer   = cc.getString(cc.getColumnIndexOrThrow("answer")) ?: ""
                    Log.d(TAG, "fetchDueCard: question=${question.take(80)}")
                    val css = fetchModelCss(context, noteId)
                    CardFetchResult.Success(CardInfo(noteId, cardOrd, question, answer, css))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchDueCard: exception", e)
            CardFetchResult.Error
        }
    }

    // Fetch the CSS for the note type so the card renders with its custom styling.
    // notes/{noteId} → MID → models/{MID} → css
    private fun fetchModelCss(context: Context, noteId: Long): String {
        return try {
            val noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId.toString())
            val mid = context.contentResolver.query(
                noteUri, arrayOf(FlashCardsContract.Note.MID), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(FlashCardsContract.Note.MID)) else null
            }
            if (mid == null) return ""
            val modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, mid.toString())
            context.contentResolver.query(
                modelUri, arrayOf(FlashCardsContract.Model.CSS), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(FlashCardsContract.Model.CSS)) ?: "" else ""
            } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "fetchModelCss: failed", e)
            ""
        }
    }

    fun submitAnswer(context: Context, noteId: Long, cardOrd: Int, ease: Int, timeTakenMs: Long) {
        Log.d(TAG, "submitAnswer: noteId=$noteId cardOrd=$cardOrd ease=$ease time=${timeTakenMs}ms")
        val values = ContentValues().apply {
            put(FlashCardsContract.ReviewInfo.NOTE_ID, noteId)
            put(FlashCardsContract.ReviewInfo.CARD_ORD, cardOrd)
            put(FlashCardsContract.ReviewInfo.EASE, ease)
            put(FlashCardsContract.ReviewInfo.TIME_TAKEN, timeTakenMs)
        }
        try {
            val rows = context.contentResolver.update(FlashCardsContract.ReviewInfo.CONTENT_URI, values, null, null)
            Log.d(TAG, "submitAnswer: updated $rows row(s)")
        } catch (e: Exception) {
            Log.e(TAG, "submitAnswer: exception", e)
        }
    }

}
