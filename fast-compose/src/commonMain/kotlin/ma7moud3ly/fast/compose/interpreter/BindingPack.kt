package ma7moud3ly.fast.compose.interpreter

/**
 * A group of bindings for one library or topic, installed into a [Registry] with [Registry.install].
 * Supporting more APIs means adding or extending a pack; the parser and interpreter stay unchanged.
 *
 * ```
 * object MyDesignSystem : BindingPack {
 *     override fun install(registry: Registry) = with(registry) {
 *         composable("BrandButton(text, onClick, modifier)") { a ->
 *             BrandButton(a.string("text"), a.action("onClick"), a.modifier())
 *         }
 *     }
 * }
 * ```
 */
public fun interface BindingPack {
    public fun install(registry: Registry)
}
