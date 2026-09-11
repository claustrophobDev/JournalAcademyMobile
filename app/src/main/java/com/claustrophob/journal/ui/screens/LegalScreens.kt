package com.claustrophob.journal.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.BuildConfig
import com.claustrophob.journal.R
import com.claustrophob.journal.ui.components.SectionCard
import com.claustrophob.journal.ui.components.SubScreen
import com.claustrophob.journal.util.Downloader

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(28.dp))
                AppMark(size = 64)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Соглашение и конфиденциальность",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Legal.REVISION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))
                DisclaimerBox()

                LegalGroup("Лицензионное соглашение", Legal.LICENSE)
                LegalGroup("Политика конфиденциальности", Legal.PRIVACY)

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Разработчик: ${Legal.DEVELOPER}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }

            HorizontalDivider()

            Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text(
                    text = "Нажимая «Принять и продолжить», вы подтверждаете, что прочитали и " +
                        "принимаете условия.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAccept,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Принять и продолжить", style = MaterialTheme.typography.titleSmall)
                }
                TextButton(
                    onClick = { context.findActivity()?.finish() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отказаться и закрыть")
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SubScreen(title = "О приложении", onBack = onBack) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppMark(size = 52)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Журнал", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Версия ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Fact("Разработчик", Legal.DEVELOPER)
                    Fact("Идентификатор", BuildConfig.APPLICATION_ID)
                    Fact("Возрастная категория", Legal.AGE_RATING)
                }
            }

            item { DisclaimerBox() }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Официальный журнал",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = Legal.JOURNAL_URL.removePrefix("https://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { Downloader.openLink(context, Legal.JOURNAL_URL) }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Открыть")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Лицензионное соглашение") {
                    Legal.LICENSE.forEachIndexed { index, (title, body) ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Clause(title, body)
                    }
                }
            }

            item {
                SectionCard(title = "Политика конфиденциальности") {
                    Legal.PRIVACY.forEachIndexed { index, (title, body) ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Clause(title, body)
                    }
                }
            }

            item {
                Text(
                    text = Legal.REVISION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

// Логотип. Берём PNG-слой, потому что adaptive-icon из mipmap Compose не рисует.
@Composable
private fun AppMark(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(size * 0.28f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
private fun DisclaimerBox() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = Legal.DISCLAIMER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LegalGroup(header: String, clauses: List<Pair<String, String>>) {
    Spacer(Modifier.height(26.dp))
    Text(
        text = header,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    clauses.forEach { (title, body) ->
        Spacer(Modifier.height(18.dp))
        Clause(title, body)
    }
}

@Composable
private fun Clause(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
