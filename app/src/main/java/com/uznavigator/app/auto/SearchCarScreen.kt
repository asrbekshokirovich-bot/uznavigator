package com.uznavigator.app.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.uznavigator.app.NavigationActivity
import com.uznavigator.app.data.GeocodingResult
import com.uznavigator.app.data.GeocodingService
import kotlinx.coroutines.*

class SearchCarScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var results: List<GeocodingResult> = emptyList()
    private var isLoading = false
    private var lastQuery = ""

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        when {
            isLoading -> listBuilder.setNoItemsMessage("Searching…")
            results.isEmpty() && lastQuery.isNotBlank() ->
                listBuilder.setNoItemsMessage("No results for \"$lastQuery\"")
            else -> results.forEach { result ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(result.name)
                        .addText(result.address)
                        .setOnClickListener { startNavigationTo(result) }
                        .build()
                )
            }
        }

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                if (searchText == lastQuery) return
                lastQuery = searchText
                if (searchText.length >= 2) triggerSearch(searchText)
            }
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.length >= 2) triggerSearch(searchText, immediate = true)
            }
        })
            .setHeaderAction(Action.APP_ICON)
            .setShowKeyboardByDefault(false)
            .setItemList(listBuilder.build())
            .build()
    }

    private fun triggerSearch(query: String, immediate: Boolean = false) {
        scope.coroutineContext.cancelChildren()
        scope.launch {
            if (!immediate) delay(600)
            isLoading = true
            invalidate()
            results = GeocodingService.search(query)
            isLoading = false
            invalidate()
        }
    }

    private fun startNavigationTo(destination: GeocodingResult) {
        screenManager.push(NavigationCarScreen(carContext))
        carContext.startActivity(
            Intent(carContext, NavigationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(NavigationActivity.EXTRA_DEST_LAT, destination.latitude)
                putExtra(NavigationActivity.EXTRA_DEST_LNG, destination.longitude)
                putExtra(NavigationActivity.EXTRA_DEST_NAME, destination.name)
            }
        )
    }
}
