package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: VibeViewModel) {
    val context = LocalContext.current
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val settingsMsg by viewModel.settingsMessage.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showSavedTranscripts by remember { mutableStateOf(false) }
    var showRawJsonImporter by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settingsMsg) {
        settingsMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.cleanSettingsMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "VibeWhisper",
                            fontWeight = FontWeight.Bold,
                            color = SleekTextMain,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Language Study & Subtitle Player",
                            fontSize = 11.sp,
                            color = SleekTextSoft
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRawJsonImporter = true }) {
                        Icon(Icons.Outlined.Code, contentDescription = "Import Subtitle JSON", tint = SleekPrimary)
                    }
                    IconButton(onClick = { showSavedTranscripts = true }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Daftar Transkrip", tint = SleekPrimary)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setelan Server", tint = SleekPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SleekBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SleekBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentTranscript == null) {
                // Intro Screen / Setup Screen
                TranscriptionSetupScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                // Split player & scrollable lyrics learning screen
                StudyDashboardScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            }

            // Dialog Popups
            if (showSettings) {
                SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
            }
            if (showSavedTranscripts) {
                SavedTranscriptsDialog(viewModel = viewModel, onDismiss = { showSavedTranscripts = false })
            }
            if (showRawJsonImporter) {
                RawJsonImportDialog(viewModel = viewModel, onDismiss = { showRawJsonImporter = false })
            }
        }
    }
}

