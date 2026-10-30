package ma7moud3ly.fast.compose.parser

import ma7moud3ly.fast.compose.parser.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {

    private fun parse(source: String): Program {
        val result = KotlinParser.parse(source)
        assertEquals(emptyList<Diagnostic>(), result.diagnostics)
        return result.program
    }

    /** Parses [code] as the body of a function and returns its statements. */
    private fun body(code: String): List<Stmt> = parse("fun test() {\n$code\n}").functions.single().body!!.statements

    private fun expr(code: String): Expr = assertIs<ExprStmt>(body(code).single()).expr

    private fun initializer(code: String): Expr = assertIs<PropertyDecl>(body(code).single()).initializer!!

    @Test
    fun composableWithModifier() {
        val program = parse(
            """
            @Composable
            fun Hell() {
                Text("Hello", Modifier.width(100.dp))
            }
            """.trimIndent(),
        )
        val fn = program.functions.single()
        assertEquals("Hell", fn.name)
        assertTrue(fn.hasAnnotation("Composable"))
        assertTrue(program.previews.isEmpty())

        val call = assertIs<Call>(assertIs<ExprStmt>(fn.body!!.statements.single()).expr)
        assertEquals("Text", assertIs<Ident>(call.callee).name)
        val text = assertIs<StringTemplate>(call.args[0].value)
        assertEquals(listOf<StringPart>(StringPart.Text("Hello")), text.parts)

        val width = assertIs<Call>(call.args[1].value)
        val widthCallee = assertIs<Member>(width.callee)
        assertEquals("width", widthCallee.name)
        assertEquals("Modifier", assertIs<Ident>(widthCallee.receiver).name)
        val dp = assertIs<Member>(width.args.single().value)
        assertEquals("dp", dp.name)
        assertEquals(100, assertIs<IntLit>(dp.receiver).value)
    }

    @Test
    fun brokenCallRecovers() {
        val result = KotlinParser.parse(
            """
            @Composable
            fun Hell(){
            Text('Hello',Modefier.width(...))
            }
            """.trimIndent(),
        )
        assertEquals(2, result.diagnostics.size)
        assertEquals("Hell", result.program.functions.single().name)
    }

    @Test
    fun dataClassAndPreview() {
        val program = parse(
            """
            data class Message(val author: String, val body: String)

            @Composable
            fun MessageCard(msg: Message) {
                Column {
                    Text(text = msg.author)
                    Text(msg.body)
                }
            }

            @Preview
            @Composable
            fun PreviewMessageCard() {
                MessageCard(msg = Message("Lexi", "Hey, take a look at Jetpack Compose, it's great!"))
            }
            """.trimIndent(),
        )
        val cls = assertIs<ClassDecl>(program.declarations[0])
        assertEquals(ClassKind.DATA_CLASS, cls.kind)
        assertEquals(listOf("author", "body"), cls.constructorParams.map { it.param.name })
        assertTrue(cls.constructorParams.all { it.isProperty && !it.isVar })

        assertEquals("PreviewMessageCard", program.previews.single().first.name)

        val card = program.functions.first { it.name == "MessageCard" }
        assertEquals("Message", card.params.single().type?.text)
        val column = assertIs<Call>(assertIs<ExprStmt>(card.body!!.statements.single()).expr)
        assertEquals(2, column.trailingLambda!!.body.statements.size)
        val firstText = assertIs<Call>(assertIs<ExprStmt>(column.trailingLambda.body.statements[0]).expr)
        assertEquals("text", firstText.args.single().name)
    }

    @Test
    fun stateDelegate() {
        val property = assertIs<PropertyDecl>(body("var count by remember { mutableStateOf(0) }").single())
        assertTrue(property.isVar)
        assertNull(property.initializer)
        val remember = assertIs<Call>(property.delegate)
        assertEquals("remember", assertIs<Ident>(remember.callee).name)
        assertEquals(1, remember.trailingLambda!!.body.statements.size)
    }

    @Test
    fun multilineModifierChain() {
        val chain = assertIs<Call>(initializer("val m = Modifier\n    .padding(8.dp)\n    .fillMaxSize()"))
        assertEquals("fillMaxSize", assertIs<Member>(chain.callee).name)
        val padding = assertIs<Call>(assertIs<Member>(chain.callee).receiver)
        assertEquals("padding", assertIs<Member>(padding.callee).name)
    }

    @Test
    fun precedence() {
        val sum = assertIs<Binary>(initializer("val x = 1 + 2 * 3"))
        assertEquals("+", sum.op)
        assertEquals("*", assertIs<Binary>(sum.right).op)

        val or = assertIs<Binary>(initializer("val y = a || b && c"))
        assertEquals("||", or.op)
        assertEquals("&&", assertIs<Binary>(or.right).op)

        val range = assertIs<Binary>(initializer("val z = x in 1..5"))
        assertEquals("in", range.op)
        assertEquals("..", assertIs<Binary>(range.right).op)
    }

    @Test
    fun infixCall() {
        val call = assertIs<Call>(initializer("val r = 0 until 10"))
        val callee = assertIs<Member>(call.callee)
        assertEquals("until", callee.name)
        assertEquals(0, assertIs<IntLit>(callee.receiver).value)
        assertEquals(10, assertIs<IntLit>(call.args.single().value).value)
    }

    @Test
    fun ifAndWhen() {
        val ifExpr = assertIs<IfExpr>(initializer("val s = if (a) \"x\" else \"y\""))
        assertEquals(1, ifExpr.otherwise!!.statements.size)

        val whenExpr = assertIs<WhenExpr>(
            initializer(
                """
                val label = when (x) {
                    0 -> "zero"
                    in 1..9 -> "small"
                    is String -> "text"
                    else -> "big"
                }
                """.trimIndent(),
            ),
        )
        assertEquals(4, whenExpr.branches.size)
        assertIs<WhenCondition.Value>(whenExpr.branches[0].conditions.single())
        assertIs<WhenCondition.In>(whenExpr.branches[1].conditions.single())
        assertEquals("String", assertIs<WhenCondition.Is>(whenExpr.branches[2].conditions.single()).type.text)
        assertTrue(whenExpr.branches[3].conditions.isEmpty())
    }

    @Test
    fun lambdaParameters() {
        val forEach = assertIs<Call>(expr("items.forEach { item -> Text(item) }"))
        assertEquals(listOf("item"), forEach.trailingLambda!!.params)

        val lambda = assertIs<Lambda>(initializer("val f = { a: Int, b: Int -> a + b }"))
        assertEquals(listOf("a", "b"), lambda.params)

        val noParams = assertIs<Lambda>(initializer("val g = { (a + b) * 2 }"))
        assertTrue(noParams.params.isEmpty())
    }

    @Test
    fun genericCallsAndComparisons() {
        val remember = assertIs<Call>(initializer("val n = remember<Int> { 0 }"))
        assertEquals("remember", assertIs<Ident>(remember.callee).name)

        val list = assertIs<Call>(initializer("val l = listOf<String>()"))
        assertEquals("listOf", assertIs<Ident>(list.callee).name)

        assertEquals("<", assertIs<Binary>(initializer("val b = a < b")).op)
    }

    @Test
    fun functionTypes() {
        val fn = parse("fun Card(content: @Composable RowScope.() -> Unit, slot: @Composable () -> Unit, modifier: Modifier = Modifier) {}")
            .functions.single()
        assertEquals(
            listOf("@Composable RowScope.() -> Unit", "@Composable () -> Unit", "Modifier"),
            fn.params.map { it.type?.text },
        )
        assertIs<Ident>(fn.params[2].default)
    }

    @Test
    fun assignments() {
        val statements = body("count += 1\nexpanded = !expanded")
        assertEquals("+=", assertIs<Assign>(statements[0]).op)
        val assign = assertIs<Assign>(statements[1])
        assertEquals("!", assertIs<Unary>(assign.value).op)
    }

    @Test
    fun enumClass() {
        val cls = assertIs<ClassDecl>(parse("enum class Screen { Home, Settings }").declarations.single())
        assertEquals(ClassKind.ENUM, cls.kind)
        assertEquals(listOf("Home", "Settings"), cls.enumEntries.map { it.name })
    }

    @Test
    fun statementErrorRecovers() {
        val result = KotlinParser.parse("fun A() {\n    val = 1\n    Text(\"ok\")\n}")
        assertEquals(1, result.diagnostics.size)
        assertEquals(1, result.program.functions.single().body!!.statements.size)
    }

    @Test
    fun topLevelErrorRecovers() {
        val result = KotlinParser.parse("Text(\"hi\")\n@Composable fun A() {}")
        assertEquals(1, result.diagnostics.size)
        assertEquals("A", result.program.functions.single().name)
    }

    @Test
    fun stringTemplateParts() {
        val template = assertIs<StringTemplate>(initializer("val s = \"Count: \$count \${a + 1}\""))
        assertEquals(4, template.parts.size)
        assertEquals("count", assertIs<Ident>(assertIs<StringPart.Template>(template.parts[1]).expr).name)
        assertIs<Binary>(assertIs<StringPart.Template>(template.parts[3]).expr)
    }

    @Test
    fun multiplePreviews() {
        val program = parse(
            """
            @Preview(name = "Light")
            @Preview(name = "Dark", uiMode = 0x20)
            @Composable
            fun A() {}

            @Composable
            fun NotAPreview() {}

            @Preview(widthDp = 200)
            @Composable
            fun B() {}
            """.trimIndent(),
        )
        assertEquals(listOf("A", "A", "B"), program.previews.map { it.first.name })
        assertEquals(listOf("name", "uiMode"), program.previews[1].second.args.map { it.name })
    }

    @Test
    fun fileAnnotationsAreSkipped() {
        val program = parse("@file:OptIn(ExperimentalMaterial3Api::class)\n\npackage x\n\n@Composable fun A() {}")
        assertEquals("A", program.functions.single().name)
    }

    @Test
    fun previewsMustBeComposable() {
        val result = KotlinParser.parse("@Preview\nfun A() {}\n\n@Preview\n@Composable\nfun B() {}")
        assertEquals(listOf("B"), result.program.previews.map { it.first.name })
        val warning = result.diagnostics.single()
        assertEquals(Diagnostic.Severity.WARNING, warning.severity)
        assertEquals(1, warning.span.line)
    }

    @Test
    fun packageAndImports() {
        val program = parse("package x.y\nimport a.b.C\nimport a.b.*\nimport a.c.D as E\n\n@Composable fun A() {}")
        assertEquals("A", program.functions.single().name)
        assertEquals("x.y", program.packageName)
        assertEquals(listOf(Import("a.b.C"), Import("a.b", isStar = true), Import("a.c.D", alias = "E")), program.imports)
        assertEquals(listOf("C", null, "E"), program.imports.map { it.visibleName })
        assertEquals(listOf("a.b", "a.b", "a.c"), program.imports.map { it.packageName })
    }

    @Test
    fun privateDeclarations() {
        val program = parse("private fun a() {}\nfun b() {}\nprivate val c = 1\nprivate class D")
        assertEquals(listOf(true, false), program.functions.map { it.isPrivate })
        assertTrue(assertIs<PropertyDecl>(program.declarations[2]).isPrivate)
        assertTrue(assertIs<ClassDecl>(program.declarations[3]).isPrivate)
    }

    @Test
    fun externalNames() {
        val program = parse(
            """
            import a.Card

            @Composable
            fun Screen(user: User, items: List<Item>) {
                val title = format(user.name)
                Column {
                    Card(title)
                    items.forEach { item -> Row(item, ::helper) }
                    Local()
                }
            }

            fun Local() {}
            """.trimIndent(),
        )
        assertEquals(setOf("User", "List", "Item", "format", "Column", "Card", "Row", "helper"), program.externalNames())
    }

    @Test
    fun expressionBodyAndReceiver() {
        val program = parse("fun twice(x: Int) = x * 2\nfun Modifier.card(): Modifier = this.padding(4.dp)")
        val twice = program.functions[0]
        assertIs<Binary>(assertIs<ReturnStmt>(twice.body!!.statements.single()).value)
        val card = program.functions[1]
        assertEquals("card", card.name)
        assertEquals("Modifier", card.receiver?.text)
    }

    @Test
    fun argsAndTrailingLambda() {
        val button = assertIs<Call>(expr("Button(onClick = { count++ }) { Text(\"Add\") }"))
        val onClick = assertIs<Lambda>(button.args.single().value)
        assertEquals("onClick", button.args.single().name)
        val increment = assertIs<Unary>(assertIs<ExprStmt>(onClick.body.statements.single()).expr)
        assertEquals("++", increment.op)
        assertEquals(1, button.trailingLambda!!.body.statements.size)
    }

    @Test
    fun largeHexBecomesLong() {
        val color = assertIs<Call>(initializer("val c = Color(0xFF6200EE)"))
        assertEquals(0xFF6200EE, assertIs<LongLit>(color.args.single().value).value)
    }

    @Test
    fun labeledReturn() {
        val forEach = assertIs<Call>(expr("items.forEach { if (it > 2) return@forEach }"))
        val ifExpr = assertIs<IfExpr>(assertIs<ExprStmt>(forEach.trailingLambda!!.body.statements.single()).expr)
        val ret = assertIs<ReturnStmt>(ifExpr.then.statements.single())
        assertEquals("forEach", ret.label)
        assertNull(ret.value)
    }
}
