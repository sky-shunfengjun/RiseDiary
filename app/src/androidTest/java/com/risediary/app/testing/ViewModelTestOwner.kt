package com.risediary.app.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/** Use the supported owner API so store.clear() exercises real lifecycle cancellation. */
internal fun <T : ViewModel> ViewModelStore.retainForTest(key: String, model: T): T =
    ViewModelProvider(this, object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = requireNotNull(modelClass.cast(model))
    })[key, model.javaClass]