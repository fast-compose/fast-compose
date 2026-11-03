package ma7moud3ly.fast.compose.interpreter.jvm.fixtures

/** A small "library" for JarResolverTest: a data class with a companion, an object, and top-level declarations. */
data class Money(val amount: Long, val currency: String = "USD") {
    fun plus(other: Money): Money = copy(amount = amount + other.amount)

    companion object {
        fun of(amount: Int): Money = Money(amount.toLong())
    }
}

object Formats {
    fun label(money: Money, prefix: String = "$"): String = prefix + money.amount
}

val DEFAULT_CURRENCY: String = "EUR"

fun Money.doubled(): Money = copy(amount = amount * 2)

fun total(vararg values: Int): Int = values.sum()

fun mapEach(values: List<Int>, transform: (Int) -> Int): List<Int> = values.map(transform)
