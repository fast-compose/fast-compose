package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import ma7moud3ly.fast.compose.interpreter.Registry
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

/** `Dispatchers.Default`, `Main`, `IO` and `Unconfined`. */
private object CoroutineDispatchers

/** `SharingStarted.Eagerly`, `Lazily` and `WhileSubscribed(…)`. */
private object SharingStarts

/**
 * Paging's `PagingData` in previews: [items] shown as loaded. The Paging library isn't part of the engine, so
 * `PagingData.from(list)`, `collectAsLazyPagingItems()` and `LazyPagingItems` are imitated with plain lists.
 */
internal class PreviewPagingData(val items: List<Any?>)

/** `LazyPagingItems` of [PreviewPagingData]: every item loaded, no loading or errors. */
internal class PreviewPagingItems(val items: List<Any?>)

/** `LoadState.NotLoading`, the only load state of preview paging data. */
internal object PreviewNotLoading

/** `loadState` of [PreviewPagingItems]: `refresh`, `append`, `prepend` and the `source` / `mediator` states. */
internal object PreviewLoadStates

/** `PagingData` and `LoadState`. */
private object PagingDataCompanion
private object LoadStates

/**
 * Flow builders and operators, coroutine scopes and dispatchers, and paging data built in previews from lists.
 * Dispatchers don't move work in previews: `withContext` runs its block where it is called.
 */
