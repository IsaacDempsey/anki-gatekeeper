package eu.isaacdempsey.ankigatekeeper

sealed class DeckPickerState {
    object Loading : DeckPickerState()
    object PermissionNeeded : DeckPickerState()
    data class Ready(val decks: List<DeckInfo>) : DeckPickerState()
}

sealed class CardScreenState {
    object Loading : CardScreenState()
    object AnkiNotInstalled : CardScreenState()
    object PermissionDenied : CardScreenState()
    object Error : CardScreenState()
    data class ShowingFront(val card: CardInfo, val startTime: Long) : CardScreenState()
    data class ShowingBack(val card: CardInfo, val startTime: Long) : CardScreenState()
}
