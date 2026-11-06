package com.spendtracker.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.domain.TransactionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewUiState(
    /** Transactions flagged needsReview, newest first. */
    val items: List<TransactionItem> = emptyList(),
    val count: Int = 0,
    /** How many transactions were marked reviewed since this screen's ViewModel was created. */
    val reviewedThisSession: Int = 0,
    /** Id of the transaction most recently marked reviewed (undo slot); null when nothing to undo. */
    val lastReviewedId: Long? = null
)

private data class ReviewSession(val reviewed: Int = 0, val lastId: Long? = null)

class ReviewViewModel(private val repository: SpendRepository) : ViewModel() {

    private val session = MutableStateFlow(ReviewSession())

    val uiState: StateFlow<ReviewUiState> = combine(
        repository.observeTransactionItems().map { all ->
            all.filter { it.needsReview }.sortedByDescending { it.timestamp }
        },
        session
    ) { items, s ->
        ReviewUiState(
            items = items,
            count = items.size,
            reviewedThisSession = s.reviewed,
            lastReviewedId = s.lastId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun markReviewed(tx: TransactionItem) {
        session.value = ReviewSession(session.value.reviewed + 1, tx.id)
        viewModelScope.launch { repository.setNeedsReview(tx.id, false) }
    }

    fun undoLast() {
        val s = session.value
        val id = s.lastId ?: return
        session.value = ReviewSession((s.reviewed - 1).coerceAtLeast(0), null)
        viewModelScope.launch { repository.setNeedsReview(id, true) }
    }

    fun markAllReviewed() {
        val n = uiState.value.count
        session.value = ReviewSession(session.value.reviewed + n, null)
        viewModelScope.launch { repository.markAllReviewed() }
    }
}