@OptIn(FlowPreview::class)
@Suppress("UNCHECKED_CAST")
internal fun Registry.flowExtras() {
    function("flow(block)") { a ->
        val block = a.suspendBlock<FlowCollector<Any?>>("block")
        flow<Any?> { block() }
    }
    suspendMember<FlowCollector<*>>("emit(value)") { collector, a -> (collector as FlowCollector<Any?>).emit(a["value"]) }
    function("flowOf(vararg elements)") { a -> flowOf(*a.varargs("elements").toTypedArray()) }
    function("emptyFlow()") { _ -> emptyFlow<Any?>() }

    type<SharedFlow<*>>("SharedFlow")
    type<MutableSharedFlow<*>>("MutableSharedFlow")
    function("MutableSharedFlow(replay, extraBufferCapacity)") { a ->
        MutableSharedFlow<Any?>(a.int("replay", 0), a.int("extraBufferCapacity", 0))
    }
    member<MutableSharedFlow<*>>("asSharedFlow()") { flow, _ -> flow.asSharedFlow() }
    member<MutableSharedFlow<*>>("tryEmit(value)") { flow, a -> (flow as MutableSharedFlow<Any?>).tryEmit(a["value"]) }
    suspendMember<MutableSharedFlow<*>>("emit(value)") { flow, a -> (flow as MutableSharedFlow<Any?>).emit(a["value"]) }

    member<Flow<*>>("map(transform)") { flow, a ->
        val transform = a.suspendFunction1("transform")
        flow.map { transform(it) }
    }
    member<Flow<*>>("filter(predicate)") { flow, a ->
        val predicate = a.suspendFunction1("predicate")
        flow.filter { predicate(it) == true }
    }
    member<Flow<*>>("onEach(action)") { flow, a ->
        val action = a.suspendAction1("action")
        flow.onEach { action(it) }
    }
    member<Flow<*>>("onStart(action)") { flow, a ->
        val action = a.suspendBlock<FlowCollector<Any?>>("action")
        flow.onStart { action() }
    }
    member<Flow<*>>("catch(action)") { flow, a ->
        val action = a.suspendAction1("action")
        flow.catch { action(it) }
    }
    member<Flow<*>>("distinctUntilChanged()") { flow, _ -> flow.distinctUntilChanged() }
    member<Flow<*>>("filterNotNull()") { flow, _ -> flow.filterNotNull() }
    member<Flow<*>>("take(count)") { flow, a -> flow.take(a.int("count", 1)) }
    member<Flow<*>>("drop(count)") { flow, a -> flow.drop(a.int("count", 0)) }
    member<Flow<*>>("debounce(timeoutMillis)") { flow, a ->
        when (val timeout = a["timeoutMillis"]) {
            is Duration -> flow.debounce(timeout)
            else -> flow.debounce(a.long("timeoutMillis", 0L))
        }
    }
    member<Flow<*>>("combine(flow, transform)") { flow, a ->
        val other = a.typedOrNull<Flow<*>>("flow") ?: flowOf(null)
        val transform = a.function2("transform")
        combine(flow, other) { first, second -> transform(first, second) }
    }
    member<Flow<*>>("stateIn(scope, started, initialValue)") { flow, a ->
        val scope = a.typedOrNull<CoroutineScope>("scope")
        if (scope == null) {
            a.warn("stateIn needs a CoroutineScope; the flow keeps its initial value")
            kotlinx.coroutines.flow.MutableStateFlow(a["initialValue"])
        } else {
            flow.stateIn(scope, a.typed("started", SharingStarted.Eagerly), a["initialValue"])
        }
    }
    member<Flow<*>>("shareIn(scope, started, replay)") { flow, a ->
        val scope = a.typedOrNull<CoroutineScope>("scope")
        if (scope == null) {
            a.warn("shareIn needs a CoroutineScope; the flow emits nothing")
            MutableSharedFlow<Any?>()
        } else {
            flow.shareIn(scope, a.typed("started", SharingStarted.Eagerly), a.int("replay", 0))
        }
    }
    suspendMember<Flow<*>>("collectLatest(action)") { flow, a ->
        val action = a.suspendAction1("action")
        flow.collectLatest { action(it) }
    }
    suspendMember<Flow<*>>("first()") { flow, _ -> flow.first() }
    suspendMember<Flow<*>>("firstOrNull()") { flow, _ -> flow.firstOrNull() }
    suspendMember<Flow<*>>("toList()") { flow, _ -> flow.toList() }

    value("SharingStarted", SharingStarts)
    type<SharingStarted>("SharingStarted")
    constants<SharingStarts>("Eagerly" to SharingStarted.Eagerly, "Lazily" to SharingStarted.Lazily)
    member<SharingStarts>("WhileSubscribed(stopTimeoutMillis, replayExpirationMillis)") { _, a ->
        SharingStarted.WhileSubscribed(a.long("stopTimeoutMillis", 0L), a.long("replayExpirationMillis", Long.MAX_VALUE))
    }

    value("Dispatchers", CoroutineDispatchers)
    // Previews don't move work between threads; IO isn't available on every platform, so it is the default dispatcher.
    constants<CoroutineDispatchers>(
        "Default" to Dispatchers.Default,
        "Main" to Dispatchers.Main,
        "IO" to Dispatchers.Default,
        "Unconfined" to Dispatchers.Unconfined,
    )
    suspendFunction("withContext(context, block)") { a -> coroutineScope { a.suspendCalculation<CoroutineScope>("block").invoke(this) } }
    suspendFunction("coroutineScope(block)") { a -> coroutineScope { a.suspendCalculation<CoroutineScope>("block").invoke(this) } }
    suspendFunction("supervisorScope(block)") { a -> supervisorScope { a.suspendCalculation<CoroutineScope>("block").invoke(this) } }
    jobs()

    paging()
}

/**
 * Jobs, scopes and contexts: `Job()`, `SupervisorJob()`, `CoroutineScope(context)`, `context + context`,
 * `NonCancellable`, `scope.async { }` with `await()`, and `suspendCancellableCoroutine` with its continuation's
 * `resume`, `resumeWithException`, `invokeOnCancellation` and `cancel`.
 */
