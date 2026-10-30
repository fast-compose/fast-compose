package ma7moud3ly.fast.compose.bindings.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ma7moud3ly.fast.compose.interpreter.ErrorBox
import ma7moud3ly.fast.compose.interpreter.Registry

/** Markers for `PageSize.Fill`/`PageSize.Fixed(…)` and `StaggeredGridCells.Fixed(…)`/`Adaptive(…)`. */
private object PageSizes
private object StaggeredGridCellsFactory

/** `HorizontalPager`, `VerticalPager` and staggered grids. */
internal fun Registry.pagers() {
    type<PagerState>("PagerState")
    composable("rememberPagerState(initialPage, initialPageOffsetFraction, pageCount)") { a ->
        val pageCount = a.calculation("pageCount")
        rememberPagerState(a.int("initialPage", 0), a.float("initialPageOffsetFraction", 0f)) {
            (pageCount() as? Number)?.toInt() ?: 0
        }
    }
    properties<PagerState>(
        "currentPage" to { it.currentPage },
        "settledPage" to { it.settledPage },
        "targetPage" to { it.targetPage },
        "pageCount" to { it.pageCount },
        "currentPageOffsetFraction" to { it.currentPageOffsetFraction },
        "isScrollInProgress" to { it.isScrollInProgress },
    )
    suspendMember<PagerState>("scrollToPage(page, pageOffsetFraction)") { state, a ->
        state.scrollToPage(a.int("page", 0), a.float("pageOffsetFraction", 0f))
    }
    suspendMember<PagerState>("animateScrollToPage(page, pageOffsetFraction)") { state, a ->
        state.animateScrollToPage(a.int("page", 0), a.float("pageOffsetFraction", 0f))
    }
    value("PageSize", PageSizes)
    member<PageSizes>("Fill") { _, _ -> PageSize.Fill }
    member<PageSizes>("Fixed(pageSize)") { _, a -> PageSize.Fixed(a.dp("pageSize", 0.dp)) }

    composable("HorizontalPager(state, modifier, contentPadding, pageSize, beyondViewportPageCount, pageSpacing, verticalAlignment, userScrollEnabled, reverseLayout, pageContent)") { a ->
        val state = a.typedOrNull<PagerState>("state")
        val content = a.scopedContent1<PagerScope>("pageContent")
        if (state == null) {
            ErrorBox("HorizontalPager needs a PagerState, e.g. rememberPagerState { 3 }")
        } else {
            HorizontalPager(
                state = state,
                modifier = a.modifier(),
                contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
                pageSize = a.typed<PageSize>("pageSize", PageSize.Fill),
                beyondViewportPageCount = a.int("beyondViewportPageCount", PagerDefaults.BeyondViewportPageCount),
                pageSpacing = a.dp("pageSpacing", 0.dp),
                verticalAlignment = a.typed<Alignment.Vertical>("verticalAlignment", Alignment.CenterVertically),
                userScrollEnabled = a.boolean("userScrollEnabled", true),
                reverseLayout = a.boolean("reverseLayout", false),
            ) { page -> content(page) }
        }
    }
    composable("VerticalPager(state, modifier, contentPadding, pageSize, beyondViewportPageCount, pageSpacing, horizontalAlignment, userScrollEnabled, reverseLayout, pageContent)") { a ->
        val state = a.typedOrNull<PagerState>("state")
        val content = a.scopedContent1<PagerScope>("pageContent")
        if (state == null) {
            ErrorBox("VerticalPager needs a PagerState, e.g. rememberPagerState { 3 }")
        } else {
            VerticalPager(
                state = state,
                modifier = a.modifier(),
                contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
                pageSize = a.typed<PageSize>("pageSize", PageSize.Fill),
                beyondViewportPageCount = a.int("beyondViewportPageCount", PagerDefaults.BeyondViewportPageCount),
                pageSpacing = a.dp("pageSpacing", 0.dp),
                horizontalAlignment = a.typed<Alignment.Horizontal>("horizontalAlignment", Alignment.CenterHorizontally),
                userScrollEnabled = a.boolean("userScrollEnabled", true),
                reverseLayout = a.boolean("reverseLayout", false),
            ) { page -> content(page) }
        }
    }
    staggeredGrids()
}

private fun Registry.staggeredGrids() {
    type<StaggeredGridCells>("StaggeredGridCells")
    value("StaggeredGridCells", StaggeredGridCellsFactory)
    member<StaggeredGridCellsFactory>("Fixed(count)") { _, a -> StaggeredGridCells.Fixed(a.int("count", 1)) }
    member<StaggeredGridCellsFactory>("Adaptive(minSize)") { _, a -> StaggeredGridCells.Adaptive(a.dp("minSize", 100.dp)) }
    value("StaggeredGridItemSpan", StaggeredGridItemSpan.Companion)
    constants<StaggeredGridItemSpan.Companion>("FullLine" to StaggeredGridItemSpan.FullLine, "SingleLane" to StaggeredGridItemSpan.SingleLane)

    composable("LazyVerticalStaggeredGrid(columns, modifier, contentPadding, reverseLayout, verticalItemSpacing, horizontalArrangement, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyStaggeredGridScope>()
        LazyVerticalStaggeredGrid(
            columns = a.typed<StaggeredGridCells>("columns", StaggeredGridCells.Fixed(2)),
            modifier = a.modifier(),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = a.boolean("reverseLayout", false),
            verticalItemSpacing = a.dp("verticalItemSpacing", 0.dp),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", Arrangement.spacedBy(0.dp)),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    composable("LazyHorizontalStaggeredGrid(rows, modifier, contentPadding, reverseLayout, verticalArrangement, horizontalItemSpacing, userScrollEnabled, content)") { a ->
        val content = a.scopedAction<LazyStaggeredGridScope>()
        LazyHorizontalStaggeredGrid(
            rows = a.typed<StaggeredGridCells>("rows", StaggeredGridCells.Fixed(2)),
            modifier = a.modifier(),
            contentPadding = a.typed<PaddingValues>("contentPadding", PaddingValues(0.dp)),
            reverseLayout = a.boolean("reverseLayout", false),
            verticalArrangement = a.typed<Arrangement.Vertical>("verticalArrangement", Arrangement.spacedBy(0.dp)),
            horizontalItemSpacing = a.dp("horizontalItemSpacing", 0.dp),
            userScrollEnabled = a.boolean("userScrollEnabled", true),
        ) { content() }
    }
    member<LazyStaggeredGridScope>("item(key, contentType, span, content)") { scope, a ->
        val content = a.scopedContent<LazyStaggeredGridItemScope>()
        scope.item(key = a["key"]?.let(::lazyKey), contentType = a["contentType"], span = a.typedOrNull<StaggeredGridItemSpan>("span")) { content() }
    }
    member<LazyStaggeredGridScope>("items(items, key, contentType, span, itemContent, *, count)") { scope, a ->
        val content = a.scopedContent1<LazyStaggeredGridItemScope>("itemContent")
        val list = a["items"] as? List<*>
        val count = list?.size ?: a["items"] as? Int ?: a["count"] as? Int
        if (count == null) {
            a.report("items needs a count or a list")
        } else {
            val span = a.itemFunction("span", list)
            scope.items(
                count = count,
                key = a.itemFunction("key", list)?.keys(),
                contentType = a.itemFunction("contentType", list) ?: { null },
                span = span?.let { fn -> { index -> fn(index) as? StaggeredGridItemSpan ?: StaggeredGridItemSpan.SingleLane } },
            ) { content(if (list != null) list[it] else it) }
        }
    }
}
