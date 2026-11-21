package io.qzz.xandll.morgo.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Klartext, wenn „Jetzt testen“ den Stream nicht starten kann. */
@Singleton
class WakeSoundStatus @Inject constructor() {
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    fun reportFailure(message: String) {
        _failure.value = message
    }

    fun clear() {
        _failure.value = null
    }
}