@Suppress("UNCHECKED_CAST")
private fun Registry.jobs() {
    type<CoroutineContext>("CoroutineContext")
    member<CoroutineContext>("plus(context)") { context, a -> context + a.typed<CoroutineContext>("context", EmptyCoroutineContext) }
    function("Job(parent)") { a -> Job(a.typedOrNull<Job>("parent")) }
    function("SupervisorJob(parent)") { a -> SupervisorJob(a.typedOrNull<Job>("parent")) }
    function("CoroutineScope(context)") { a -> CoroutineScope(a.typed<CoroutineContext>("context", EmptyCoroutineContext)) }
    value("NonCancellable", NonCancellable)

    type<Deferred<*>>("Deferred")
    member<CoroutineScope>("async(block)") { scope, a ->
        val block = a.suspendCalculation<CoroutineScope>("block")
        scope.async { block() }
    }
    suspendMember<Deferred<*>>("await()") { deferred, _ -> deferred.await() }

    type<CancellableContinuation<*>>("CancellableContinuation")
    suspendFunction("suspendCancellableCoroutine(block)") { a ->
        val block = a.action1("block")
        suspendCancellableCoroutine<Any?> { continuation -> block(continuation) }
    }
    member<CancellableContinuation<*>>("resume(value)") { c, a -> (c as CancellableContinuation<Any?>).resume(a["value"]) }
    member<CancellableContinuation<*>>("resumeWithException(exception)") { c, a ->
        c.resumeWithException(a.typed<Throwable>("exception", IllegalStateException()))
    }
    member<CancellableContinuation<*>>("invokeOnCancellation(handler)") { c, a ->
        val handler = a.action1("handler")
        c.invokeOnCancellation { handler(it) }
    }
    member<CancellableContinuation<*>>("cancel(cause)") { c, a -> c.cancel(a.typedOrNull<Throwable>("cause")) }
    properties<CancellableContinuation<*>>("isActive" to { it.isActive }, "isCancelled" to { it.isCancelled })
}

private fun Registry.paging() {
    value("PagingData", PagingDataCompanion)
    member<PagingDataCompanion>("from(data)") { _, a -> PreviewPagingData(a.typed<List<*>>("data", emptyList<Any?>())) }
    member<PagingDataCompanion>("empty()") { _, _ -> PreviewPagingData(emptyList()) }
    composableMember<Flow<*>>("collectAsLazyPagingItems()") { flow, _ ->
        val data = flow.collectAsState(null).value as? PreviewPagingData
        PreviewPagingItems(data?.items.orEmpty())
    }
    properties<PreviewPagingItems>(
        "itemCount" to { it.items.size },
        "itemSnapshotList" to { it.items },
        "loadState" to { PreviewLoadStates },
    )
    member<PreviewPagingItems>("get(index)") { paging, a -> paging.items.getOrNull(a.int("index", 0)) }
    member<PreviewPagingItems>("peek(index)") { paging, a -> paging.items.getOrNull(a.int("index", 0)) }
    member<PreviewPagingItems>("refresh()") { _, _ -> Unit }
    member<PreviewPagingItems>("retry()") { _, _ -> Unit }
    member<PreviewPagingItems>("itemKey(key)") { paging, a ->
        val key = a.function1("key")
        val hasKey = a.has("key")
        val keyOf: (Int) -> Any = { index -> paging.items.getOrNull(index)?.takeIf { hasKey }?.let { key(it) } ?: index }
        keyOf
    }
    member<PreviewPagingItems>("itemContentType(contentType)") { paging, a ->
        val contentType = a.function1("contentType")
        val hasType = a.has("contentType")
        val typeOf: (Int) -> Any? = { index -> paging.items.getOrNull(index)?.takeIf { hasType }?.let { contentType(it) } }
        typeOf
    }
    constants<PreviewLoadStates>(
        "refresh" to PreviewNotLoading,
        "append" to PreviewNotLoading,
        "prepend" to PreviewNotLoading,
        "source" to PreviewLoadStates,
        "mediator" to PreviewLoadStates,
    )
    value("LoadState", LoadStates)
    constants<LoadStates>("NotLoading" to PreviewNotLoading)
    type<PreviewNotLoading>("NotLoading")
    addType("Loading") { false }
    properties<PreviewNotLoading>("endOfPaginationReached" to { true })
}
