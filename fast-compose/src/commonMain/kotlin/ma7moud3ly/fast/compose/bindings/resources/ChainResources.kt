package ma7moud3ly.fast.compose.bindings.resources

/**
 * Looks resources up in [providers] in order, e.g. the previewed module's folders first, then its dependencies'.
 * While an earlier provider is still reading a match, later ones are not used, so a resource never flips from a
 * dependency's version to the module's own.
 */
public class ChainResources(private val providers: List<ResourceProvider>) : ResourceProvider {

    override fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray? {
        for (provider in providers) {
            provider.bytes(type, name, environment)?.let { return it }
            if (provider.isLoading(type, name, environment)) return null
        }
        return null
    }

    override fun string(name: String, environment: ResourceEnvironment): String? {
        for (provider in providers) {
            provider.string(name, environment)?.let { return it }
            if (provider.isLoading(STRING, name, environment)) return null
        }
        return null
    }

    override fun isLoading(type: String, name: String, environment: ResourceEnvironment): Boolean {
        for (provider in providers) {
            if (provider.isLoading(type, name, environment)) return true
            val found = if (type == STRING) {
                provider.string(name, environment) != null
            } else {
                provider.bytes(type, name, environment) != null
            }
            if (found) return false
        }
        return false
    }

    private companion object {
        const val STRING = "string"
    }
}
