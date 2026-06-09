/*
 * Ever Call Recorder
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later.
 */

package com.coolappstore.evercallrecorder.by.svhp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coolappstore.evercallrecorder.by.svhp.AppUrls
import com.coolappstore.evercallrecorder.by.svhp.R
import com.coolappstore.evercallrecorder.by.svhp.ui.theme.ShizucallrecorderTheme

@Composable
fun DisclaimerScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var hasAccepted by rememberSaveable { mutableStateOf(false) }
    var hasScrolledToBottom by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.canScrollForward) {
        if (!scrollState.canScrollForward) {
            hasScrolledToBottom = true
        }
    }

    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            val links = mapOf(
                stringResource(R.string.disclaimer_wiki_link_KEYWORD) to AppUrls.GITHUB_WIKI
            )
            HyperlinkText(stringResource(R.string.disclaimer_introduction), links)

            Spacer(modifier = Modifier.height(1.dp))

            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = stringResource(R.string.disclaimer_body),
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineBreak = LineBreak.Paragraph
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = hasAccepted,
                        onValueChange = { if (hasScrolledToBottom) hasAccepted = it },
                        role = Role.Checkbox,
                        enabled = hasScrolledToBottom
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = hasAccepted,
                    onCheckedChange = null,
                    enabled = hasScrolledToBottom
                )
                Text(
                    text = stringResource(R.string.disclaimer_checkbox_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasScrolledToBottom) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            val canContinue = hasAccepted && hasScrolledToBottom

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = canContinue,
            ) {
                if (!hasScrolledToBottom) {
                    Text(text = stringResource(R.string.disclaimer_must_read))
                } else {
                    Text(text = stringResource(R.string.general_continue))
                }
            }
        }
    }
}

@Composable
fun HyperlinkText(
    fullText: String,
    links: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    val listener = LinkInteractionListener { link ->
        if (link is LinkAnnotation.Clickable) {
            uriHandler.openUri(link.tag)
        }
    }

    val annotatedText = buildAnnotatedString {
        append(fullText)
        links.forEach { (keyword, url) ->
            val startIndex = fullText.indexOf(keyword)
            if (startIndex != -1) {
                val endIndex = startIndex + keyword.length
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        ),
                        linkInteractionListener = listener
                    ),
                    start = startIndex,
                    end = endIndex
                )
            }
        }
    }

    Text(text = annotatedText, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun DisclaimerScreenPreview() {
    ShizucallrecorderTheme {
        DisclaimerScreen(onContinue = {})
    }
}
