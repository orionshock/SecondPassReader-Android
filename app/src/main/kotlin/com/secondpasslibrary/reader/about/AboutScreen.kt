package com.secondpasslibrary.reader.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.R
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail

@Composable
internal fun AboutScreen(info: AboutInfo) {
    val context = LocalContext.current
    var licensesOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.app_icon), null, Modifier.size(64.dp))
            Text(
                "Second Pass Reader",
                Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        InformationCard("App") {
            InformationDetail("Version", info.versionName)
            InformationDetail("Version code", info.versionCode.toString())
            InformationDetail("Package ID", info.packageId)
        }
        InformationCard("Device") {
            InformationDetail("Model", info.device)
            InformationDetail("Android", "${info.androidVersion} / API ${info.apiLevel}")
        }
        if (info.hasLibraryDetails) {
            InformationCard("Library") {
                info.libraryName?.let { InformationDetail("Name", it) }
                info.serverVersion?.let { InformationDetail("Server version", it) }
                info.serverId?.let { InformationDetail("Server ID", it) }
                info.libraryUrl?.let { InformationDetail("Library URL", it) }
            }
        }
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("Second Pass Reader app info", info.copyText())
            )
            Toast.makeText(context, "App info copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy app info") }
        TextButton(onClick = { licensesOpen = true }) { Text("Open source licenses") }
    }
    if (licensesOpen) OpenSourceLicensesDialog(onDismiss = { licensesOpen = false })
}

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        context.assets.open("licenses/open_source_licenses.txt").bufferedReader().use {
            it.readText()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open source licenses") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(notices, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        modifier = Modifier.fillMaxWidth()
    )
}
