package ma7moud3ly.fast.compose.bindings.material3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.ui.graphics.Shape
import ma7moud3ly.fast.compose.interpreter.Args
import ma7moud3ly.fast.compose.interpreter.Registry

/** Argument of a nullable parameter: the passed value (null included), or [default] when it was not passed. */
internal inline fun <reified T> Args.nullable(name: String, default: T?): T? = if (has(name)) typedOrNull<T>(name) else default

/** Assist, suggestion, filter and input chips, flat and elevated. */
internal fun Registry.chips() {
    composable("AssistChip(onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        val enabled = a.boolean("enabled", true)
        AssistChip(
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = enabled,
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            shape = a.typed<Shape>("shape", AssistChipDefaults.shape),
            colors = a.typed("colors", AssistChipDefaults.assistChipColors()),
            elevation = a.nullable("elevation", AssistChipDefaults.assistChipElevation()),
            border = a.nullable("border", AssistChipDefaults.assistChipBorder(enabled)),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", AssistChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", AssistChipDefaults.ContentPadding),
        )
    }
    composable("ElevatedAssistChip(onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        ElevatedAssistChip(
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            shape = a.typed<Shape>("shape", AssistChipDefaults.shape),
            colors = a.typed("colors", AssistChipDefaults.elevatedAssistChipColors()),
            elevation = a.nullable("elevation", AssistChipDefaults.elevatedAssistChipElevation()),
            border = a.nullable<BorderStroke>("border", null),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", AssistChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", AssistChipDefaults.ContentPadding),
        )
    }
    composable("SuggestionChip(onClick, label, modifier, enabled, icon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        val enabled = a.boolean("enabled", true)
        SuggestionChip(
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = enabled,
            icon = a.contentOrNull("icon"),
            shape = a.typed<Shape>("shape", SuggestionChipDefaults.shape),
            colors = a.typed("colors", SuggestionChipDefaults.suggestionChipColors()),
            elevation = a.nullable("elevation", SuggestionChipDefaults.suggestionChipElevation()),
            border = a.nullable("border", SuggestionChipDefaults.suggestionChipBorder(enabled)),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", SuggestionChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", SuggestionChipDefaults.ContentPadding),
        )
    }
    composable("ElevatedSuggestionChip(onClick, label, modifier, enabled, icon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        ElevatedSuggestionChip(
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            icon = a.contentOrNull("icon"),
            shape = a.typed<Shape>("shape", SuggestionChipDefaults.shape),
            colors = a.typed("colors", SuggestionChipDefaults.elevatedSuggestionChipColors()),
            elevation = a.nullable("elevation", SuggestionChipDefaults.elevatedSuggestionChipElevation()),
            border = a.nullable<BorderStroke>("border", null),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", SuggestionChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", SuggestionChipDefaults.ContentPadding),
        )
    }
    composable("FilterChip(selected, onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        val selected = a.boolean("selected", false)
        val enabled = a.boolean("enabled", true)
        FilterChip(
            selected = selected,
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = enabled,
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            shape = a.typed<Shape>("shape", FilterChipDefaults.shape),
            colors = a.typed("colors", FilterChipDefaults.filterChipColors()),
            elevation = a.nullable("elevation", FilterChipDefaults.filterChipElevation()),
            border = a.nullable("border", FilterChipDefaults.filterChipBorder(enabled, selected)),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", FilterChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", FilterChipDefaults.ContentPadding),
        )
    }
    composable("ElevatedFilterChip(selected, onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding)") { a ->
        ElevatedFilterChip(
            selected = a.boolean("selected", false),
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = a.boolean("enabled", true),
            leadingIcon = a.contentOrNull("leadingIcon"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            shape = a.typed<Shape>("shape", FilterChipDefaults.shape),
            colors = a.typed("colors", FilterChipDefaults.elevatedFilterChipColors()),
            elevation = a.nullable("elevation", FilterChipDefaults.elevatedFilterChipElevation()),
            border = a.nullable<BorderStroke>("border", null),
            horizontalArrangement = a.typed<Arrangement.Horizontal>("horizontalArrangement", FilterChipDefaults.horizontalArrangement()),
            contentPadding = a.typed<PaddingValues>("contentPadding", FilterChipDefaults.ContentPadding),
        )
    }
    composable("InputChip(selected, onClick, label, modifier, enabled, leadingIcon, avatar, trailingIcon, shape, colors, elevation, border)") { a ->
        val selected = a.boolean("selected", false)
        val enabled = a.boolean("enabled", true)
        InputChip(
            selected = selected,
            onClick = a.action("onClick"),
            label = a.content("label"),
            modifier = a.modifier(),
            enabled = enabled,
            leadingIcon = a.contentOrNull("leadingIcon"),
            avatar = a.contentOrNull("avatar"),
            trailingIcon = a.contentOrNull("trailingIcon"),
            shape = a.typed<Shape>("shape", InputChipDefaults.shape),
            colors = a.typed("colors", InputChipDefaults.inputChipColors()),
            elevation = a.nullable("elevation", InputChipDefaults.inputChipElevation()),
            border = a.nullable("border", InputChipDefaults.inputChipBorder(enabled, selected)),
        )
    }
}
