package eu.isaacdempsey.ankigatekeeper

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    // Persisted through process death via SavedStateHandle
    val hasAnsweredCard = mutableStateOf(savedStateHandle.get<Boolean>(KEY_HAS_ANSWERED) ?: false)
    val noDueMode      = mutableStateOf(savedStateHandle.get<Boolean>(KEY_NO_DUE_MODE)   ?: false)
    val showSettings   = mutableStateOf(savedStateHandle.get<Boolean>(KEY_SHOW_SETTINGS)  ?: false)

    // Transient — re-derived on recreation from SharedPreferences / re-fetched
    val deckSelected     = mutableStateOf(hasDeckSelection())
    val cardScreenState  = mutableStateOf<CardScreenState>(CardScreenState.Loading)
    val deckPickerState  = mutableStateOf<DeckPickerState>(DeckPickerState.Loading)

    // One-shot event: Activity observes this to call finish()
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private var decksJob: Job? = null
    private var fetchJob: Job? = null

    fun loadDecks() {
        decksJob?.cancel()
        deckPickerState.value = DeckPickerState.Loading
        decksJob = viewModelScope.launch(Dispatchers.IO) {
            val decks = AnkiRepository.fetchDecks(getApplication())
            withContext(Dispatchers.Main) {
                deckPickerState.value = DeckPickerState.Ready(decks)
            }
        }
    }

    fun fetchCard() {
        fetchJob?.cancel()
        cardScreenState.value = CardScreenState.Loading
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            val result = AnkiRepository.fetchDueCard(getApplication(), getSavedDeckId())
            withContext(Dispatchers.Main) {
                cardScreenState.value = when (result) {
                    is CardFetchResult.NoDue -> {
                        applyLock(false)
                        setNoDueMode(true)
                        loadDecks()
                        CardScreenState.Loading
                    }
                    is CardFetchResult.Error        -> { applyLock(false); CardScreenState.Error }
                    is CardFetchResult.NotInstalled -> { applyLock(false); CardScreenState.AnkiNotInstalled }
                    is CardFetchResult.PermissionDenied -> { applyLock(false); CardScreenState.PermissionDenied }
                    is CardFetchResult.Success -> {
                        if (!hasAnsweredCard.value) applyLock(true)
                        CardScreenState.ShowingFront(result.card, SystemClock.elapsedRealtime())
                    }
                }
            }
        }
    }

    fun onShowAnswer() {
        val s = cardScreenState.value
        if (s is CardScreenState.ShowingFront) {
            cardScreenState.value = CardScreenState.ShowingBack(s.card, s.startTime)
        }
    }

    fun onAnswer(ease: Int) {
        val s = cardScreenState.value
        if (s is CardScreenState.ShowingBack) {
            val elapsed = SystemClock.elapsedRealtime() - s.startTime
            viewModelScope.launch(Dispatchers.IO) {
                AnkiRepository.submitAnswer(getApplication(), s.card.noteId, s.card.cardOrd, ease, elapsed)
                withContext(Dispatchers.Main) { onCardAnswered() }
            }
        }
    }

    fun onCardAnswered() {
        setHasAnsweredCard(true)
        applyLock(false)
        cardScreenState.value = CardScreenState.Loading
        fetchCard()
    }

    fun endSession() {
        applyLock(false)
        _events.tryEmit(Unit)
    }

    fun startNewSession() {
        fetchJob?.cancel()
        setHasAnsweredCard(false)
        setNoDueMode(false)
        setShowSettings(false)
        cardScreenState.value = CardScreenState.Loading
    }

    fun onDeckSelected(deckId: Long) {
        saveDeckSelection(deckId)
        deckSelected.value = true
        setShowSettings(false)
        setNoDueMode(false)
        cardScreenState.value = CardScreenState.Loading
        fetchCard()
    }

    fun openSettings() {
        setShowSettings(true)
        cardScreenState.value = CardScreenState.Loading
        loadDecks()
    }

    fun closeSettings() {
        setShowSettings(false)
        if (cardScreenState.value is CardScreenState.Loading) fetchCard()
    }

    fun applyLock(locked: Boolean) {
        AnkiGatekeeperAccessibilityService.isLocked = locked
    }

    fun isMidSession(): Boolean =
        deckSelected.value && !noDueMode.value && !showSettings.value && !hasAnsweredCard.value

    fun onAnkiPermissionGranted() {
        if (!deckSelected.value || showSettings.value) loadDecks() else fetchCard()
    }

    fun onAnkiPermissionDenied() {
        onCardAnswered()
    }

    fun setAnkiPermissionNeeded() {
        deckPickerState.value = DeckPickerState.PermissionNeeded
    }

    private fun setHasAnsweredCard(value: Boolean) {
        hasAnsweredCard.value = value
        savedStateHandle[KEY_HAS_ANSWERED] = value
    }

    private fun setNoDueMode(value: Boolean) {
        noDueMode.value = value
        savedStateHandle[KEY_NO_DUE_MODE] = value
    }

    private fun setShowSettings(value: Boolean) {
        showSettings.value = value
        savedStateHandle[KEY_SHOW_SETTINGS] = value
    }

    // Matches Activity.getPreferences(MODE_PRIVATE) which uses the simple class name as file name
    private fun prefs() = getApplication<Application>()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasDeckSelection(): Boolean = prefs().contains("deck_id")

    private fun getSavedDeckId(): Long = prefs().getLong("deck_id", 0L)

    private fun saveDeckSelection(deckId: Long) {
        prefs().edit().putLong("deck_id", deckId).apply()
    }

    companion object {
        private const val KEY_HAS_ANSWERED  = "has_answered"
        private const val KEY_NO_DUE_MODE   = "no_due_mode"
        private const val KEY_SHOW_SETTINGS = "show_settings"
        private const val PREFS_NAME        = "MainActivity"
    }
}
