package com.example.appcolegios.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BadgesState(
    val unreadNotifications: Int = 0,
    val unreadMessages: Int = 0
)

class BadgesViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val _state = MutableStateFlow(BadgesState())
    val state: StateFlow<BadgesState> = _state

    private var notificationsListener: ListenerRegistration? = null
    private var inboxListener: ListenerRegistration? = null

    init {
        observeNotificationCount()
        observeInboxUnreadCount()
    }

    private fun observeNotificationCount() {
        val uid = auth.currentUser?.uid ?: return
        notificationsListener?.remove()
        notificationsListener = db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("leida", false)
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                viewModelScope.launch {
                    _state.value = _state.value.copy(unreadNotifications = count)
                }
            }
    }

    private fun observeInboxUnreadCount() {
        val uid = auth.currentUser?.uid ?: return
        inboxListener?.remove()
        inboxListener = db.collection("users").document(uid)
            .collection("inbox")
            .addSnapshotListener { snapshot, _ ->
                val total = snapshot?.documents?.sumOf { (it.getLong("unreadCount") ?: 0L).toInt() } ?: 0
                viewModelScope.launch {
                    _state.value = _state.value.copy(unreadMessages = total)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        notificationsListener?.remove()
        inboxListener?.remove()
    }
}
