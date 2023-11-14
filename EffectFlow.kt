package com.plamfy.platform.presentation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Hot flow that can have only one subscriber and provide ONE SHORT events for him
 * with Replay Strategy. Needed for cases when you have a flow in ViewModel and you don't won't to
 * miss some events while your UI is not in appropriate state.
 * Events will be removed from replay cache when we have subscribe and unsubscribe events.
 */
interface EffectFlow<T> : SharedFlow<T>
internal open class EffectFlowImpl<T>(
    private val sharedFlow: SharedFlow<T>
) :
    SharedFlow<T> by sharedFlow, EffectFlow<T>

interface MutableEffectFlow<T> : MutableSharedFlow<T>, EffectFlow<T>
internal open class MutableEffectFlowImpl<T>(
    internal val mutableSharedFlow: MutableSharedFlow<T>
) :
    MutableSharedFlow<T> by mutableSharedFlow, EffectFlow<T>, MutableEffectFlow<T>, SharedFlow<T>

fun <T> MutableEffectFlow<T>.asEffectFlow(): EffectFlow<T> {
    return EffectFlowImpl((this as MutableEffectFlowImpl).mutableSharedFlow.asSharedFlow())
}

fun <T> ViewModel.MutableEffectFlow(
    replay: Int = 1
): MutableEffectFlow<T> = MutableEffectFlow(replay = replay, scope = viewModelScope)

@SuppressLint("ComposableNaming")
@Composable
fun <T> MutableEffectFlow(
    replay: Int = 1
): MutableEffectFlow<T> = MutableEffectFlow(replay = replay, scope = rememberCoroutineScope())

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> MutableEffectFlow(
    replay: Int = 1,
    scope: CoroutineScope,
): MutableEffectFlow<T> {
    return if (replay > 0) {
        MutableEffectFlowImpl(MutableSharedFlow<T>(replay = replay).apply {
            subscriptionCount
                .map { count ->
                    count > 0
                }
                .distinctUntilChanged()
                .onEach { _ ->
                    //remove replay cache every time when you have subscribe and unsubscribe
                    resetReplayCache()
                }
                .launchIn(scope)
        })
    } else {
        throw IllegalArgumentException("replay should be more than 0")
    }
}