package com.example.appcolegios.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class GroupItem(val id: String, val nombre: String)

class AssignGroupViewModel(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : ViewModel() {
    private val _grupos = MutableStateFlow<List<GroupItem>>(emptyList())
    val grupos: StateFlow<List<GroupItem>> = _grupos

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        loadGrupos()
    }

    fun loadGrupos() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snaps = db.collection("grupos").get().await()
                val list = snaps.documents.map { d ->
                    val id = d.id
                    val nombre = d.getString("nombre") ?: d.getString("nombreGrupo") ?: id
                    GroupItem(id, nombre)
                }
                _grupos.value = list
            } catch (e: Exception) {
                _grupos.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}

