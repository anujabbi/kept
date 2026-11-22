package com.example.kept

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * ViewModels for a test that builds them by hand, held the way an activity holds them so the
 * test's `@After` can clear them. Clearing cancels each `viewModelScope`; without it a
 * `stateIn(viewModelScope, WhileSubscribed(5_000))` goes on reading the test's database for five
 * seconds after the test has closed it, and the failure surfaces in whichever test runs next.
 */
class TestViewModels {
    private val store = ViewModelStore()
    private var made = 0

    fun <T : ViewModel> create(type: Class<T>, make: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
        }
        return ViewModelProvider(store, factory)["${type.name}#${made++}", type]
    }

    inline fun <reified T : ViewModel> create(noinline make: () -> T): T = create(T::class.java, make)

    fun clear() = store.clear()
}