@Composable
fun TranscriptionSetupScreen(
    viewModel: VibeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTranscribing by viewModel.isTranscribing.collectAsStateWithLifecycle()
    val logs by viewModel.transcriptionLogs.collectAsStateWithLifecycle()
    val progressStage by viewModel.progressStage.collectAsStateWithLifecycle()
    val progressPercent by viewModel.progressPercent.collectAsStateWithLifecycle()

    val sourceMode by viewModel.selectedSource.collectAsStateWithLifecycle()
    val urlVal by viewModel.urlInput.collectAsStateWithLifecycle()
    val localUri by viewModel.localFileUri.collectAsStateWithLifecycle()
    val localName by viewModel.localFileName.collectAsStateWithLifecycle()

    val langVal by viewModel.selectedLang.collectAsStateWithLifecycle()
    val modelVal by viewModel.selectedModel.collectAsStateWithLifecycle()
    val saveOnServer by viewModel.saveToServer.collectAsStateWithLifecycle()

    val trimModeVal by viewModel.trimMode.collectAsStateWithLifecycle()
    val trimStartVal by viewModel.trimStart.collectAsStateWithLifecycle()
    val trimEndVal by viewModel.trimEnd.collectAsStateWithLifecycle()
    val serverUrlVal by viewModel.serverUrl.collectAsStateWithLifecycle()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.localFileUri.value = uri
            viewModel.localFileName.value = getFileName(context, uri)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SleekPrimaryContainer)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(SleekPrimary, RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = "Server Connected Status",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SERVER STATUS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleekNavy,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (serverUrlVal.isNotBlank()) serverUrlVal else "api.json-server.dev/v1",
                        fontSize = 14.sp,
                        color = SleekNavy.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "LATENCY",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SleekNavy
                )
                Text(
                    text = "24ms",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SleekNavy
                )
            }
        }

        // Source Selection Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SleekNavBarBg)
                .border(BorderStroke(1.dp, SleekOutline), RoundedCornerShape(24.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { viewModel.selectedSource.value = "youtube" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sourceMode == "youtube") SleekPrimaryContainer else Color.Transparent,
                    contentColor = if (sourceMode == "youtube") SleekNavy else SleekTextSoft
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("YouTube Link", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { viewModel.selectedSource.value = "local" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sourceMode == "local") SleekPrimaryContainer else Color.Transparent,
                    contentColor = if (sourceMode == "local") SleekNavy else SleekTextSoft
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Upload Video", fontWeight = FontWeight.SemiBold)
            }
        }

        // Selected Source Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sourceMode == "youtube") {
                    Text("Tautan Video YouTube", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = urlVal,
                        onValueChange = { viewModel.urlInput.value = it },
                        placeholder = { Text("https://www.youtube.com/watch?v=...", color = SleekTextSoft) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SleekTextMain,
                            unfocusedTextColor = SleekTextMain,
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = SleekOutline
                        ),
                        singleLine = true
                    )
                } else {
                    Text("Pilih Video dari Handphone", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold)
                    if (localUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SleekNavBarBg)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Filled.VideoFile, contentDescription = "Video file selected", tint = SleekPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = localName ?: "file_video.mp4",
                                color = SleekTextMain,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.0f)
                            )
                            IconButton(onClick = {
                                viewModel.localFileUri.value = null
                                viewModel.localFileName.value = null
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = AlertDanger)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, SleekPrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Add selection")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih Berkas Video")
                        }
                    }
                }

                // Trim Controls
                HorizontalDivider(color = SleekOutline.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bagian yang diproses", fontSize = 12.sp, color = SleekTextSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = trimModeVal == "full",
                                onClick = { viewModel.trimMode.value = "full" },
                                colors = RadioButtonDefaults.colors(selectedColor = SleekPrimary)
                            )
                            Text("Penuh", color = SleekTextMain, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = trimModeVal == "partial",
                                onClick = { viewModel.trimMode.value = "partial" },
                                colors = RadioButtonDefaults.colors(selectedColor = SleekPrimary)
                            )
                            Text("Pilih waktu", color = SleekTextMain, fontSize = 13.sp)
                        }
                    }
                }

                if (trimModeVal == "partial") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = trimStartVal.toString(),
                                onValueChange = { viewModel.trimStart.value = it.toDoubleOrNull() ?: 0.0 },
                                label = { Text("Mulai (Detik)", color = SleekTextSoft, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SleekTextMain,
                                    unfocusedTextColor = SleekTextMain,
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = SleekOutline
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = trimEndVal.toString(),
                                onValueChange = { viewModel.trimEnd.value = it.toDoubleOrNull() ?: 60.0 },
                                label = { Text("Selesai (Detik)", color = SleekTextSoft, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = SleekTextMain,
                                    unfocusedTextColor = SleekTextMain,
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = SleekOutline
                                ),
                                singleLine = true
                            )
                        }
                        Text(
                            text = "Shortcuts: 30s, 1m, 3m",
                            fontSize = 11.sp,
                            color = SleekTextSoft
                        )
                    }
                }
            }
        }

        // Selection parameters
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekOutline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Language Select Dropdown
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bahasa Audio", modifier = Modifier.width(110.dp), color = SleekTextSoft, fontSize = 13.sp)
                    var expandedLang by remember { mutableStateOf(false) }
                    val currentLangName = when (langVal) {
                        "ja" -> "Jepang (ja)"
                        "id" -> "Indonesia (id)"
                        "ko" -> "Korea (ko)"
                        "zh" -> "Mandarin (zh)"
                        "en" -> "Inggris (en)"
                        else -> "Deteksi Otomatis"
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedLang = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextMain),
                            border = BorderStroke(1.dp, SleekOutline),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(currentLangName, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, "expand", tint = SleekPrimary)
                        }
                        DropdownMenu(
                            expanded = expandedLang,
                            onDismissRequest = { expandedLang = false },
                            modifier = Modifier.background(SleekSurface)
                        ) {
                            DropdownMenuItem(text = { Text("Jepang", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "ja"; expandedLang = false })
                            DropdownMenuItem(text = { Text("Indonesia", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "id"; expandedLang = false })
                            DropdownMenuItem(text = { Text("Korea", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "ko"; expandedLang = false })
                            DropdownMenuItem(text = { Text("Mandarin", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "zh"; expandedLang = false })
                            DropdownMenuItem(text = { Text("Inggris", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "en"; expandedLang = false })
                            DropdownMenuItem(text = { Text("Deteksi Otomatis", color = SleekTextMain) }, onClick = { viewModel.selectedLang.value = "auto"; expandedLang = false })
                        }
                    }
                }

                // AI Model Selection
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Model AI Size", modifier = Modifier.width(110.dp), color = SleekTextSoft, fontSize = 13.sp)
                    var expandedModel by remember { mutableStateOf(false) }
                    val modelLabel = when (modelVal) {
                        "tiny" -> "Tiny (lebih cepat & ringan)"
                        else -> "Small (lebih akurat)"
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedModel = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextMain),
                            border = BorderStroke(1.dp, SleekOutline),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(modelLabel, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, "expand", tint = SleekPrimary)
                        }
                        DropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false },
                            modifier = Modifier.background(SleekSurface)
                        ) {
                            DropdownMenuItem(text = { Text("Tiny (Lebih cepat & ringan)", color = SleekTextMain) }, onClick = { viewModel.selectedModel.value = "tiny"; expandedModel = false })
                            DropdownMenuItem(text = { Text("Small (Lebih akurat)", color = SleekTextMain) }, onClick = { viewModel.selectedModel.value = "small"; expandedModel = false })
                        }
                    }
                }

                // Save to server Option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.saveToServer.value = !saveOnServer }
                ) {
                    Checkbox(
                        checked = saveOnServer,
                        onCheckedChange = { viewModel.saveToServer.value = it },
                        colors = CheckboxDefaults.colors(checkedColor = SleekPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simpan hasil transkripsi ke database server untuk pembelajaran berikutnya",
                        color = SleekTextSoft,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Trigger Button / Progress
        if (isTranscribing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SleekPrimaryContainer),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, SleekOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = progressStage ?: "Memulai...",
                            color = SleekNavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        progressPercent?.let {
                            Text(
                                text = "${it.toInt()}%",
                                color = SleekNavy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    progressPercent?.let {
                        LinearProgressIndicator(
                            progress = { it.toFloat() / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = SleekPrimary,
                            trackColor = SleekOutline,
                        )
                    } ?: LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = SleekPrimary,
                        trackColor = SleekOutline
                    )

                    // Logs display terminal style
                    Text("Proses log server:", fontSize = 11.sp, color = SleekNavy)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.Black)
                            .border(1.dp, SleekOutline, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val state = rememberScrollState()
                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) {
                                state.animateScrollTo(state.maxValue)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .verticalScroll(state)
                                .fillMaxSize()
                        ) {
                            logs.forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (line.contains("❌")) AlertDanger else if (line.contains("✅")) AlertSuccess else Color(0xFFDCDFE3)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = { viewModel.startTranscription(context) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = "Gas transkripsi!")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mulai Transkripsi (Gas!)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun StudyDashboardScreen(
    viewModel: VibeViewModel,
    modifier: Modifier = Modifier
) {
    val transcript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val activeTime by viewModel.currentPlaybackTime.collectAsStateWithLifecycle()

    var activeWordIndex by remember { mutableIntStateOf(-1) }
    var activeSegmentIndex by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()

    var showExplanationSheet by remember { mutableStateOf(false) }
    var selectedTextForExplain by remember { mutableStateOf("") }

    // Synchronize active card and scrolling
    LaunchedEffect(activeTime, transcript) {
        val list = transcript?.subtitles ?: return@LaunchedEffect
        val time = activeTime
        var foundIdx = -1
        for (i in list.indices) {
            val seg = list[i]
            if (time >= seg.start && time < seg.end) {
                foundIdx = i
                break
            }
        }

        if (foundIdx != -1 && foundIdx != activeSegmentIndex) {
            activeSegmentIndex = foundIdx
            listState.animateScrollToItem(foundIdx)
        }
    }

    Scaffold(
        bottomBar = {
            PlaybackToolbar(
                viewModel = viewModel,
                activeSegmentIndex = activeSegmentIndex,
                onExplainRequest = { sentence ->
                    selectedTextForExplain = sentence
                    showExplanationSheet = true
                    viewModel.explainSentence(sentence)
                }
            )
        },
        containerColor = SleekBackground
    ) { paddingVals ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            // Player Top Block (YouTube or Local Player view)
            if (transcript?.sourceType == "youtube") {
                YoutubeWebViewPlayer(
                    videoId = extractVideoId(transcript?.sourceValue ?: ""),
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )
            } else {
                LocalVideoPlayer(
                    videoUriStr = transcript?.sourceValue,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )
            }

            // Interactive Subtitle Cards List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SleekBackground)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val list = transcript?.subtitles ?: emptyList()
                itemsIndexed(list) { idx, segment ->
                    val isSegmentActive = idx == activeSegmentIndex
                    SubtitleSegmentCard(
                        segment = segment,
                        idx = idx,
                        isActive = isSegmentActive,
                        currentPlaybackTime = activeTime,
                        onCardClicked = {
                            viewModel.currentPlaybackTime.value = segment.start
                            viewModel.isPlaying.value = true
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(130.dp)) // Padding for floating study control bars
                }
            }
        }
    }

    if (showExplanationSheet) {
        ExplainSentenceSheet(
            sentence = selectedTextForExplain,
            viewModel = viewModel,
            onDismiss = {
                showExplanationSheet = false
                viewModel.clearActiveExplanation()
            }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SubtitleSegmentCard(
    segment: SubtitleSegment,
    idx: Int,
    isActive: Boolean,
    currentPlaybackTime: Double,
    onCardClicked: () -> Unit
) {
    val containerBg = if (isActive) SleekActiveBg else SleekSurface
    val borderStroke = if (isActive) BorderStroke(2.dp, SleekPrimary) else BorderStroke(1.dp, SleekOutline)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClicked() },
        colors = CardDefaults.cardColors(containerColor = containerBg),
        shape = RoundedCornerShape(20.dp),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Build visual word chips matching Japanese studies layout
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = segment.chips ?: segment.text.map { ch ->
                    WordChip(text = ch.toString(), reading = null, romaji = null)
                }

                chips.forEachIndexed { wordIdx, wordChip ->
                    // Check if this word chip should be highlighted
                    val isChipActive = if (wordChip.start != null && wordChip.end != null) {
                        currentPlaybackTime >= wordChip.start && currentPlaybackTime < wordChip.end
                    } else if (isActive) {
                        // fallback progress interpolation across the whole segment duration
                        val totalWords = chips.size
                        val duration = segment.end - segment.start
                        if (duration > 0) {
                            val relativeProgress = (currentPlaybackTime - segment.start) / duration
                            val activeIdx = (relativeProgress * totalWords).toInt()
                            wordIdx == activeIdx.coerceIn(0, totalWords - 1)
                        } else false
                    } else false

                    WordChipItem(
                        chip = wordChip,
                        isActive = isChipActive
                    )
                }
            }

            // Translation block
            segment.translation?.let { trans ->
                if (trans.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = trans,
                        style = androidx.compose.ui.text.TextStyle(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 13.sp,
                            color = SleekTextSoft
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
fun WordChipItem(
    chip: WordChip,
    isActive: Boolean
) {
    val textMainColor = if (isActive) SleekPrimary else SleekTextMain
    val backgroundColor = if (isActive) SleekPrimaryContainer else Color.Transparent
    val borderStroke = if (isActive) BorderStroke(1.dp, SleekPrimary) else BorderStroke(0.1.dp, Color.Transparent)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(borderStroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Furigana
        Text(
            text = chip.reading ?: "",
            fontSize = 9.sp,
            color = if (isActive) SleekPrimary else SleekTextSoft,
            lineHeight = 9.sp,
            maxLines = 1
        )
        // Main text word
        Text(
            text = chip.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textMainColor,
            lineHeight = 17.sp
        )
        // Bottom Romaji
        Text(
            text = chip.romaji ?: "",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isActive) SleekPrimary else SleekTextSoft,
            lineHeight = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
fun PlaybackToolbar(
    viewModel: VibeViewModel,
    activeSegmentIndex: Int,
    onExplainRequest: (String) -> Unit
) {
    val transcript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val activeTime by viewModel.currentPlaybackTime.collectAsStateWithLifecycle()
    val playState by viewModel.isPlaying.collectAsStateWithLifecycle()
    val repeatState by viewModel.repeatSegmentActive.collectAsStateWithLifecycle()
    val speedRate by viewModel.playbackSpeed.collectAsStateWithLifecycle()

    val segments = transcript?.subtitles ?: emptyList()
    val activeSegment = if (activeSegmentIndex in segments.indices) segments[activeSegmentIndex] else null

    // Audio segment local repeating logic checks
    LaunchedEffect(activeTime, repeatState, activeSegment) {
        if (repeatState && activeSegment != null) {
            if (activeTime >= activeSegment.end) {
                // Seek back to start!
                viewModel.currentPlaybackTime.value = activeSegment.start
            }
        }
    }

    val overallStart = segments.firstOrNull()?.start ?: 0.0
    val overallEnd = segments.lastOrNull()?.end ?: 60.0

    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(BorderStroke(1.dp, SleekOutline), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = SleekNavBarBg,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Seek progress bar
            val totalDur = overallEnd - overallStart
            val scrollFraction = if (totalDur > 0) ((activeTime - overallStart) / totalDur).toFloat().coerceIn(0f, 1f) else 0f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatShortTime(activeTime),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SleekTextSoft
                )
                Slider(
                    value = scrollFraction,
                    onValueChange = { fraction ->
                        viewModel.currentPlaybackTime.value = overallStart + (fraction * totalDur)
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SleekPrimary,
                        activeTrackColor = SleekPrimary,
                        inactiveTrackColor = SleekOutline
                    )
                )
                Text(
                    text = formatShortTime(overallEnd),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SleekTextSoft
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Toolbar Rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Jelas feature (Dictionary analysis)
                IconButton(
                    onClick = {
                        activeSegment?.let { onExplainRequest(it.text) }
                    },
                    enabled = activeSegment != null
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = "Jelas",
                            tint = if (activeSegment != null) SleekPrimary else Color.Gray
                        )
                        Text("Jelas", fontSize = 8.sp, color = SleekTextSoft)
                    }
                }

                // 2. Loop/Repeat active segment option
                IconButton(onClick = { viewModel.repeatSegmentActive.value = !repeatState }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "Ulang",
                            tint = if (repeatState) SleekPrimary else Color.Gray
                        )
                        Text("Ulang", fontSize = 8.sp, color = SleekTextSoft)
                    }
                }

                // 3. Play / Pause Control Panel
                IconButton(
                    onClick = { viewModel.isPlaying.value = !playState },
                    modifier = Modifier
                        .background(SleekPrimaryContainer, RoundedCornerShape(14.dp))
                        .size(46.dp)
                ) {
                    Icon(
                        imageVector = if (playState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play Control",
                        tint = SleekNavy,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 4. Playback Speed Selector
                IconButton(onClick = {
                    val currentSpeed = speedRate
                    viewModel.playbackSpeed.value = when (currentSpeed) {
                        0.5f -> 0.75f
                        0.75f -> 1.0f
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        else -> 0.5f
                    }
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = SleekPrimary)
                        Text("${speedRate}x", fontSize = 8.sp, color = SleekTextSoft)
                    }
                }

                // 5. Unduh SRT File locally
                IconButton(onClick = {
                    transcript?.let { exportSrtToDownloads(context, it) }
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Download, contentDescription = "Srt file", tint = SleekPrimary)
                        Text("SRT", fontSize = 8.sp, color = SleekTextSoft)
                    }
                }
            }
        }
    }
}

@Composable
fun ExplainSentenceSheet(
    sentence: String,
    viewModel: VibeViewModel,
    onDismiss: () -> Unit
) {
    val loading by viewModel.explainLoading.collectAsStateWithLifecycle()
    val response by viewModel.explainResponse.collectAsStateWithLifecycle()
    val error by viewModel.explainError.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(BorderStroke(1.dp, SleekOutline), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            color = SleekSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SleekOutline)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Penjelasan Kalimat",
                        fontWeight = FontWeight.Bold,
                        color = SleekPrimary,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = SleekTextSoft)
                    }
                }

                HorizontalDivider(color = SleekOutline, modifier = Modifier.padding(vertical = 12.dp))

                if (loading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = SleekPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Menganalisis kalimat...", color = SleekTextSoft)
                        }
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(text = error ?: "Gagal memuat", color = AlertDanger, textAlign = TextAlign.Center)
                    }
                } else if (response != null) {
                    val parsed = response!!
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Terjemahan
                        Text("Terjemahan", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            text = parsed.translation ?: sentence,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = SleekTextMain),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Kosakata
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Kosakata", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold)
                        val vocabulary = parsed.vocabulary ?: emptyList()
                        if (vocabulary.isEmpty()) {
                            Text("Tidak ada list kosakata yang terdeteksi.", fontSize = 13.sp, color = SleekTextSoft)
                        } else {
                            vocabulary.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(1.dp, SleekOutline, RoundedCornerShape(12.dp))
                                        .background(SleekNavBarBg)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = item.word ?: "",
                                        color = SleekPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = item.reading ?: "", color = SleekTextSoft, fontSize = 11.sp)
                                        Text(text = item.meaning ?: "", color = SleekTextMain, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Tata Bahasa / Grammar
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tata Bahasa", fontSize = 12.sp, color = SleekPrimary, fontWeight = FontWeight.Bold)
                        val grammar = parsed.grammar ?: emptyList()
                        if (grammar.isEmpty()) {
                            Text("Tidak ada penjelasan tata bahasa khusus.", fontSize = 13.sp, color = SleekTextSoft)
                        } else {
                            grammar.forEach { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(1.dp, SleekOutline, RoundedCornerShape(12.dp))
                                        .background(SleekNavBarBg)
                                        .padding(10.dp)
                                ) {
                                    Text(text = item.pattern ?: "", color = SleekPrimary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = item.explanation ?: "", color = SleekTextMain, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    viewModel: VibeViewModel,
    onDismiss: () -> Unit
) {
    val serverUrlVal by viewModel.serverUrl.collectAsStateWithLifecycle()
    val providerSettings by viewModel.providerSettings.collectAsStateWithLifecycle()

    val providerVal by viewModel.llmProviderInput.collectAsStateWithLifecycle()
    val modelVal by viewModel.llmModelInput.collectAsStateWithLifecycle()
    val apiKeyVal by viewModel.llmApiKeyInput.collectAsStateWithLifecycle()

    var tempUrl by remember { mutableStateOf(serverUrlVal) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekOutline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Setelan Server & LLM", fontWeight = FontWeight.Bold, color = SleekPrimary, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = SleekTextSoft)
                    }
                }

                HorizontalDivider(color = SleekOutline)

                // Server URL Setup
                Text("VibeWhisper Server Terminal URL", fontSize = 12.sp, color = SleekTextSoft)
                OutlinedTextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.5:5000", color = SleekTextSoft) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextMain,
                        unfocusedTextColor = SleekTextMain,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.updateServerUrl(tempUrl)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimaryContainer, contentColor = SleekNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Koneksikan Server", fontWeight = FontWeight.Bold)
                }

                // LLM API preferences and defaults definitions
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SleekOutline)
                Text("Integrasi LLM (Provider & API Key)", color = SleekPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                // Select provider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LLM Provider", modifier = Modifier.weight(1f), color = SleekTextSoft, fontSize = 13.sp)
                    var expandedList by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedList = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekPrimary),
                            border = BorderStroke(1.dp, SleekOutline)
                        ) {
                            Text(providerVal.uppercase())
                            Icon(Icons.Filled.ArrowDropDown, "expand")
                        }
                        DropdownMenu(
                            expanded = expandedList, 
                            onDismissRequest = { expandedList = false }, 
                            modifier = Modifier.background(SleekSurface)
                        ) {
                            DropdownMenuItem(text = { Text("GEMINI", color = SleekTextMain) }, onClick = { viewModel.llmProviderInput.value = "gemini"; expandedList = false })
                            DropdownMenuItem(text = { Text("OPENAI", color = SleekTextMain) }, onClick = { viewModel.llmProviderInput.value = "openai"; expandedList = false })
                            DropdownMenuItem(text = { Text("CLAUDE", color = SleekTextMain) }, onClick = { viewModel.llmProviderInput.value = "anthropic"; expandedList = false })
                        }
                    }
                }

                // Model name input
                OutlinedTextField(
                    value = modelVal,
                    onValueChange = { viewModel.llmModelInput.value = it },
                    label = { Text("Nama Model", color = SleekTextSoft) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextMain,
                        unfocusedTextColor = SleekTextMain,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // API key input
                OutlinedTextField(
                    value = apiKeyVal,
                    onValueChange = { viewModel.llmApiKeyInput.value = it },
                    label = { Text("API Key", color = SleekTextSoft) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextMain,
                        unfocusedTextColor = SleekTextMain,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (providerSettings?.has_key == true) {
                    Text(
                        text = "Masked API Key saved: ${providerSettings?.api_key_masked}",
                        color = AlertSuccess,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, SleekOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextSoft),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            viewModel.saveServerSettings()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Simpan Setelan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SavedTranscriptsDialog(
    viewModel: VibeViewModel,
    onDismiss: () -> Unit
) {
    val locals by viewModel.localSavedList.collectAsStateWithLifecycle(initialValue = emptyList())
    val remotes by viewModel.serverTranscripts.collectAsStateWithLifecycle()

    var activeTabOffline by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekOutline),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📁 Daftar Transkrip", fontWeight = FontWeight.Bold, color = SleekPrimary, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = SleekTextSoft)
                    }
                }

                HorizontalDivider(color = SleekOutline, modifier = Modifier.padding(vertical = 12.dp))

                // Toggle tabs Online/Offline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SleekNavBarBg)
                        .border(1.dp, SleekOutline, RoundedCornerShape(12.dp))
                ) {
                    Button(
                        onClick = { activeTabOffline = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTabOffline) SleekPrimaryContainer else Color.Transparent,
                            contentColor = if (activeTabOffline) SleekPrimary else SleekTextSoft
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lokal (${locals.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            activeTabOffline = false
                            viewModel.refreshServerData()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!activeTabOffline) SleekPrimaryContainer else Color.Transparent,
                            contentColor = if (!activeTabOffline) SleekPrimary else SleekTextSoft
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Server (${remotes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (activeTabOffline) {
                        // OFF-LINE list
                        if (locals.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Belum ada transkrip offline saved.", color = SleekTextSoft, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(locals.size) { i ->
                                    val local = locals[i]
                                    TranscriptListRow(
                                        title = local.title,
                                        subInfo = "Lokal • ${local.subtitles.size} Baris • ${local.createdAt}",
                                        onSelect = {
                                            viewModel.selectTranscript(local.id)
                                            onDismiss()
                                        },
                                        onDelete = {
                                            viewModel.deleteTranscript(local.id)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // ON-LINE list from server
                        if (remotes.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tidak ada data transkripsi di server.", color = SleekTextSoft, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(remotes.size) { i ->
                                    val r = remotes[i]
                                    TranscriptListRow(
                                        title = r.title,
                                        subInfo = "Server • ${r.line_count} Baris • ${r.created_at}",
                                        onSelect = {
                                            viewModel.selectTranscript(r.id)
                                            onDismiss()
                                        },
                                        onDelete = {
                                            viewModel.deleteTranscript(r.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptListRow(
    title: String,
    subInfo: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SleekNavBarBg)
            .border(1.dp, SleekOutline, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SleekTextMain,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subInfo,
                color = SleekTextSoft,
                fontSize = 10.5.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AlertDanger, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun RawJsonImportDialog(
    viewModel: VibeViewModel,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var titleVal by remember { mutableStateOf("Import Subtitle Manual") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SleekSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SleekOutline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Impor JSON Subtitle", fontWeight = FontWeight.Bold, color = SleekPrimary, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Dialog", tint = SleekTextSoft)
                    }
                }

                HorizontalDivider(color = SleekOutline)

                Text(
                    text = "Konversi / Tempel kode JSON subtitle list langsung di bawah (Masing-masing elemen memiliki start, end, text, translation, chips).",
                    color = SleekTextSoft,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = titleVal,
                    onValueChange = { titleVal = it },
                    label = { Text("Nama Judul", color = SleekTextSoft) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextMain,
                        unfocusedTextColor = SleekTextMain,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("Tempel JSON list di sini...", color = SleekTextSoft, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SleekTextMain,
                        unfocusedTextColor = SleekTextMain,
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekOutline
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, SleekOutline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekTextSoft),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            viewModel.loadRawSubtitleJson(rawText, titleVal)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Impor Berkas", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- WebView-based YouTube Synchronizer ----------------
@Composable
fun YoutubeWebViewPlayer(
    videoId: String?,
    viewModel: VibeViewModel,
    modifier: Modifier = Modifier
) {
    if (videoId == null) return

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val speedRate by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val activeTime by viewModel.currentPlaybackTime.collectAsStateWithLifecycle()

    var cachedWebView by remember { mutableStateOf<WebView?>(null) }

    // Control playing and seeking externally from viewModel to WebView iframe player
    LaunchedEffect(isPlaying, speedRate) {
        cachedWebView?.let { webView ->
            if (isPlaying) {
                webView.evaluateJavascript("play();", null)
            } else {
                webView.evaluateJavascript("pause();", null)
            }
            webView.evaluateJavascript("setSpeed($speedRate);", null)
        }
    }

    // Capture manual seek trigger from slide bar
    LaunchedEffect(activeTime) {
        cachedWebView?.let { webView ->
            // Update time inside IFrame safely if difference is significant
            webView.evaluateJavascript("if(player && Math.abs(player.getCurrentTime() - $activeTime) > 1.2) { seekTo($activeTime); }", null)
        }
    }

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: black; overflow: hidden; }
            #ytplayer { width: 100%; height: 100%; }
          </style>
        </head>
        <body>
          <div id="ytplayer"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            var isReady = false;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('ytplayer', {
                videoId: '$videoId',
                playerVars: { rel: 0, modestbranding: 1, controls: 1, fs: 1 },
                events: {
                  'onReady': onPlayerReady,
                  'onStateChange': onPlayerStateChange
                }
              });
            }
            
            function onPlayerReady(event) {
              isReady = true;
              window.Android.onPlayerReady();
              
              // Emit constant updates
              setInterval(function() {
                if(player && typeof player.getCurrentTime === 'function') {
                  window.Android.onPlayTimeUpdate(player.getCurrentTime());
                }
              }, 200);
            }
            
            function onPlayerStateChange(event) {
              window.Android.onPlayerStateChange(event.data);
            }
            
            function seekTo(seconds) {
              if(player && isReady) {
                player.seekTo(seconds, true);
              }
            }
            
            function play() {
              if(player && isReady) player.playVideo();
            }
            
            function pause() {
              if(player && isReady) player.pauseVideo();
            }
            
            function setSpeed(rate) {
              if(player && isReady && player.setPlaybackRate) player.setPlaybackRate(rate);
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()

                // Register Javascript interface to bind with JVM
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onPlayerReady() {
                        // Webview complete ready callback
                    }

                    @JavascriptInterface
                    fun onPlayTimeUpdate(time: Double) {
                        // update play state timer flow (only if video is playing in viewmodel)
                        if (isPlaying) {
                            viewModel.currentPlaybackTime.value = time
                        }
                    }

                    @JavascriptInterface
                    fun onPlayerStateChange(state: Int) {
                        when (state) {
                            1 -> viewModel.isPlaying.value = true // playing
                            2 -> viewModel.isPlaying.value = false // paused
                        }
                    }
                }, "Android")

                loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
                cachedWebView = this
            }
        },
        update = { webView ->
            cachedWebView = webView
        }
    )
}

// ---------------- Offline / Local Player using VideoView ----------------
@Composable
fun LocalVideoPlayer(
    videoUriStr: String?,
    viewModel: VibeViewModel,
    modifier: Modifier = Modifier
) {
    if (videoUriStr == null) return
    val context = LocalContext.current

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activeTime by viewModel.currentPlaybackTime.collectAsStateWithLifecycle()
    val speedRate by viewModel.playbackSpeed.collectAsStateWithLifecycle()

    var cachedVideoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }

    // Start coordinates time updates
    LaunchedEffect(isPlaying, mediaPlayerInstance) {
        while (true) {
            delay(200)
            mediaPlayerInstance?.let { player ->
                if (player.isPlaying && isPlaying) {
                    viewModel.currentPlaybackTime.value = player.currentPosition.toDouble() / 1000.0
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        cachedVideoView?.let { video ->
            if (isPlaying) {
                if (!video.isPlaying) video.start()
            } else {
                if (video.isPlaying) video.pause()
            }
        }
    }

    LaunchedEffect(activeTime) {
        mediaPlayerInstance?.let { player ->
            val ms = (activeTime * 1000.0).toInt()
            if (Math.abs(player.currentPosition - ms) > 1300) {
                player.seekTo(ms)
            }
        }
    }

    LaunchedEffect(speedRate) {
        mediaPlayerInstance?.let { player ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    player.playbackParams = player.playbackParams.setSpeed(speedRate)
                }
            } catch (e: Exception) {
                // Ignore playback params failures on legacy devices
            }
        }
    }

    AndroidView(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse(videoUriStr))
                setOnPreparedListener { mp ->
                    mediaPlayerInstance = mp
                    mp.isLooping = false
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            mp.playbackParams = mp.playbackParams.setSpeed(speedRate)
                        }
                    } catch (e: Exception) {}

                    if (isPlaying) {
                        start()
                    }
                }
                setOnCompletionListener {
                    viewModel.isPlaying.value = false
                }
                cachedVideoView = this
            }
        },
        update = { videoView ->
            cachedVideoView = videoView
        }
    )
}

// Helpers
private fun formatShortTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%02d:%02d", mins, secs)
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            name = name?.substring(cut + 1)
        }
    }
    return name
}

private fun extractVideoId(url: String): String? {
    val patterns = listOf(
        "(?:youtube\\.com/watch\\?v=)([\\w-]{11})",
        "(?:youtu\\.be/)([\\w-]{11})",
        "(?:youtube\\.com/shorts/)([\\w-]{11})",
        "(?:youtube\\.com/embed/)([\\w-]{11})"
    )
    for (p in patterns) {
        val match = Regex(p).find(url)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    return null
}

// Convert model logs list representation into download standard SRT structures
fun exportSrtToDownloads(context: Context, transcript: SavedTranscript) {
    val srtContent = StringBuilder()
    transcript.subtitles.forEachIndexed { i, seg ->
        val index = i + 1
        val startFormatted = formatTimeSrt(seg.start)
        val endFormatted = formatTimeSrt(seg.end)
        srtContent.append("$index\n")
        srtContent.append("$startFormatted --> $endFormatted\n")
        srtContent.append("${seg.text}\n")
        if (!seg.translation.isNullOrEmpty()) {
            srtContent.append("${seg.translation}\n")
        }
        srtContent.append("\n")
    }

    try {
        val filename = "${transcript.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}_subtitle.srt"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(storageDir, filename)
        FileOutputStream(file).use { out ->
            out.write(srtContent.toString().toByteArray())
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Unduh Subtitles SRT")
            putExtra(Intent.EXTRA_TEXT, srtContent.toString())
        }
        context.startActivity(Intent.createChooser(shareIntent, "Save SRT"))
    } catch (e: Exception) {
        // Safe fail
    }
}

private fun formatTimeSrt(sec: Double): String {
    val h = (sec / 3600).toInt()
    val m = ((sec % 3600) / 60).toInt()
    val s = (sec % 60).toInt()
    val ms = ((sec - sec.toInt()) * 1000).toInt()
    return String.format("%02d:%02d:%02d,%03d", h, m, s, ms)
}
