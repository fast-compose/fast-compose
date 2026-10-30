package ma7moud3ly.fast.compose.bindings.resources

/** Reads the `<string name="…">` entries of an Android or Compose Multiplatform `strings.xml` file. */
public object StringsXml {

    private val entry = Regex("""<string\s+name\s*=\s*"([^"]+)"[^>]*?(?:/>|>([\s\S]*?)</string>)""")
    private val comment = Regex("""<!--[\s\S]*?-->""")
    private val cdata = Regex("""<!\[CDATA\[([\s\S]*?)]]>""")
    private val tag = Regex("""<[^>]+>""")

    /** Names and values of the strings in [xml], with quotes, escapes and markup tags resolved. */
    public fun parse(xml: String): Map<String, String> =
        entry.findAll(comment.replace(xml, "")).associate { match -> match.groupValues[1] to value(match.groupValues[2]) }

    private fun value(raw: String): String {
        var text = cdata.replace(raw) { it.groupValues[1] }
        text = tag.replace(text, "").trim()
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) text = text.substring(1, text.length - 1)
        return unescape(text)
    }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
