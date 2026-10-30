package ma7moud3ly.fast.compose

val sampleCode = """
// 1. Several Components
@Preview
@Composable
fun ComponentsExample() {
    var name by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("Accept terms")
        }

        Button(onClick = { showDialog = true }) {
            Text("Continue")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hello, ${'$'}name!") },
            text = { Text("Welcome to Jetpack Compose.") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

// 2. Animations and Canvas
@Preview
@Composable
fun AnimatedCanvasExample() {
    val transition = rememberInfiniteTransition(label = "pulse")

    val progress by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    Canvas(
        Modifier.fillMaxWidth().height(180.dp)
    ) {
        drawCircle(
            color = Color(0xFF6750A4),
            radius = size.minDimension * 0.25f * progress,
            center = Offset(size.width / 2, size.height / 2)
        )
    }
}

// 3. Network Image (Coil 3)
@Preview
@Composable
fun NetworkImageExample() {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = "https://picsum.photos/id/64/200",
            contentDescription = "Profile picture",
            modifier = Modifier.size(80.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(16.dp))

        Column {
            Text("Lexi", style = MaterialTheme.typography.titleLarge)
            Text("Jetpack Compose Developer")
        }
    }
}
""".trimIndent()
