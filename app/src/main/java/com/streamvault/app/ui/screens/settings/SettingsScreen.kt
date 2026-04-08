package com.streamvault.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.streamvault.app.ui.interaction.TvClickableSurface
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.interaction.TvIconButton

import com.streamvault.app.ui.components.dialogs.PinDialog
import com.streamvault.app.ui.components.dialogs.PremiumDialog
import com.streamvault.app.ui.components.dialogs.PremiumDialogActionButton
import com.streamvault.app.ui.components.dialogs.PremiumDialogFooterButton
import com.streamvault.app.ui.components.TvEmptyState
import com.streamvault.app.localization.localeForLanguageTag
import com.streamvault.app.localization.supportedAppLanguageTags
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import com.streamvault.app.ui.model.LiveTvChannelMode
import com.streamvault.app.ui.model.LiveTvQuickFilterVisibilityMode
import com.streamvault.app.ui.model.VodViewMode
import com.streamvault.app.ui.theme.*
import com.streamvault.domain.manager.BackupConflictStrategy
import com.streamvault.domain.model.Category
import com.streamvault.domain.model.CategorySortMode
import com.streamvault.domain.model.ChannelNumberingMode
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.DecoderMode
import com.streamvault.domain.model.ActiveLiveSource
import com.streamvault.domain.model.CombinedM3uProfile
import com.streamvault.domain.model.Provider
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.ProviderStatus
import com.streamvault.domain.model.RecordingFailureCategory
import com.streamvault.domain.model.RecordingItem
import com.streamvault.domain.model.RecordingRecurrence
import com.streamvault.domain.model.RecordingSourceType
import com.streamvault.domain.model.RecordingStatus
import com.streamvault.app.ui.screens.settings.ProviderDiagnosticsUiModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import com.streamvault.app.R
import com.streamvault.app.MainActivity
import com.streamvault.app.navigation.Routes
import java.util.Locale
import com.streamvault.app.BuildConfig
import com.streamvault.app.ui.screens.settings.AppUpdateUiModel
import com.streamvault.app.ui.interaction.mouseClickable
import java.text.DateFormat
import com.streamvault.app.ui.design.FocusSpec


@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onAddProvider: () -> Unit = {},
    onEditProvider: (Provider) -> Unit = {},
    onNavigateToParentalControl: (Long) -> Unit = {},
    currentRoute: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainActivity = context.findMainActivity()
    val appLanguageLabel = remember(uiState.appLanguage, context) {
        displayLanguageLabel(uiState.appLanguage, context.getString(R.string.settings_system_default))
    }
    val preferredAudioLanguageLabel = remember(uiState.preferredAudioLanguage, context) {
        displayLanguageLabel(uiState.preferredAudioLanguage, context.getString(R.string.settings_audio_language_auto))
    }
    val playbackSpeedLabel = remember(uiState.playerPlaybackSpeed) {
        formatPlaybackSpeedLabel(uiState.playerPlaybackSpeed)
    }
    val decoderModeLabel = remember(uiState.playerDecoderMode, context) {
        formatDecoderModeLabel(uiState.playerDecoderMode, context)
    }
    val controlsTimeoutLabel = remember(uiState.playerControlsTimeoutSeconds, context) {
        formatTimeoutSecondsLabel(uiState.playerControlsTimeoutSeconds, context)
    }
    val liveOverlayTimeoutLabel = remember(uiState.playerLiveOverlayTimeoutSeconds, context) {
        formatTimeoutSecondsLabel(uiState.playerLiveOverlayTimeoutSeconds, context)
    }
    val noticeTimeoutLabel = remember(uiState.playerNoticeTimeoutSeconds, context) {
        formatTimeoutSecondsLabel(uiState.playerNoticeTimeoutSeconds, context)
    }
    val diagnosticsTimeoutLabel = remember(uiState.playerDiagnosticsTimeoutSeconds, context) {
        formatTimeoutSecondsLabel(uiState.playerDiagnosticsTimeoutSeconds, context)
    }
    val subtitleSizeLabel = remember(uiState.subtitleTextScale, context) {
        formatSubtitleSizeLabel(uiState.subtitleTextScale, context)
    }
    val subtitleTextColorLabel = remember(uiState.subtitleTextColor, context) {
        formatSubtitleColorLabel(uiState.subtitleTextColor, subtitleTextColorOptions(context))
    }
    val subtitleBackgroundLabel = remember(uiState.subtitleBackgroundColor, context) {
        formatSubtitleColorLabel(uiState.subtitleBackgroundColor, subtitleBackgroundColorOptions(context))
    }
    val wifiQualityLabel = remember(uiState.wifiMaxVideoHeight, context) {
        formatQualityCapLabel(uiState.wifiMaxVideoHeight, context.getString(R.string.settings_quality_cap_auto))
    }
    val ethernetQualityLabel = remember(uiState.ethernetMaxVideoHeight, context) {
        formatQualityCapLabel(uiState.ethernetMaxVideoHeight, context.getString(R.string.settings_quality_cap_auto))
    }
    val lastSpeedTestLabel = remember(uiState.lastSpeedTest) {
        uiState.lastSpeedTest?.let(::formatSpeedTestValueLabel)
            ?: context.getString(R.string.settings_speed_test_not_run)
    }
    val lastSpeedTestSummary = remember(uiState.lastSpeedTest, context) {
        uiState.lastSpeedTest?.let { formatSpeedTestSummary(it, context) }
            ?: context.getString(R.string.settings_speed_test_summary_default)
    }
    val speedTestRecommendationLabel = remember(uiState.lastSpeedTest, context) {
        formatQualityCapLabel(
            uiState.lastSpeedTest?.recommendedMaxVideoHeight,
            context.getString(R.string.settings_quality_cap_auto)
        )
    }
    val protectionSummary = remember(uiState.parentalControlLevel, context) {
        when (uiState.parentalControlLevel) {
            0 -> context.getString(R.string.settings_level_off)
            1 -> context.getString(R.string.settings_level_locked)
            2 -> context.getString(R.string.settings_level_hidden)
            else -> context.getString(R.string.settings_level_unknown)
        }
    }

    // Parental Control State
    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    var showLevelDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveTvModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveTvQuickFilterVisibilityDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveChannelNumberingDialog by rememberSaveable { mutableStateOf(false) }
    var showVodViewModeDialog by rememberSaveable { mutableStateOf(false) }
    var showPlaybackSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showDecoderModeDialog by rememberSaveable { mutableStateOf(false) }
    var showControlsTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveOverlayTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showNoticeTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showLiveTvFiltersDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleSizeDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleTextColorDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleBackgroundDialog by rememberSaveable { mutableStateOf(false) }
    var showWifiQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showProviderSyncDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomProviderSyncDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateCombinedDialog by rememberSaveable { mutableStateOf(false) }
    var showAddCombinedMemberDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameCombinedDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCombinedProfileId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSyncProviderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customSyncSelections by rememberSaveable {
        mutableStateOf(
            setOf(
                ProviderSyncSelection.TV,
                ProviderSyncSelection.MOVIES,
                ProviderSyncSelection.EPG
            )
        )
    }
    var showEthernetQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showRecordingPatternDialog by rememberSaveable { mutableStateOf(false) }
    var showRecordingRetentionDialog by rememberSaveable { mutableStateOf(false) }
    var showRecordingConcurrencyDialog by rememberSaveable { mutableStateOf(false) }
    var categorySortDialogType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(0) }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<ParentalAction?>(null) }
    var pendingProtectionLevel by rememberSaveable { mutableStateOf<Int?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportConfig(it.toString()) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.inspectBackup(it.toString()) }
    }

    val recordingFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val displayName = DocumentFile.fromTreeUri(context, it)?.name
            viewModel.updateRecordingFolder(it.toString(), displayName)
        }
    }

    val uriHandler = LocalUriHandler.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppScreenScaffold(
            currentRoute = currentRoute,
            onNavigate = { if (!uiState.isSyncing) onNavigate(it) },
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_providers_subtitle),
            navigationChrome = AppNavigationChrome.TopBar,
            compactHeader = true,
            showScreenHeader = false
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Left navigation rail ──────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .width(236.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentPadding = PaddingValues(top = 76.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item { SettingsNavItem(stringResource(R.string.settings_providers), "P", Primary, selectedCategory == 0) { selectedCategory = 0 } }
                    item { SettingsNavItem(stringResource(R.string.settings_playback), ">", Color(0xFF9E8FFF), selectedCategory == 1) { selectedCategory = 1 } }
                    item { SettingsNavItem(stringResource(R.string.settings_privacy), "L", Color(0xFFFFB74D), selectedCategory == 2) { selectedCategory = 2 } }
                    item { SettingsNavItem(stringResource(R.string.settings_recording_title), "R", Color(0xFFEF5350), selectedCategory == 3) { selectedCategory = 3 } }
                    item { SettingsNavItem(stringResource(R.string.settings_backup_restore), "B", Color(0xFF42A5F5), selectedCategory == 4) { selectedCategory = 4 } }
                    item { SettingsNavItem("EPG Sources", "E", Color(0xFF66BB6A), selectedCategory == 5) { selectedCategory = 5; viewModel.loadEpgSources() } }
                    item { SettingsNavItem(stringResource(R.string.settings_about), "i", Color(0xFF78909C), selectedCategory == 6) { selectedCategory = 6 } }
                }

                // Thin vertical separator
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.07f))
                )

                // ── Right content pane ────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = 20.dp, top = 76.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = !uiState.isSyncing
                ) {
                    // ── 0: Providers ──────────────────────────────────────────
                    if (selectedCategory == 0) {
                        if (uiState.providers.isEmpty()) {
                            item {
                                TvEmptyState(
                                    title = stringResource(R.string.settings_no_providers),
                                    subtitle = stringResource(R.string.settings_no_providers_subtitle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
                                )
                            }
                        } else {
                            item {
                                var selectedProviderId by rememberSaveable(uiState.providers, uiState.activeProviderId) {
                                    mutableStateOf(uiState.activeProviderId ?: uiState.providers.first().id)
                                }
                                LaunchedEffect(uiState.providers, uiState.activeProviderId) {
                                    val availableIds = uiState.providers.map { it.id }.toSet()
                                    if (selectedProviderId !in availableIds) {
                                        selectedProviderId = uiState.activeProviderId ?: uiState.providers.first().id
                                    }
                                }
                                val selectedProvider = uiState.providers.firstOrNull { it.id == selectedProviderId }
                                    ?: uiState.providers.first()

                                Text(
                                    text = stringResource(R.string.settings_provider_selector_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 14.dp)
                                ) {
                                    items(uiState.providers, key = { it.id }) { provider ->
                                        ProviderSelectorTab(
                                            provider = provider,
                                            isSelected = provider.id == selectedProvider.id,
                                            isActive = provider.id == uiState.activeProviderId,
                                            onClick = { selectedProviderId = provider.id }
                                        )
                                    }
                                }
                                ProviderSettingsCard(
                                    provider = selectedProvider,
                                    isActive = selectedProvider.id == uiState.activeProviderId,
                                    isSyncing = uiState.isSyncing,
                                    diagnostics = uiState.diagnosticsByProvider[selectedProvider.id],
                                    syncWarnings = uiState.syncWarningsByProvider[selectedProvider.id].orEmpty(),
                                    onRetryWarningAction = { action -> viewModel.retryWarningAction(selectedProvider.id, action) },
                                    onConnect = { viewModel.setActiveProvider(selectedProvider.id) },
                                    onRefresh = {
                                        pendingSyncProviderId = selectedProvider.id
                                        customSyncSelections = buildSet {
                                            add(ProviderSyncSelection.TV)
                                            add(ProviderSyncSelection.MOVIES)
                                            add(ProviderSyncSelection.EPG)
                                            if (selectedProvider.type == ProviderType.XTREAM_CODES) {
                                                add(ProviderSyncSelection.SERIES)
                                            }
                                        }
                                        showProviderSyncDialog = true
                                    },
                                    onDelete = { viewModel.deleteProvider(selectedProvider.id) },
                                    onEdit = { onEditProvider(selectedProvider) },
                                    onParentalControl = { onNavigateToParentalControl(selectedProvider.id) },
                                    onToggleM3uVodClassification = { enabled ->
                                        viewModel.setM3uVodClassificationEnabled(selectedProvider.id, enabled)
                                    },
                                    onRefreshM3uClassification = {
                                        viewModel.refreshProviderClassification(selectedProvider.id)
                                    }
                                )

                                Spacer(modifier = Modifier.height(18.dp))
                                CombinedM3uProfilesCard(
                                    profiles = uiState.combinedProfiles,
                                    availableProviders = uiState.availableM3uProviders,
                                    selectedProfileId = selectedCombinedProfileId,
                                    activeLiveSource = uiState.activeLiveSource,
                                    onSelectProfile = { selectedCombinedProfileId = it },
                                    onCreateProfile = { showCreateCombinedDialog = true },
                                    onActivateProfile = { profileId -> viewModel.setActiveCombinedProfile(profileId) },
                                    onDeleteProfile = { profileId ->
                                        if (selectedCombinedProfileId == profileId) {
                                            selectedCombinedProfileId = null
                                        }
                                        viewModel.deleteCombinedProfile(profileId)
                                    },
                                    onRenameProfile = { profileId ->
                                        selectedCombinedProfileId = profileId
                                        showRenameCombinedDialog = true
                                    },
                                    onAddProvider = { profileId ->
                                        selectedCombinedProfileId = profileId
                                        showAddCombinedMemberDialog = true
                                    },
                                    onRemoveProvider = { profileId, providerId ->
                                        viewModel.removeProviderFromCombinedProfile(profileId, providerId)
                                    },
                                    onToggleProviderEnabled = { profileId, providerId, enabled ->
                                        viewModel.setCombinedProviderEnabled(profileId, providerId, enabled)
                                    },
                                    onMoveProvider = { profileId, providerId, moveUp ->
                                        viewModel.moveCombinedProvider(profileId, providerId, moveUp)
                                    }
                                )
                            }
                        }
                        item {
                            TvClickableSurface(
                                onClick = onAddProvider,
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Primary.copy(alpha = 0.15f),
                                    focusedContainerColor = Primary.copy(alpha = 0.3f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_add_provider),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }

                    // ── 1: Playback ───────────────────────────────────────────
                    else if (selectedCategory == 1) {
                        item {
                            TvClickableSurface(
                                onClick = { viewModel.setPreventStandbyDuringPlayback(!uiState.preventStandbyDuringPlayback) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_prevent_standby), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_prevent_standby_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Switch(checked = uiState.preventStandbyDuringPlayback, onCheckedChange = { viewModel.setPreventStandbyDuringPlayback(it) })
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            TvClickableSurface(
                                onClick = { viewModel.setPlayerMediaSessionEnabled(!uiState.playerMediaSessionEnabled) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_media_session), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_media_session_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Switch(checked = uiState.playerMediaSessionEnabled, onCheckedChange = { viewModel.setPlayerMediaSessionEnabled(it) })
                                }
                            }
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_decoder_mode),
                                value = decoderModeLabel,
                                onClick = { showDecoderModeDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_live_tv_channel_mode),
                                value = stringResource(uiState.liveTvChannelMode.labelResId()),
                                onClick = { showLiveTvModeDialog = true }
                            )
                            TvClickableSurface(
                                onClick = { viewModel.setShowLiveSourceSwitcher(!uiState.showLiveSourceSwitcher) },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_show_live_source_switcher), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_show_live_source_switcher_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Switch(checked = uiState.showLiveSourceSwitcher, onCheckedChange = { viewModel.setShowLiveSourceSwitcher(it) })
                                }
                            }
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_live_tv_quick_filters),
                                value = formatLiveTvQuickFiltersValue(uiState.liveTvCategoryFilters, context),
                                onClick = { showLiveTvFiltersDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_live_tv_quick_filter_visibility),
                                value = stringResource(uiState.liveTvQuickFilterVisibilityMode.labelResId()),
                                onClick = { showLiveTvQuickFilterVisibilityDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_live_channel_numbering_mode),
                                value = stringResource(uiState.liveChannelNumberingMode.labelResId()),
                                onClick = { showLiveChannelNumberingDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_vod_view_mode),
                                value = stringResource(uiState.vodViewMode.labelResId()),
                                onClick = { showVodViewModeDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_category_sort_live),
                                value = formatCategorySortModeLabel(uiState.categorySortModes[ContentType.LIVE] ?: CategorySortMode.DEFAULT, context),
                                onClick = { categorySortDialogType = ContentType.LIVE.name }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_category_sort_movies),
                                value = formatCategorySortModeLabel(uiState.categorySortModes[ContentType.MOVIE] ?: CategorySortMode.DEFAULT, context),
                                onClick = { categorySortDialogType = ContentType.MOVIE.name }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_category_sort_series),
                                value = formatCategorySortModeLabel(uiState.categorySortModes[ContentType.SERIES] ?: CategorySortMode.DEFAULT, context),
                                onClick = { categorySortDialogType = ContentType.SERIES.name }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            TvClickableSurface(
                                onClick = { showLanguageDialog = true },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = stringResource(R.string.settings_app_language), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                    Text(text = appLanguageLabel, style = MaterialTheme.typography.bodyMedium, color = Primary)
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_default_playback_speed),
                                value = playbackSpeedLabel,
                                onClick = { showPlaybackSpeedDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_player_controls_timeout),
                                value = controlsTimeoutLabel,
                                onClick = { showControlsTimeoutDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_live_overlay_timeout),
                                value = liveOverlayTimeoutLabel,
                                onClick = { showLiveOverlayTimeoutDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_player_notice_timeout),
                                value = noticeTimeoutLabel,
                                onClick = { showNoticeTimeoutDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_player_diagnostics_timeout),
                                value = diagnosticsTimeoutLabel,
                                onClick = { showDiagnosticsTimeoutDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_preferred_audio_language),
                                value = preferredAudioLanguageLabel,
                                onClick = { showAudioLanguageDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_subtitle_size),
                                value = subtitleSizeLabel,
                                onClick = { showSubtitleSizeDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_subtitle_text_color),
                                value = subtitleTextColorLabel,
                                onClick = { showSubtitleTextColorDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_subtitle_background),
                                value = subtitleBackgroundLabel,
                                onClick = { showSubtitleBackgroundDialog = true }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_wifi_quality_cap),
                                value = wifiQualityLabel,
                                onClick = { showWifiQualityDialog = true }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_ethernet_quality_cap),
                                value = ethernetQualityLabel,
                                onClick = { showEthernetQualityDialog = true }
                            )
                        }
                        item {
                            InternetSpeedTestCard(
                                valueLabel = lastSpeedTestLabel,
                                summary = lastSpeedTestSummary,
                                recommendationLabel = speedTestRecommendationLabel,
                                isRunning = uiState.isRunningInternetSpeedTest,
                                canApplyRecommendation = uiState.lastSpeedTest != null,
                                onRunTest = viewModel::runInternetSpeedTest,
                                onApplyWifi = viewModel::applySpeedTestRecommendationToWifi,
                                onApplyEthernet = viewModel::applySpeedTestRecommendationToEthernet
                            )
                        }
                    }

                    // ── 2: Privacy & Parental ─────────────────────────────────
                    else if (selectedCategory == 2) {
                        item {
                            ParentalControlCard(
                                level = uiState.parentalControlLevel,
                                hasParentalPin = uiState.hasParentalPin,
                                hasActiveProvider = uiState.activeProviderId != null,
                                onChangeLevel = {
                                    pendingProtectionLevel = null
                                    if (uiState.hasParentalPin) {
                                        pendingAction = ParentalAction.ChangeLevel
                                        showPinDialog = true
                                    } else {
                                        showLevelDialog = true
                                    }
                                },
                                onChangePin = {
                                    pendingProtectionLevel = null
                                    pendingAction = if (uiState.hasParentalPin) {
                                        ParentalAction.ChangePin
                                    } else {
                                        ParentalAction.SetNewPin
                                    }
                                    showPinDialog = true
                                }
                            )
                        }
                        item {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
                            TvClickableSurface(
                                onClick = { viewModel.toggleIncognitoMode() },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_incognito_mode), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_incognito_mode_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Switch(checked = uiState.isIncognitoMode, onCheckedChange = { viewModel.toggleIncognitoMode() })
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            TvClickableSurface(
                                onClick = { viewModel.toggleXtreamTextClassification() },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_xtream_text_classification), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_xtream_text_classification_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Switch(checked = uiState.useXtreamTextClassification, onCheckedChange = { viewModel.toggleXtreamTextClassification() })
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            TvClickableSurface(
                                onClick = { showClearHistoryDialog = true },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Primary.copy(alpha = 0.15f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                        Text(text = stringResource(R.string.settings_clear_history_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                                    }
                                    Text(text = stringResource(R.string.settings_clear_history_confirm), style = MaterialTheme.typography.labelLarge, color = Primary)
                                }
                            }
                        }
                    }

                    // ── 3: Recordings ─────────────────────────────────────────
                    else if (selectedCategory == 3) {
                        item {
                            RecordingOverviewCard(
                                treeLabel = uiState.recordingStorageState.displayName,
                                outputDirectory = uiState.recordingStorageState.outputDirectory,
                                availableBytes = uiState.recordingStorageState.availableBytes,
                                isWritable = uiState.recordingStorageState.isWritable,
                                activeCount = uiState.recordingItems.count { it.status == RecordingStatus.RECORDING },
                                scheduledCount = uiState.recordingItems.count { it.status == RecordingStatus.SCHEDULED },
                                fileNamePattern = uiState.recordingStorageState.fileNamePattern,
                                retentionDays = uiState.recordingStorageState.retentionDays,
                                maxSimultaneousRecordings = uiState.recordingStorageState.maxSimultaneousRecordings,
                                onChooseFolder = { recordingFolderLauncher.launch(null) },
                                onUseAppStorage = { viewModel.updateRecordingFolder(null, null) },
                                onChangePattern = { showRecordingPatternDialog = true },
                                onChangeRetention = { showRecordingRetentionDialog = true },
                                onChangeConcurrency = { showRecordingConcurrencyDialog = true },
                                onRepairSchedule = { viewModel.reconcileRecordings() }
                            )
                        }
                        if (uiState.recordingItems.isEmpty()) {
                            item {
                                TvClickableSurface(
                                    onClick = {},
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        focusedContainerColor = Primary.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                                ) {
                                    TvEmptyState(
                                        title = stringResource(R.string.settings_recording_empty_title),
                                        subtitle = stringResource(R.string.settings_recording_empty_subtitle)
                                    )
                                }
                            }
                        } else {
                            items(uiState.recordingItems, key = { it.id }) { item ->
                                RecordingItemCard(
                                    item = item,
                                    onPlay = {
                                        val playbackUrl = item.playbackUrl()
                                        if (!playbackUrl.isNullOrBlank()) {
                                            mainActivity?.openPlayer(
                                                Routes.player(
                                                    streamUrl = playbackUrl,
                                                    title = item.programTitle ?: item.channelName,
                                                    internalId = item.channelId,
                                                    providerId = item.providerId,
                                                    contentType = "MOVIE",
                                                    returnRoute = currentRoute
                                                )
                                            )
                                        }
                                    },
                                    onStop = { viewModel.stopRecording(item.id) },
                                    onCancel = { viewModel.cancelRecording(item.id) },
                                    onDelete = { viewModel.deleteRecording(item.id) },
                                    onRetry = { viewModel.retryRecording(item.id) },
                                    onToggleSchedule = { enabled -> viewModel.setRecordingScheduleEnabled(item.id, enabled) }
                                )
                            }
                        }
                    }

                    // ── 4: Backup ─────────────────────────────────────────────
                    else if (selectedCategory == 4) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TvClickableSurface(
                                    onClick = { createDocumentLauncher.launch("streamvault_backup.json") },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Primary.copy(alpha = 0.12f),
                                        focusedContainerColor = Primary.copy(alpha = 0.28f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "\u2191", style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
                                        Text(text = stringResource(R.string.settings_backup_data), style = MaterialTheme.typography.titleSmall, color = Primary, textAlign = TextAlign.Center)
                                        Text(text = stringResource(R.string.settings_backup_subtitle), style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, textAlign = TextAlign.Center)
                                    }
                                }
                                TvClickableSurface(
                                    onClick = { openDocumentLauncher.launch(arrayOf("application/json")) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Secondary.copy(alpha = 0.12f),
                                        focusedContainerColor = Secondary.copy(alpha = 0.28f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = "\u2193", style = MaterialTheme.typography.titleLarge, color = Secondary, fontWeight = FontWeight.Bold)
                                        Text(text = stringResource(R.string.settings_restore_data), style = MaterialTheme.typography.titleSmall, color = Secondary, textAlign = TextAlign.Center)
                                        Text(text = stringResource(R.string.settings_backup_subtitle), style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }

                    // ── 5: EPG Sources ───────────────────────────────────────
                    else if (selectedCategory == 5) {
                        val epgSources = uiState.epgSources
                        val providers = uiState.providers

                        item {
                            Text(
                                text = "EPG Sources",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF66BB6A),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Add external XMLTV EPG sources and assign them to providers. External sources are matched to channels by ID or name and override provider-native EPG data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        // Add source form
                        item {
                            var newName by remember { mutableStateOf("") }
                            var newUrl by remember { mutableStateOf("") }
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val filePickerLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.OpenDocument()
                            ) { uri ->
                                if (uri != null) {
                                    try {
                                        ctx.contentResolver.takePersistableUriPermission(
                                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (_: SecurityException) { /* provider does not offer persistable permission */ }
                                    newUrl = uri.toString()
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Add EPG Source", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    EpgSourceTextField(value = newName, onValueChange = { newName = it }, placeholder = "Source name")
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            EpgSourceTextField(
                                                value = newUrl,
                                                onValueChange = { newUrl = it },
                                                placeholder = "XMLTV URL (HTTPS) or browse file"
                                            )
                                        }
                                        TvClickableSurface(
                                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = Primary.copy(alpha = 0.15f),
                                                focusedContainerColor = Primary.copy(alpha = 0.3f)
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                        ) {
                                            Text("Browse", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium, color = Primary)
                                        }
                                    }
                                    TvClickableSurface(
                                        onClick = {
                                            if (newName.isNotBlank() && newUrl.isNotBlank()) {
                                                viewModel.addEpgSource(newName.trim(), newUrl.trim())
                                                newName = ""
                                                newUrl = ""
                                            }
                                        },
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFF66BB6A).copy(alpha = 0.2f),
                                            focusedContainerColor = Color(0xFF66BB6A).copy(alpha = 0.4f)
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                    ) {
                                        Text("Add Source", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFF66BB6A))
                                    }
                                }
                            }
                        }

                        // Source list
                        if (epgSources.isEmpty()) {
                            item {
                                Text(
                                    text = "No external EPG sources configured. Add a source above to get started.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceDim,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        } else {
                            items(epgSources.size) { index ->
                                val source = epgSources[index]
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(source.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                                Text(displayableEpgUrl(source.url), style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim, maxLines = 1)
                                                if (source.lastError != null) {
                                                    Text("Error: ${source.lastError}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF5350))
                                                }
                                                if (source.lastSuccessAt != null) {
                                                    val ago = (System.currentTimeMillis() - source.lastSuccessAt) / 60000
                                                    Text("Last synced: ${ago}m ago", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TvClickableSurface(
                                                    onClick = { viewModel.toggleEpgSourceEnabled(source.id, !source.enabled) },
                                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                    colors = ClickableSurfaceDefaults.colors(
                                                        containerColor = if (source.enabled) Color(0xFF66BB6A).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                                        focusedContainerColor = if (source.enabled) Color(0xFF66BB6A).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f)
                                                    ),
                                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                ) {
                                                    Text(
                                                        if (source.enabled) "ON" else "OFF",
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (source.enabled) Color(0xFF66BB6A) else OnSurfaceDim
                                                    )
                                                }
                                                TvClickableSurface(
                                                    onClick = { viewModel.refreshEpgSource(source.id) },
                                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                    colors = ClickableSurfaceDefaults.colors(
                                                        containerColor = Primary.copy(alpha = 0.15f),
                                                        focusedContainerColor = Primary.copy(alpha = 0.3f)
                                                    ),
                                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                ) {
                                                    Text("Refresh", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
                                                }
                                                TvClickableSurface(
                                                    onClick = { viewModel.deleteEpgSource(source.id) },
                                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                    colors = ClickableSurfaceDefaults.colors(
                                                        containerColor = Color(0xFFEF5350).copy(alpha = 0.12f),
                                                        focusedContainerColor = Color(0xFFEF5350).copy(alpha = 0.25f)
                                                    ),
                                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                ) {
                                                    Text("Delete", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Provider assignment section
                        if (providers.isNotEmpty() && epgSources.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Provider Assignments",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF66BB6A),
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                                )
                                Text(
                                    text = "Assign EPG sources to providers. Channels will be matched automatically by ID or name.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            items(providers.size) { providerIndex ->
                                val provider = providers[providerIndex]
                                val assignments = uiState.epgSourceAssignments[provider.id].orEmpty()
                                val resolutionSummary = uiState.epgResolutionSummaries[provider.id]
                                val assignedSourceIds = assignments.map { it.epgSourceId }.toSet()
                                val unassignedSources = epgSources.filter { it.id !in assignedSourceIds }

                                LaunchedEffect(provider.id) {
                                    viewModel.loadEpgAssignments(provider.id)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(provider.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        if (resolutionSummary != null) {
                                            val matchedChannels = (resolutionSummary.totalChannels - resolutionSummary.unresolvedChannels).coerceAtLeast(0)
                                            Text(
                                                text = "Matched $matchedChannels/${resolutionSummary.totalChannels} channels • ${resolutionSummary.exactIdMatches} exact • ${resolutionSummary.normalizedNameMatches} name • ${resolutionSummary.providerNativeMatches} provider",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OnSurfaceDim
                                            )
                                        }

                                        if (assignments.isEmpty()) {
                                            Text("No EPG sources assigned", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                                        } else {
                                            assignments.sortedBy { it.priority }.forEachIndexed { assignmentIndex, assignment ->
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        "${assignment.epgSourceName} (priority: ${assignment.priority})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        TvClickableSurface(
                                                            onClick = { viewModel.moveEpgSourceAssignmentUp(provider.id, assignment.epgSourceId) },
                                                            enabled = assignmentIndex > 0,
                                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                                            colors = ClickableSurfaceDefaults.colors(
                                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                                focusedContainerColor = Color.White.copy(alpha = 0.16f),
                                                                disabledContainerColor = Color.White.copy(alpha = 0.04f)
                                                            ),
                                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                        ) {
                                                            Text("Up", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                                        }
                                                        TvClickableSurface(
                                                            onClick = { viewModel.moveEpgSourceAssignmentDown(provider.id, assignment.epgSourceId) },
                                                            enabled = assignmentIndex < assignments.lastIndex,
                                                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                                            colors = ClickableSurfaceDefaults.colors(
                                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                                focusedContainerColor = Color.White.copy(alpha = 0.16f),
                                                                disabledContainerColor = Color.White.copy(alpha = 0.04f)
                                                            ),
                                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                        ) {
                                                            Text("Down", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    TvClickableSurface(
                                                        onClick = { viewModel.unassignEpgSourceFromProvider(provider.id, assignment.epgSourceId) },
                                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                                        colors = ClickableSurfaceDefaults.colors(
                                                            containerColor = Color(0xFFEF5350).copy(alpha = 0.12f),
                                                            focusedContainerColor = Color(0xFFEF5350).copy(alpha = 0.25f)
                                                        ),
                                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                    ) {
                                                        Text("Remove", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF5350))
                                                    }
                                                }
                                            }
                                        }

                                        if (unassignedSources.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                unassignedSources.forEach { source ->
                                                    TvClickableSurface(
                                                        onClick = { viewModel.assignEpgSourceToProvider(provider.id, source.id) },
                                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                        colors = ClickableSurfaceDefaults.colors(
                                                            containerColor = Color(0xFF66BB6A).copy(alpha = 0.12f),
                                                            focusedContainerColor = Color(0xFF66BB6A).copy(alpha = 0.25f)
                                                        ),
                                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                                                    ) {
                                                        Text("+ ${source.name}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 6: About ──────────────────────────────────────────────
                    else {
                        item {
                            SettingsSectionHeader(
                                title = stringResource(R.string.settings_updates_title),
                                subtitle = stringResource(R.string.settings_updates_subtitle)
                            )
                            SettingsRow(label = stringResource(R.string.settings_app_version), value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                            SwitchSettingsRow(
                                label = stringResource(R.string.settings_update_auto_check),
                                value = stringResource(
                                    if (uiState.autoCheckAppUpdates) R.string.settings_enabled else R.string.settings_disabled
                                ),
                                checked = uiState.autoCheckAppUpdates,
                                onCheckedChange = viewModel::setAutoCheckAppUpdates
                            )
                            SettingsRow(
                                label = stringResource(R.string.settings_update_latest_release),
                                value = formatLatestReleaseLabel(uiState.appUpdate, context)
                            )
                            SettingsRow(
                                label = stringResource(R.string.settings_update_status),
                                value = formatUpdateStatusLabel(uiState.appUpdate, context)
                            )
                            SettingsRow(
                                label = stringResource(R.string.settings_update_last_checked),
                                value = formatUpdateCheckTimeLabel(uiState.appUpdate.lastCheckedAt, context)
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_update_check_now),
                                value = stringResource(
                                    if (uiState.isCheckingForUpdates) R.string.settings_update_checking else R.string.settings_update_check_action
                                ),
                                onClick = {
                                    if (!uiState.isCheckingForUpdates) {
                                        viewModel.checkForAppUpdates()
                                    }
                                }
                            )
                            if (shouldShowUpdateDownloadAction(uiState.appUpdate)) {
                                ClickableSettingsRow(
                                    label = stringResource(R.string.settings_update_download),
                                    value = formatUpdateDownloadLabel(uiState.appUpdate, context),
                                    onClick = {
                                        if (uiState.appUpdate.downloadStatus == com.streamvault.app.update.AppUpdateDownloadStatus.Downloaded) {
                                            viewModel.installDownloadedUpdate()
                                        } else if (uiState.appUpdate.downloadStatus != com.streamvault.app.update.AppUpdateDownloadStatus.Downloading) {
                                            viewModel.downloadLatestUpdate()
                                        }
                                    }
                                )
                            }
                            if (!uiState.appUpdate.releaseUrl.isNullOrBlank()) {
                                ClickableSettingsRow(
                                    label = stringResource(R.string.settings_update_view_release),
                                    value = uiState.appUpdate.latestVersionName ?: stringResource(R.string.settings_update_release_notes),
                                    onClick = { uriHandler.openUri(uiState.appUpdate.releaseUrl.orEmpty()) }
                                )
                            }
                            if (!uiState.appUpdate.errorMessage.isNullOrBlank()) {
                                SettingsRow(
                                    label = stringResource(R.string.settings_update_error),
                                    value = uiState.appUpdate.errorMessage.orEmpty()
                                )
                            }
                        }

                        item {
                            SettingsRow(label = stringResource(R.string.settings_build), value = stringResource(R.string.settings_build_desc))
                            SettingsRow(label = stringResource(R.string.settings_developed_by), value = stringResource(R.string.settings_developer_name))
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_github),
                                value = stringResource(R.string.settings_github_url),
                                onClick = { uriHandler.openUri(context.getString(R.string.settings_github_url)) }
                            )
                            ClickableSettingsRow(
                                label = stringResource(R.string.settings_donate),
                                value = stringResource(R.string.settings_donate_url),
                                onClick = { uriHandler.openUri(context.getString(R.string.settings_donate_url)) }
                            )
                        }
                    }
                }
            }
        }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )

        // Blocking Loading Overlay
        if (uiState.isSyncing) {
            // Block Back Press
            androidx.activity.compose.BackHandler(enabled = true) {
                // Do nothing, effectively blocking back
            }

            val overlayFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { overlayFocusRequester.requestFocus() }

            // Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = true, onClick = {}) // Consume clicks
                    .focusRequester(overlayFocusRequester)
                    .focusable()
                    .onKeyEvent { true } // Consume all d-pad/key events
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Primary)
                    Text(
                        text = stringResource(R.string.settings_syncing_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_syncing_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
            }
        }


        if (showLiveTvModeDialog) {
            LiveTvChannelModeDialog(
                selectedMode = uiState.liveTvChannelMode,
                onDismiss = { showLiveTvModeDialog = false },
                onModeSelected = { mode ->
                    viewModel.setLiveTvChannelMode(mode)
                    showLiveTvModeDialog = false
                }
            )
        }

        if (showLiveTvQuickFilterVisibilityDialog) {
            LiveTvQuickFilterVisibilityDialog(
                selectedMode = uiState.liveTvQuickFilterVisibilityMode,
                onDismiss = { showLiveTvQuickFilterVisibilityDialog = false },
                onModeSelected = { mode ->
                    viewModel.setLiveTvQuickFilterVisibilityMode(mode)
                    showLiveTvQuickFilterVisibilityDialog = false
                }
            )
        }

        if (showLiveChannelNumberingDialog) {
            LiveChannelNumberingModeDialog(
                selectedMode = uiState.liveChannelNumberingMode,
                onDismiss = { showLiveChannelNumberingDialog = false },
                onModeSelected = { mode ->
                    viewModel.setLiveChannelNumberingMode(mode)
                    showLiveChannelNumberingDialog = false
                }
            )
        }

        if (showVodViewModeDialog) {
            VodViewModeDialog(
                selectedMode = uiState.vodViewMode,
                onDismiss = { showVodViewModeDialog = false },
                onModeSelected = { mode ->
                    viewModel.setVodViewMode(mode)
                    showVodViewModeDialog = false
                }
            )
        }

        if (showPlaybackSpeedDialog) {
            val speedOptions = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_playback_speed),
                onDismiss = { showPlaybackSpeedDialog = false }
            ) {
                speedOptions.forEachIndexed { index, speed ->
                    LevelOption(
                        level = index,
                        text = formatPlaybackSpeedLabel(speed),
                        currentLevel = if (speed == uiState.playerPlaybackSpeed) index else -1,
                        onSelect = {
                            viewModel.setDefaultPlaybackSpeed(speed)
                            showPlaybackSpeedDialog = false
                        }
                    )
                }
            }
        }

        if (showDecoderModeDialog) {
            val decoderOptions = remember(context) {
                listOf(
                    DecoderMode.AUTO to context.getString(R.string.settings_decoder_auto),
                    DecoderMode.HARDWARE to context.getString(R.string.settings_decoder_hardware),
                    DecoderMode.SOFTWARE to context.getString(R.string.settings_decoder_software)
                )
            }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_decoder_mode),
                onDismiss = { showDecoderModeDialog = false }
            ) {
                decoderOptions.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.second,
                        currentLevel = if (uiState.playerDecoderMode == option.first) index else -1,
                        onSelect = {
                            viewModel.setPlayerDecoderMode(option.first)
                            showDecoderModeDialog = false
                        }
                    )
                }
            }
        }

        if (showControlsTimeoutDialog) {
            TimeoutValueDialog(
                title = stringResource(R.string.settings_player_controls_timeout),
                subtitle = stringResource(R.string.settings_timeout_vod_controls_subtitle),
                initialValue = uiState.playerControlsTimeoutSeconds,
                onDismiss = { showControlsTimeoutDialog = false }
            ) { seconds ->
                viewModel.setPlayerControlsTimeoutSeconds(seconds)
                showControlsTimeoutDialog = false
            }
        }

        if (showLiveOverlayTimeoutDialog) {
            TimeoutValueDialog(
                title = stringResource(R.string.settings_live_overlay_timeout),
                subtitle = stringResource(R.string.settings_timeout_live_overlays_subtitle),
                initialValue = uiState.playerLiveOverlayTimeoutSeconds,
                onDismiss = { showLiveOverlayTimeoutDialog = false }
            ) { seconds ->
                viewModel.setPlayerLiveOverlayTimeoutSeconds(seconds)
                showLiveOverlayTimeoutDialog = false
            }
        }

        if (showNoticeTimeoutDialog) {
            TimeoutValueDialog(
                title = stringResource(R.string.settings_player_notice_timeout),
                subtitle = stringResource(R.string.settings_timeout_notices_subtitle),
                initialValue = uiState.playerNoticeTimeoutSeconds,
                onDismiss = { showNoticeTimeoutDialog = false }
            ) { seconds ->
                viewModel.setPlayerNoticeTimeoutSeconds(seconds)
                showNoticeTimeoutDialog = false
            }
        }

        if (showDiagnosticsTimeoutDialog) {
            TimeoutValueDialog(
                title = stringResource(R.string.settings_player_diagnostics_timeout),
                subtitle = stringResource(R.string.settings_timeout_diagnostics_subtitle),
                initialValue = uiState.playerDiagnosticsTimeoutSeconds,
                onDismiss = { showDiagnosticsTimeoutDialog = false }
            ) { seconds ->
                viewModel.setPlayerDiagnosticsTimeoutSeconds(seconds)
                showDiagnosticsTimeoutDialog = false
            }
        }

        if (showLiveTvFiltersDialog) {
            LiveTvQuickFiltersDialog(
                filters = uiState.liveTvCategoryFilters,
                onDismiss = { showLiveTvFiltersDialog = false },
                onAddFilter = viewModel::addLiveTvCategoryFilter,
                onRemoveFilter = viewModel::removeLiveTvCategoryFilter
            )
        }

        if (showAudioLanguageDialog) {
            val autoLabel = stringResource(R.string.settings_audio_language_auto)
            val audioLanguageOptions = remember(autoLabel) { supportedAudioLanguages(autoLabel) }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_audio_language),
                onDismiss = { showAudioLanguageDialog = false }
            ) {
                audioLanguageOptions.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.label,
                        currentLevel = if (uiState.preferredAudioLanguage == option.tag) index else -1,
                        onSelect = {
                            viewModel.setPreferredAudioLanguage(option.tag)
                            showAudioLanguageDialog = false
                        }
                    )
                }
            }
        }

        if (showSubtitleSizeDialog) {
            val subtitleSizeOptions = remember { subtitleSizeOptions() }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_subtitle_size),
                onDismiss = { showSubtitleSizeDialog = false }
            ) {
                subtitleSizeOptions.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.label(context),
                        currentLevel = if (uiState.subtitleTextScale == option.scale) index else -1,
                        onSelect = {
                            viewModel.setSubtitleTextScale(option.scale)
                            showSubtitleSizeDialog = false
                        }
                    )
                }
            }
        }

        if (showSubtitleTextColorDialog) {
            val options = remember(context) { subtitleTextColorOptions(context) }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_subtitle_text_color),
                onDismiss = { showSubtitleTextColorDialog = false }
            ) {
                options.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.label,
                        currentLevel = if (uiState.subtitleTextColor == option.colorArgb) index else -1,
                        onSelect = {
                            viewModel.setSubtitleTextColor(option.colorArgb)
                            showSubtitleTextColorDialog = false
                        }
                    )
                }
            }
        }

        if (showSubtitleBackgroundDialog) {
            val options = remember(context) { subtitleBackgroundColorOptions(context) }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_subtitle_background),
                onDismiss = { showSubtitleBackgroundDialog = false }
            ) {
                options.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.label,
                        currentLevel = if (uiState.subtitleBackgroundColor == option.colorArgb) index else -1,
                        onSelect = {
                            viewModel.setSubtitleBackgroundColor(option.colorArgb)
                            showSubtitleBackgroundDialog = false
                        }
                    )
                }
            }
        }

        if (showWifiQualityDialog) {
            QualityCapSelectionDialog(
                title = stringResource(R.string.settings_select_wifi_quality_cap),
                currentValue = uiState.wifiMaxVideoHeight,
                onDismiss = { showWifiQualityDialog = false },
                onSelect = {
                    viewModel.setWifiQualityCap(it)
                    showWifiQualityDialog = false
                }
            )
        }

        if (showEthernetQualityDialog) {
            QualityCapSelectionDialog(
                title = stringResource(R.string.settings_select_ethernet_quality_cap),
                currentValue = uiState.ethernetMaxVideoHeight,
                onDismiss = { showEthernetQualityDialog = false },
                onSelect = {
                    viewModel.setEthernetQualityCap(it)
                    showEthernetQualityDialog = false
                }
            )
        }

        categorySortDialogType?.let { typeName ->
            val type = ContentType.entries.firstOrNull { it.name == typeName }
            if (type != null) {
                CategorySortModeDialog(
                    type = type,
                    currentMode = uiState.categorySortModes[type] ?: CategorySortMode.DEFAULT,
                    onDismiss = { categorySortDialogType = null },
                    onModeSelected = { mode ->
                        viewModel.setCategorySortMode(type, mode)
                        categorySortDialogType = null
                    }
                )
            }
        }

        if (showPinDialog) {
            PinDialog(
                onDismissRequest = { 
                    showPinDialog = false
                    pinError = null
                    if (pendingAction == ParentalAction.SetNewPin) {
                        pendingAction = null
                        pendingProtectionLevel = null
                    }
                },
                onPinEntered = { pin ->
                    scope.launch {
                        if (pendingAction == ParentalAction.SetNewPin) {
                            viewModel.changePin(pin)
                            pendingProtectionLevel?.let(viewModel::setParentalControlLevel)
                            pendingProtectionLevel = null
                            showPinDialog = false
                            pendingAction = null
                        } else {
                            if (viewModel.verifyPin(pin)) {
                                showPinDialog = false
                                pinError = null
                                when (pendingAction) {
                                    ParentalAction.ChangeLevel -> showLevelDialog = true
                                    ParentalAction.ChangePin -> {
                                        pendingAction = ParentalAction.SetNewPin
                                        showPinDialog = true 
                                    }
                                    else -> pendingAction = null
                                }
                            } else {
                                pinError = context.getString(R.string.home_incorrect_pin)
                            }
                        }
                    }
                },
                title = if (pendingAction == ParentalAction.SetNewPin) stringResource(R.string.settings_enter_new_pin) else stringResource(R.string.settings_enter_pin),
                error = pinError
            )
        }

        if (showLevelDialog) {
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_level),
                onDismiss = { showLevelDialog = false }
            ) {
                LevelOption(0, stringResource(R.string.settings_level_off_desc), uiState.parentalControlLevel) {
                    viewModel.setParentalControlLevel(0)
                    showLevelDialog = false
                }
                LevelOption(1, stringResource(R.string.settings_level_locked_desc), uiState.parentalControlLevel) {
                    if (uiState.hasParentalPin) {
                        viewModel.setParentalControlLevel(1)
                    } else {
                        pendingProtectionLevel = 1
                        pendingAction = ParentalAction.SetNewPin
                        showPinDialog = true
                    }
                    showLevelDialog = false
                }
                LevelOption(2, stringResource(R.string.settings_level_hidden_desc), uiState.parentalControlLevel) {
                    if (uiState.hasParentalPin) {
                        viewModel.setParentalControlLevel(2)
                    } else {
                        pendingProtectionLevel = 2
                        pendingAction = ParentalAction.SetNewPin
                        showPinDialog = true
                    }
                    showLevelDialog = false
                }
            }
        }

        if (showLanguageDialog) {
            val systemDefaultLabel = stringResource(R.string.settings_system_default)
            val languageOptions = remember(systemDefaultLabel) { supportedAppLanguages(systemDefaultLabel) }
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_select_language),
                onDismiss = { showLanguageDialog = false }
            ) {
                languageOptions.forEachIndexed { index, option ->
                    LevelOption(
                        level = index,
                        text = option.label,
                        currentLevel = if (uiState.appLanguage == option.tag) index else -1,
                        onSelect = {
                            viewModel.setAppLanguage(option.tag)
                            showLanguageDialog = false
                        }
                    )
                }
            }
        }

        val backupPreview = uiState.backupPreview
        if (backupPreview != null && uiState.pendingBackupUri != null) {
            BackupImportPreviewDialog(
                preview = backupPreview,
                plan = uiState.backupImportPlan,
                onDismiss = { viewModel.dismissBackupPreview() },
                onStrategySelected = { viewModel.setBackupConflictStrategy(it) },
                onImportPreferencesChanged = { viewModel.setImportPreferences(it) },
                onImportProvidersChanged = { viewModel.setImportProviders(it) },
                onImportSavedLibraryChanged = { viewModel.setImportSavedLibrary(it) },
                onImportPlaybackHistoryChanged = { viewModel.setImportPlaybackHistory(it) },
                onImportMultiViewChanged = { viewModel.setImportMultiViewPresets(it) },
                onImportRecordingSchedulesChanged = { viewModel.setImportRecordingSchedules(it) },
                onConfirm = { viewModel.confirmBackupImport() }
            )
        }

        if (showRecordingPatternDialog) {
            SimpleTextValueDialog(
                title = stringResource(R.string.settings_recording_pattern_title),
                subtitle = stringResource(R.string.settings_recording_pattern_hint),
                initialValue = uiState.recordingStorageState.fileNamePattern,
                onDismiss = { showRecordingPatternDialog = false },
                onConfirm = { pattern ->
                    viewModel.updateRecordingFileNamePattern(pattern)
                    showRecordingPatternDialog = false
                }
            )
        }

        if (showRecordingRetentionDialog) {
            val retentionOptions = listOf<Int?>(null, 7, 14, 30, 60, 90)
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_recording_retention_title),
                onDismiss = { showRecordingRetentionDialog = false }
            ) {
                retentionOptions.forEachIndexed { index, days ->
                    LevelOption(
                        level = index,
                        text = if (days == null) {
                            stringResource(R.string.settings_recording_retention_keep_all)
                        } else {
                            stringResource(R.string.settings_recording_retention_days, days)
                        },
                        currentLevel = if (days == uiState.recordingStorageState.retentionDays) index else -1,
                        onSelect = {
                            viewModel.updateRecordingRetentionDays(days)
                            showRecordingRetentionDialog = false
                        }
                    )
                }
            }
        }

        if (showRecordingConcurrencyDialog) {
            PremiumSelectionDialog(
                title = stringResource(R.string.settings_recording_concurrency_title),
                onDismiss = { showRecordingConcurrencyDialog = false }
            ) {
                (1..4).forEach { value ->
                    LevelOption(
                        level = value,
                        text = value.toString(),
                        currentLevel = if (value == uiState.recordingStorageState.maxSimultaneousRecordings) value else -1,
                        onSelect = {
                            viewModel.updateRecordingMaxSimultaneous(value)
                            showRecordingConcurrencyDialog = false
                        }
                    )
                }
            }
        }

        if (showClearHistoryDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text(text = stringResource(R.string.settings_clear_history_dialog_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.settings_clear_history_dialog_body),
                        color = OnSurface
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearHistory()
                            showClearHistoryDialog = false
                        }
                    ) {
                        Text(text = stringResource(R.string.settings_clear_history_confirm), color = Primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text(text = stringResource(R.string.settings_cancel), color = OnSurface)
                    }
                },
                containerColor = SurfaceElevated,
                titleContentColor = OnSurface,
                textContentColor = TextSecondary
            )
        }
    }

    val pendingSyncProvider = pendingSyncProviderId?.let { providerId ->
        uiState.providers.firstOrNull { it.id == providerId }
    }
    if (showCreateCombinedDialog) {
        CreateCombinedM3uDialog(
            providers = uiState.availableM3uProviders,
            onDismiss = { showCreateCombinedDialog = false },
            onCreate = { name, providerIds ->
                showCreateCombinedDialog = false
                viewModel.createCombinedProfile(name, providerIds)
            }
        )
    }
    if (showRenameCombinedDialog) {
        val selectedProfile = uiState.combinedProfiles.firstOrNull { it.id == selectedCombinedProfileId }
        if (selectedProfile != null) {
            RenameCombinedM3uDialog(
                profile = selectedProfile,
                onDismiss = { showRenameCombinedDialog = false },
                onRename = { name ->
                    showRenameCombinedDialog = false
                    viewModel.renameCombinedProfile(selectedProfile.id, name)
                }
            )
        }
    }
    if (showAddCombinedMemberDialog) {
        val selectedProfile = uiState.combinedProfiles.firstOrNull { it.id == selectedCombinedProfileId }
        if (selectedProfile != null) {
            AddCombinedProviderDialog(
                profile = selectedProfile,
                availableProviders = uiState.availableM3uProviders,
                onDismiss = { showAddCombinedMemberDialog = false },
                onAddProvider = { providerId ->
                    showAddCombinedMemberDialog = false
                    viewModel.addProviderToCombinedProfile(selectedProfile.id, providerId)
                }
            )
        }
    }
    if (showProviderSyncDialog && pendingSyncProvider != null) {
        ProviderSyncOptionsDialog(
            provider = pendingSyncProvider,
            onDismiss = {
                showProviderSyncDialog = false
                pendingSyncProviderId = null
            },
            onSelect = { selection ->
                showProviderSyncDialog = false
                if (selection == ProviderSyncSelection.ALL) {
                    viewModel.syncProviderSection(pendingSyncProvider.id, selection)
                    pendingSyncProviderId = null
                } else if (selection == null) {
                    showCustomProviderSyncDialog = true
                } else {
                    viewModel.syncProviderSection(pendingSyncProvider.id, selection)
                    pendingSyncProviderId = null
                }
            }
        )
    }
    if (showCustomProviderSyncDialog && pendingSyncProvider != null) {
        ProviderCustomSyncDialog(
            provider = pendingSyncProvider,
            selected = customSyncSelections,
            onToggle = { option ->
                customSyncSelections = if (option in customSyncSelections) {
                    customSyncSelections - option
                } else {
                    customSyncSelections + option
                }
            },
            onDismiss = {
                showCustomProviderSyncDialog = false
                pendingSyncProviderId = null
            },
            onConfirm = {
                showCustomProviderSyncDialog = false
                viewModel.syncProviderCustom(pendingSyncProvider.id, customSyncSelections)
                pendingSyncProviderId = null
            }
        )
    }
}

@Composable
private fun CombinedM3uProfilesCard(
    profiles: List<CombinedM3uProfile>,
    availableProviders: List<Provider>,
    selectedProfileId: Long?,
    activeLiveSource: ActiveLiveSource?,
    onSelectProfile: (Long) -> Unit,
    onCreateProfile: () -> Unit,
    onActivateProfile: (Long) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onRenameProfile: (Long) -> Unit,
    onAddProvider: (Long) -> Unit,
    onRemoveProvider: (Long, Long) -> Unit,
    onToggleProviderEnabled: (Long, Long, Boolean) -> Unit,
    onMoveProvider: (Long, Long, Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Combined M3U", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Merge selected M3U playlists into one Live TV and EPG source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
                CompactSettingsActionChip(
                    label = "Create Combined",
                    accent = Primary,
                    onClick = onCreateProfile
                )
            }

            if (profiles.isEmpty()) {
                Text("No combined M3U sources yet.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        val isActive = (activeLiveSource as? ActiveLiveSource.CombinedM3uSource)?.profileId == profile.id
                        ProviderChip(
                            title = profile.name,
                            subtitle = buildString {
                                append("${profile.members.count { it.enabled }}/${profile.members.size} playlist(s)")
                                if (isActive) append(" • Active")
                                if (profile.members.none { it.enabled }) append(" • Empty")
                            },
                            isSelected = selectedProfileId == profile.id,
                            isActive = isActive,
                            onClick = { onSelectProfile(profile.id) }
                        )
                    }
                }

                val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactSettingsActionChip(
                            label = "Use For Live TV",
                            accent = Primary,
                            onClick = { onActivateProfile(selectedProfile.id) }
                        )
                        CompactSettingsActionChip(
                            label = "Rename",
                            accent = OnBackground,
                            onClick = { onRenameProfile(selectedProfile.id) }
                        )
                        CompactSettingsActionChip(
                            label = "Add Playlist",
                            accent = OnBackground,
                            onClick = { onAddProvider(selectedProfile.id) }
                        )
                        CompactSettingsActionChip(
                            label = "Delete",
                            accent = ErrorColor,
                            onClick = { onDeleteProfile(selectedProfile.id) }
                        )
                    }

                    Text(
                        text = "${selectedProfile.members.count { it.enabled }} of ${selectedProfile.members.size} playlists enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )

                    if (selectedProfile.members.isEmpty()) {
                        Text(
                            text = "This combined source has no playlists yet. Add at least one M3U playlist before using it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    } else if (selectedProfile.members.none { it.enabled }) {
                        Text(
                            text = "All playlists in this combined source are disabled. Enable at least one to use it in Live TV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }

                    selectedProfile.members
                        .sortedBy { it.priority }
                        .forEachIndexed { index, member ->
                        val providerName = member.providerName.ifBlank {
                            availableProviders.firstOrNull { it.id == member.providerId }?.name ?: "Playlist ${member.providerId}"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(providerName, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                                Text(
                                    if (member.enabled) "Enabled in merged source" else "Disabled in merged source",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompactSettingsActionChip(
                                    label = "Up",
                                    accent = OnBackground,
                                    enabled = index > 0,
                                    onClick = { onMoveProvider(selectedProfile.id, member.providerId, true) }
                                )
                                CompactSettingsActionChip(
                                    label = "Down",
                                    accent = OnBackground,
                                    enabled = index < selectedProfile.members.lastIndex,
                                    onClick = { onMoveProvider(selectedProfile.id, member.providerId, false) }
                                )
                                Switch(
                                    checked = member.enabled,
                                    onCheckedChange = { onToggleProviderEnabled(selectedProfile.id, member.providerId, it) }
                                )
                                CompactSettingsActionChip(
                                    label = "Remove",
                                    accent = ErrorColor,
                                    onClick = { onRemoveProvider(selectedProfile.id, member.providerId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameCombinedM3uDialog(
    profile: CombinedM3uProfile,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }

    PremiumDialog(
        title = "Rename Combined M3U",
        subtitle = "Update the name shown in Live TV and provider settings.",
        onDismissRequest = onDismiss,
        widthFraction = 0.48f,
        content = {
            EpgSourceTextField(
                value = name,
                onValueChange = { updated -> name = updated },
                placeholder = "Combined source name"
            )
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
            PremiumDialogFooterButton(
                label = "Save",
                onClick = { onRename(name.trim()) },
                enabled = name.isNotBlank(),
                emphasized = true
            )
        }
    )
}

@Composable
private fun CreateCombinedM3uDialog(
    providers: List<Provider>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedProviderIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val m3uProviders = remember(providers) { providers.filter { it.type == ProviderType.M3U } }
    val effectiveName = remember(name, selectedProviderIds, m3uProviders) {
        val manualName = name.trim()
        if (manualName.isNotBlank()) {
            manualName
        } else {
            val selectedProviders = m3uProviders.filter { it.id in selectedProviderIds }
            when {
                selectedProviders.isEmpty() -> ""
                selectedProviders.size == 1 -> "${selectedProviders.first().name} Mix"
                selectedProviders.size == 2 -> "${selectedProviders[0].name} + ${selectedProviders[1].name}"
                else -> "${selectedProviders.first().name} + ${selectedProviders.size - 1} More"
            }
        }
    }

    PremiumDialog(
        title = "Create Combined M3U",
        subtitle = "Pick the M3U playlists you want to browse together in Live TV and guide.",
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            EpgSourceTextField(
                value = name,
                onValueChange = { updated -> name = updated },
                placeholder = effectiveName.ifBlank { "Combined source name" }
            )

            if (m3uProviders.isEmpty()) {
                Text(
                    text = "No M3U playlists are available yet. Add at least one playlist first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    m3uProviders.forEach { provider ->
                        val isSelected = provider.id in selectedProviderIds
                        TvClickableSurface(
                            onClick = {
                                selectedProviderIds = if (isSelected) {
                                    selectedProviderIds - provider.id
                                } else {
                                    selectedProviderIds + provider.id
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                                focusedContainerColor = Primary.copy(alpha = 0.24f)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurface
                                    )
                                    Text(
                                        text = if (isSelected) "Included in this combined source" else "Press to include this playlist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceDim
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                            }
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
            PremiumDialogFooterButton(
                label = "Create",
                onClick = { onCreate(effectiveName, selectedProviderIds.toList()) },
                enabled = selectedProviderIds.isNotEmpty() && effectiveName.isNotBlank(),
                emphasized = true
            )
        }
    )
}

@Composable
private fun AddCombinedProviderDialog(
    profile: CombinedM3uProfile,
    availableProviders: List<Provider>,
    onDismiss: () -> Unit,
    onAddProvider: (Long) -> Unit
) {
    val candidateProviders = remember(profile, availableProviders) {
        availableProviders.filter { provider -> profile.members.none { it.providerId == provider.id } }
    }
    var selectedProviderId by rememberSaveable(profile.id) { mutableStateOf(candidateProviders.firstOrNull()?.id) }
    PremiumDialog(
        title = "Add Playlist To ${profile.name}",
        subtitle = "Select another M3U playlist to include in this combined source.",
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            if (candidateProviders.isEmpty()) {
                Text(
                    text = "All M3U playlists are already in this combined source.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    candidateProviders.forEach { provider ->
                        val isSelected = selectedProviderId == provider.id
                        TvClickableSurface(
                            onClick = { selectedProviderId = provider.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                                focusedContainerColor = Primary.copy(alpha = 0.22f)
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedProviderId = provider.id }
                                )
                            }
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
            PremiumDialogFooterButton(
                label = "Add",
                onClick = { selectedProviderId?.let(onAddProvider) },
                enabled = selectedProviderId != null && candidateProviders.isNotEmpty(),
                emphasized = true
            )
        }
    )
}

@Composable
private fun ProviderChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = Primary.copy(alpha = 0.24f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(
                if (isActive) "$subtitle • Active" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) Primary else OnSurfaceDim
            )
        }
    }
}

private fun availableSyncSelections(provider: Provider): List<ProviderSyncSelection> = buildList {
    add(ProviderSyncSelection.TV)
    add(ProviderSyncSelection.MOVIES)
    if (provider.type == ProviderType.XTREAM_CODES) {
        add(ProviderSyncSelection.SERIES)
    }
    add(ProviderSyncSelection.EPG)
}

@Composable
private fun ProviderSyncOptionsDialog(
    provider: Provider,
    onDismiss: () -> Unit,
    onSelect: (ProviderSyncSelection?) -> Unit
) {
    PremiumDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_sync_dialog_title, provider.name),
        content = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.settings_sync_dialog_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface
            )
            SyncOptionButton(stringResource(R.string.settings_sync_option_all)) {
                onSelect(ProviderSyncSelection.ALL)
            }
            availableSyncSelections(provider).forEach { option ->
                SyncOptionButton(text = syncSelectionLabel(option)) {
                    onSelect(option)
                }
            }
            OutlinedButton(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_sync_option_custom))
            }
        }
    }
    )
}

@Composable
private fun ProviderCustomSyncDialog(
    provider: Provider,
    selected: Set<ProviderSyncSelection>,
    onToggle: (ProviderSyncSelection) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PremiumDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_sync_custom_title, provider.name),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_sync_custom_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface
                )
                availableSyncSelections(provider).forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = option in selected,
                            onCheckedChange = { onToggle(option) }
                        )
                        Text(
                            text = syncSelectionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnBackground
                        )
                    }
                }
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.settings_cancel),
                    onClick = onDismiss
                )
                PremiumDialogFooterButton(
                    label = stringResource(R.string.settings_sync_btn),
                    onClick = onConfirm,
                    enabled = selected.isNotEmpty()
                )
            }
        }
    )
}

@Composable
private fun SyncOptionButton(
    text: String,
    onClick: () -> Unit
) {
    TvButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun syncSelectionLabel(selection: ProviderSyncSelection): String = when (selection) {
    ProviderSyncSelection.ALL -> stringResource(R.string.settings_sync_option_all)
    ProviderSyncSelection.FAST -> stringResource(R.string.settings_sync_option_fast)
    ProviderSyncSelection.TV -> stringResource(R.string.settings_sync_option_tv)
    ProviderSyncSelection.MOVIES -> stringResource(R.string.settings_sync_option_movies)
    ProviderSyncSelection.SERIES -> stringResource(R.string.settings_sync_option_series)
    ProviderSyncSelection.EPG -> stringResource(R.string.settings_sync_option_epg)
}

private enum class ParentalAction {
    ChangeLevel, ChangePin, SetNewPin
}

@Composable
private fun PremiumSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isTelevisionDevice = com.streamvault.app.device.rememberIsTelevisionDevice()
    Dialog(onDismissRequest = onDismiss) {
        val dialogContent: @Composable (Modifier) -> Unit = { resolvedModifier ->
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                modifier = resolvedModifier
                    .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Primary
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        content()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TvClickableSurface(
                            onClick = onDismiss,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Primary.copy(alpha = 0.2f),
                                focusedContainerColor = Primary.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.settings_cancel),
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isTelevisionDevice) {
            dialogContent(Modifier.fillMaxWidth(0.62f))
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val dialogWidthFraction = when {
                    maxWidth < 700.dp -> 0.92f
                    maxWidth < 1000.dp -> 0.78f
                    else -> 0.62f
                }
                dialogContent(Modifier.fillMaxWidth(dialogWidthFraction))
            }
        }
    }
}

@Composable
private fun TimeoutValueDialog(
    title: String,
    subtitle: String,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue.toString()) }
    val parsedValue = value.toIntOrNull()
    val isValid = parsedValue != null && parsedValue in 2..60

    PremiumDialog(
        title = title,
        subtitle = subtitle,
        onDismissRequest = onDismiss,
        widthFraction = 0.42f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_timeout_seconds_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface
                )
                NumericSettingsTextField(
                    value = value,
                    onValueChange = { updated -> value = updated.filter(Char::isDigit).take(2) },
                    placeholder = stringResource(R.string.settings_timeout_seconds_placeholder)
                )
                Text(
                    text = if (isValid) {
                        formatTimeoutSecondsLabel(parsedValue ?: initialValue, androidx.compose.ui.platform.LocalContext.current)
                    } else {
                        stringResource(R.string.settings_timeout_validation)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isValid) OnSurfaceDim else Color(0xFFFF8A80)
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.settings_cancel),
                    onClick = onDismiss
                )
                PremiumDialogFooterButton(
                    label = stringResource(R.string.settings_timeout_apply),
                    onClick = { parsedValue?.let(onConfirm) },
                    enabled = isValid
                )
            }
        }
    )
}

@Composable
private fun LevelOption(level: Int, text: String, currentLevel: Int, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = level == currentLevel,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
    }
}

@Composable
private fun NumericSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val isTelevisionDevice = com.streamvault.app.device.rememberIsTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasContainerFocus by remember { mutableStateOf(false) }
    var hasInputFocus by remember { mutableStateOf(false) }
    var acceptsInput by remember(isTelevisionDevice) { mutableStateOf(!isTelevisionDevice) }
    var pendingInputActivation by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val isFocused = hasContainerFocus || hasInputFocus

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    LaunchedEffect(acceptsInput, pendingInputActivation) {
        if (!isTelevisionDevice || !acceptsInput || !pendingInputActivation) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboardController?.show()
        pendingInputActivation = false
    }

    TvClickableSurface(
        onClick = {
            if (!isTelevisionDevice) {
                focusRequester.requestFocus()
                keyboardController?.show()
                return@TvClickableSurface
            }
            acceptsInput = true
            pendingInputActivation = true
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { hasContainerFocus = it.isFocused }
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (value.isEmpty() && !isFocused) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { updatedValue ->
                    val digitsOnly = updatedValue.text.filter(Char::isDigit).take(2)
                    fieldValue = updatedValue.copy(
                        text = digitsOnly,
                        selection = TextRange(digitsOnly.length.coerceAtMost(digitsOnly.length))
                    )
                    if (digitsOnly != value) {
                        onValueChange(digitsOnly)
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                singleLine = true,
                cursorBrush = SolidColor(Primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties {
                        canFocus = !isTelevisionDevice || acceptsInput
                        if (isTelevisionDevice && acceptsInput) {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                    }
                    .onFocusChanged {
                        hasInputFocus = it.isFocused
                        if (!it.isFocused) {
                            if (isTelevisionDevice) {
                                acceptsInput = false
                            }
                            keyboardController?.hide()
                        }
                    },
                readOnly = isTelevisionDevice && !acceptsInput
            )
        }
    }
}

@Composable
private fun QualityCapSelectionDialog(
    title: String,
    currentValue: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options = remember(context) {
        listOf<Int?>(null, 2160, 1080, 720, 480)
    }
    PremiumSelectionDialog(
        title = title,
        onDismiss = onDismiss
    ) {
        options.forEachIndexed { index, option ->
            LevelOption(
                level = index,
                text = formatQualityCapLabel(option, context.getString(R.string.settings_quality_cap_auto)),
                currentLevel = if (option == currentValue) index else -1,
                onSelect = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun CategorySortModeDialog(
    type: ContentType,
    currentMode: CategorySortMode,
    onDismiss: () -> Unit,
    onModeSelected: (CategorySortMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    PremiumDialog(
        title = categoryTypeLabel(type, context),
        subtitle = categoryTypeDescription(type, context),
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategorySortMode.entries.forEach { mode ->
                    TvClickableSurface(
                        onClick = { onModeSelected(mode) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == currentMode) Primary.copy(alpha = 0.18f) else SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = formatCategorySortModeLabel(mode, context),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (mode == currentMode) Primary else OnBackground
                            )
                            Text(
                                text = sortModeLabel(mode, context),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun ParentalControlCard(
    level: Int,
    hasParentalPin: Boolean,
    hasActiveProvider: Boolean,
    onChangeLevel: () -> Unit,
    onChangePin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.settings_protection_level), style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                Text(
                    text = when(level) {
                        0 -> stringResource(R.string.settings_level_off)
                        1 -> stringResource(R.string.settings_level_locked)
                        2 -> stringResource(R.string.settings_level_hidden)
                        else -> stringResource(R.string.settings_level_unknown)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (level == 0) ErrorColor else Primary
                )
            }
            
            // Custom Focusable Button for "Change"
            TvClickableSurface(
                onClick = onChangeLevel,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Secondary.copy(alpha = 0.2f),
                    focusedContainerColor = Secondary.copy(alpha = 0.5f),
                    contentColor = Secondary,
                    focusedContentColor = Secondary
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_change),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_parental_pin), style = MaterialTheme.typography.bodyLarge, color = OnBackground)
            
            // Custom Focusable Button for "Change PIN"
            TvClickableSurface(
                onClick = onChangePin,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.2f),
                    focusedContainerColor = Primary.copy(alpha = 0.5f),
                    contentColor = Primary,
                    focusedContentColor = Primary
                )
            ) {
                Text(
                    text = stringResource(if (hasParentalPin) R.string.settings_change_pin else R.string.settings_set_pin),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(
    provider: Provider,
    isActive: Boolean,
    isSyncing: Boolean,
    diagnostics: ProviderDiagnosticsUiModel?,
    syncWarnings: List<String>,
    onRetryWarningAction: (ProviderWarningAction) -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onParentalControl: () -> Unit,
    onToggleM3uVodClassification: (Boolean) -> Unit,
    onRefreshM3uClassification: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Use Column layout - provider info + buttons below as separate focusable items
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isActive) SurfaceHighlight else SurfaceElevated,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = if (isActive) Primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Provider info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground
                )
                Text(
                    text = provider.type.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }
            ProviderStatusBadge(status = provider.status)
            if (isActive) {
                Text(
                    text = stringResource(R.string.settings_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Expiration Date
        val expDate = provider.expirationDate
        val expirationText = remember(expDate) {
            when (expDate) {
                null -> context.getString(R.string.settings_expiration_unknown)
                Long.MAX_VALUE -> context.getString(R.string.settings_expiration_never)
                else -> context.getString(R.string.settings_expires, java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(expDate)))
            }
        }
        Text(
            text = expirationText,
            style = MaterialTheme.typography.bodySmall,
            color = if (expDate != null && expDate < System.currentTimeMillis() && expDate != Long.MAX_VALUE) ErrorColor else OnSurfaceDim
        )

        diagnostics?.let { model ->
            Text(
                text = listOf(model.sourceLabel, model.connectionSummary, model.expirySummary)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderCompactStat(title = stringResource(R.string.settings_diagnostic_live), count = model.liveCount)
                ProviderCompactStat(title = stringResource(R.string.settings_diagnostic_movies), count = model.movieCount)
                if (provider.type == ProviderType.XTREAM_CODES) {
                    ProviderCompactStat(title = stringResource(R.string.settings_diagnostic_series), count = model.seriesCount)
                }
                ProviderCompactStat(title = stringResource(R.string.settings_diagnostic_epg), count = model.epgCount)
            }

            ProviderDiagnosticsPanel(
                provider = provider,
                diagnostics = model
            )
        }

        if (provider.type == ProviderType.M3U) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(10.dp))
                    .border(1.dp, SurfaceHighlight, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_m3u_vod_classification_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = OnBackground
                        )
                        Text(
                            text = stringResource(R.string.settings_m3u_vod_classification_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                    Switch(
                        checked = provider.m3uVodClassificationEnabled,
                        onCheckedChange = onToggleM3uVodClassification
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvClickableSurface(
                        onClick = onRefreshM3uClassification,
                        enabled = !isSyncing,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Primary.copy(alpha = 0.15f),
                            focusedContainerColor = Primary.copy(alpha = 0.3f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_m3u_vod_classification_refresh),
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        if (syncWarnings.isNotEmpty()) {
            val hasEpgWarning = syncWarnings.any { it.contains("EPG", ignoreCase = true) }
            val hasMoviesWarning = syncWarnings.any { it.contains("Movies", ignoreCase = true) }
            val hasSeriesWarning = syncWarnings.any { it.contains("Series", ignoreCase = true) }

            Text(
                text = stringResource(R.string.settings_provider_warnings, syncWarnings.take(3).joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = Secondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasEpgWarning) {
                    TvClickableSurface(
                        onClick = { onRetryWarningAction(ProviderWarningAction.EPG) },
                        enabled = !isSyncing,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Secondary.copy(alpha = 0.16f),
                            focusedContainerColor = Secondary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.settings_retry_epg),
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                if (hasMoviesWarning) {
                    TvClickableSurface(
                        onClick = { onRetryWarningAction(ProviderWarningAction.MOVIES) },
                        enabled = !isSyncing,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Secondary.copy(alpha = 0.16f),
                            focusedContainerColor = Secondary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.settings_retry_movies),
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                if (hasSeriesWarning && provider.type == ProviderType.XTREAM_CODES) {
                    TvClickableSurface(
                        onClick = { onRetryWarningAction(ProviderWarningAction.SERIES) },
                        enabled = !isSyncing,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Secondary.copy(alpha = 0.16f),
                            focusedContainerColor = Secondary.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.settings_retry_series),
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Action buttons - each independently focusable for d-pad
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isActive) {
                TvClickableSurface(
                    onClick = onConnect,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary,
                        focusedContainerColor = Primary.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                            shape = RoundedCornerShape(6.dp)
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Text(
                        text = stringResource(R.string.settings_connect),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                TvClickableSurface(
                    onClick = onRefresh,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.2f),
                        focusedContainerColor = Primary.copy(alpha = 0.5f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                            shape = RoundedCornerShape(6.dp)
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Text(
                        text = if (isSyncing) stringResource(R.string.settings_syncing_btn) else stringResource(R.string.settings_sync_btn),
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            TvClickableSurface(
                onClick = onEdit,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Secondary.copy(alpha = 0.2f),
                    focusedContainerColor = Secondary.copy(alpha = 0.5f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                        shape = RoundedCornerShape(6.dp)
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                Text(
                    text = stringResource(R.string.settings_edit),
                    style = MaterialTheme.typography.labelMedium,
                    color = Secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            TvClickableSurface(
                onClick = onDelete,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = ErrorColor.copy(alpha = 0.2f),
                    focusedContainerColor = ErrorColor.copy(alpha = 0.5f)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                        shape = RoundedCornerShape(6.dp)
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
            ) {
                Text(
                    text = stringResource(R.string.settings_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = ErrorColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (isActive) {
                TvClickableSurface(
                    onClick = onParentalControl,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.15f),
                        focusedContainerColor = Primary.copy(alpha = 0.3f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                            shape = RoundedCornerShape(6.dp)
                        )
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Text(
                        text = stringResource(R.string.settings_provider_category_controls_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

    }
}

@Composable
private fun ProviderSelectorTab(
    provider: Provider,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.18f) else SurfaceElevated,
            focusedContainerColor = Primary.copy(alpha = 0.34f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) Primary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f)
                )
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = Primary
                )
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = provider.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceDim
                )
            }
            if (isActive) {
                Text(
                    text = stringResource(R.string.settings_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProviderCompactStat(
    title: String,
    count: Int
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = OnBackground
            )
        }
    }
}

@Composable
private fun ProviderStatusBadge(status: ProviderStatus) {
    val (label, color) = when (status) {
        ProviderStatus.ACTIVE -> stringResource(R.string.settings_status_active) to Primary
        ProviderStatus.PARTIAL -> stringResource(R.string.settings_status_partial) to Secondary
        ProviderStatus.ERROR -> stringResource(R.string.settings_status_error) to ErrorColor
        ProviderStatus.EXPIRED -> stringResource(R.string.settings_status_expired) to ErrorColor
        ProviderStatus.DISABLED -> stringResource(R.string.settings_status_disabled) to OnSurfaceDim
        ProviderStatus.UNKNOWN -> stringResource(R.string.settings_status_unknown) to OnSurfaceDim
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsOverviewCard(
    activeProviderName: String,
    providerCount: Int,
    protectionSummary: String,
    languageLabel: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_overview_title),
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground
            )
            Text(
                text = stringResource(R.string.settings_overview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDim
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.settings_overview_provider_label),
                    value = activeProviderName
                )
                SettingsOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.settings_overview_count_label),
                    value = providerCount.toString()
                )
                SettingsOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.settings_overview_protection_label),
                    value = protectionSummary
                )
                SettingsOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.settings_overview_language_label),
                    value = languageLabel
                )
            }
        }

    }
}

@Composable
private fun ProviderDiagnosticsPanel(
    provider: Provider,
    diagnostics: ProviderDiagnosticsUiModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.settings_provider_diagnostics_title),
            style = MaterialTheme.typography.titleSmall,
            color = Primary
        )
        Text(
            text = diagnostics.capabilitySummary,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
        Text(
            text = stringResource(R.string.diagnostics_summary_format, diagnostics.sourceLabel, diagnostics.connectionSummary),
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface
        )
        Text(
            text = diagnostics.expirySummary,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface
        )
        Text(
            text = diagnostics.archiveSummary,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProviderDiagnosticPill(
                title = stringResource(R.string.settings_diagnostic_live),
                count = diagnostics.liveCount,
                timestamp = diagnostics.lastLiveSync
            )
            ProviderDiagnosticPill(
                title = stringResource(R.string.settings_diagnostic_movies),
                count = diagnostics.movieCount,
                timestamp = diagnostics.lastMovieSync
            )
            if (provider.type == ProviderType.XTREAM_CODES) {
                ProviderDiagnosticPill(
                    title = stringResource(R.string.settings_diagnostic_series),
                    count = diagnostics.seriesCount,
                    timestamp = diagnostics.lastSeriesSync
                )
            }
            ProviderDiagnosticPill(
                title = stringResource(R.string.settings_diagnostic_epg),
                count = diagnostics.epgCount,
                timestamp = diagnostics.lastEpgSync
            )
        }
        Text(
            text = stringResource(R.string.settings_diagnostic_status, diagnostics.lastSyncStatus),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface
        )
    }
}

@Composable
private fun ProviderDiagnosticPill(
    title: String,
    count: Int,
    timestamp: Long
) {
    val syncLabel = remember(timestamp) {
        if (timestamp <= 0L) {
            null
        } else {
            java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        colors = SurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim
            )
            Text(
                text = stringResource(R.string.settings_diagnostic_items, count),
                style = MaterialTheme.typography.labelLarge,
                color = OnBackground
            )
            Text(
                text = syncLabel ?: stringResource(R.string.settings_diagnostic_never),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface
            )
        }
    }
}

@Composable
private fun SettingsOverviewStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceHighlight.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceDim
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = OnBackground,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LiveTvChannelModeDialog(
    selectedMode: LiveTvChannelMode,
    onDismiss: () -> Unit,
    onModeSelected: (LiveTvChannelMode) -> Unit
) {
    PremiumDialog(
        title = stringResource(R.string.settings_live_tv_channel_mode),
        subtitle = stringResource(R.string.settings_live_tv_channel_mode_subtitle),
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTvChannelMode.entries.forEach { mode ->
                    TvClickableSurface(
                        onClick = { onModeSelected(mode) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == selectedMode) Primary.copy(alpha = 0.18f) else SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(mode.labelResId()),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (mode == selectedMode) Primary else OnBackground
                            )
                            Text(
                                text = stringResource(mode.descriptionResId()),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun LiveTvQuickFilterVisibilityDialog(
    selectedMode: LiveTvQuickFilterVisibilityMode,
    onDismiss: () -> Unit,
    onModeSelected: (LiveTvQuickFilterVisibilityMode) -> Unit
) {
    PremiumDialog(
        title = stringResource(R.string.settings_live_tv_quick_filter_visibility),
        subtitle = stringResource(R.string.settings_live_tv_quick_filter_visibility_subtitle),
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTvQuickFilterVisibilityMode.entries.forEach { mode ->
                    TvClickableSurface(
                        onClick = { onModeSelected(mode) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == selectedMode) Primary.copy(alpha = 0.18f) else SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(mode.labelResId()),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (mode == selectedMode) Primary else OnBackground
                            )
                            Text(
                                text = stringResource(mode.descriptionResId()),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
    }
}

@Composable
private fun VodViewModeDialog(
    selectedMode: VodViewMode,
    onDismiss: () -> Unit,
    onModeSelected: (VodViewMode) -> Unit
) {
    PremiumDialog(
        title = stringResource(R.string.settings_vod_view_mode),
        subtitle = stringResource(R.string.settings_vod_view_mode_subtitle),
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VodViewMode.entries.forEach { mode ->
                    TvClickableSurface(
                        onClick = { onModeSelected(mode) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == selectedMode) Primary.copy(alpha = 0.18f) else SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(mode.labelResId()),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (mode == selectedMode) Primary else OnBackground
                            )
                            Text(
                                text = stringResource(mode.descriptionResId()),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun LiveChannelNumberingModeDialog(
    selectedMode: ChannelNumberingMode,
    onDismiss: () -> Unit,
    onModeSelected: (ChannelNumberingMode) -> Unit
) {
    PremiumDialog(
        title = stringResource(R.string.settings_live_channel_numbering_mode),
        subtitle = stringResource(R.string.settings_live_channel_numbering_mode_subtitle),
        onDismissRequest = onDismiss,
        widthFraction = 0.52f,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelNumberingMode.entries.forEach { mode ->
                    TvClickableSurface(
                        onClick = { onModeSelected(mode) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (mode == selectedMode) Primary.copy(alpha = 0.18f) else SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.28f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(mode.labelResId()),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (mode == selectedMode) Primary else OnBackground
                            )
                            Text(
                                text = stringResource(mode.descriptionResId()),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun SettingsRow(label: String, value: String) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Primary.copy(alpha = 0.15f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = {}
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
        }
    }
}

@Composable
private fun ClickableSettingsRow(label: String, value: String, onClick: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Primary.copy(alpha = 0.15f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Primary)
        }
    }
}

@Composable
private fun SwitchSettingsRow(label: String, value: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = { onCheckedChange(!checked) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Primary.copy(alpha = 0.15f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = { onCheckedChange(!checked) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) }
            )
        }
    }
}

private fun formatLatestReleaseLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val versionName = update.latestVersionName ?: return context.getString(R.string.settings_update_not_checked)
    val versionCodeSuffix = update.latestVersionCode?.let { " ($it)" }.orEmpty()
    return "$versionName$versionCodeSuffix"
}

private fun formatUpdateStatusLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when {
        update.errorMessage != null -> context.getString(R.string.settings_update_status_check_failed)
        update.downloadStatus == com.streamvault.app.update.AppUpdateDownloadStatus.Downloading -> context.getString(R.string.settings_update_status_downloading)
        update.downloadStatus == com.streamvault.app.update.AppUpdateDownloadStatus.Downloaded && downloadedReleaseMatchesLatest -> context.getString(R.string.settings_update_status_ready_to_install)
        update.latestVersionName == null -> context.getString(R.string.settings_update_not_checked)
        update.isUpdateAvailable -> context.getString(R.string.settings_update_status_available)
        else -> context.getString(R.string.settings_update_status_current)
    }
}

private fun formatUpdateCheckTimeLabel(timestamp: Long?, context: android.content.Context): String {
    if (timestamp == null || timestamp <= 0L) {
        return context.getString(R.string.settings_update_not_checked)
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(timestamp))
}

private fun shouldShowUpdateDownloadAction(update: AppUpdateUiModel): Boolean {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when (update.downloadStatus) {
        com.streamvault.app.update.AppUpdateDownloadStatus.Downloading,
        com.streamvault.app.update.AppUpdateDownloadStatus.Downloaded -> downloadedReleaseMatchesLatest || (update.isUpdateAvailable && !update.downloadUrl.isNullOrBlank())
        else -> update.isUpdateAvailable && !update.downloadUrl.isNullOrBlank()
    }
}

private fun formatUpdateDownloadLabel(update: AppUpdateUiModel, context: android.content.Context): String {
    val downloadedReleaseMatchesLatest = update.downloadedVersionName != null &&
        (update.latestVersionName == null || update.downloadedVersionName == update.latestVersionName)
    return when (update.downloadStatus) {
        com.streamvault.app.update.AppUpdateDownloadStatus.Downloading -> context.getString(R.string.settings_update_download_in_progress)
        com.streamvault.app.update.AppUpdateDownloadStatus.Downloaded -> {
            if (downloadedReleaseMatchesLatest) {
                context.getString(R.string.settings_update_install_action)
            } else {
                context.getString(R.string.settings_update_download_action)
            }
        }
        else -> context.getString(R.string.settings_update_download_action)
    }
}

@Composable
private fun InternetSpeedTestCard(
    valueLabel: String,
    summary: String,
    recommendationLabel: String,
    isRunning: Boolean,
    canApplyRecommendation: Boolean,
    onRunTest: () -> Unit,
    onApplyWifi: () -> Unit,
    onApplyEthernet: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onRunTest,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = Primary.copy(alpha = 0.18f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onRunTest
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_speed_test_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnBackground
                    )
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isRunning) {
                    CircularProgressIndicator(
                        color = Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_speed_test_run_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary
                    )
                }
            }

            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )

            Text(
                text = stringResource(R.string.settings_speed_test_recommendation, recommendationLabel),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvClickableSurface(
                    onClick = onRunTest,
                    enabled = !isRunning,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.18f),
                        focusedContainerColor = Primary.copy(alpha = 0.32f)
                    )
                ) {
                    Text(
                        text = stringResource(if (isRunning) R.string.settings_speed_test_running_action else R.string.settings_speed_test_run_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                TvClickableSurface(
                    onClick = onApplyWifi,
                    enabled = canApplyRecommendation && !isRunning,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Secondary.copy(alpha = 0.16f),
                        focusedContainerColor = Secondary.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings_speed_test_apply_wifi),
                        style = MaterialTheme.typography.labelMedium,
                        color = Secondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                TvClickableSurface(
                    onClick = onApplyEthernet,
                    enabled = canApplyRecommendation && !isRunning,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Secondary.copy(alpha = 0.16f),
                        focusedContainerColor = Secondary.copy(alpha = 0.28f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings_speed_test_apply_ethernet),
                        style = MaterialTheme.typography.labelMedium,
                        color = Secondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupImportPreviewDialog(
    preview: com.streamvault.domain.manager.BackupPreview,
    plan: com.streamvault.domain.manager.BackupImportPlan,
    onDismiss: () -> Unit,
    onStrategySelected: (BackupConflictStrategy) -> Unit,
    onImportPreferencesChanged: (Boolean) -> Unit,
    onImportProvidersChanged: (Boolean) -> Unit,
    onImportSavedLibraryChanged: (Boolean) -> Unit,
    onImportPlaybackHistoryChanged: (Boolean) -> Unit,
    onImportMultiViewChanged: (Boolean) -> Unit,
    onImportRecordingSchedulesChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    PremiumDialog(
        title = stringResource(R.string.settings_backup_preview_title),
        subtitle = stringResource(R.string.settings_backup_preview_subtitle, preview.version),
        onDismissRequest = onDismiss,
        widthFraction = 0.58f,
        content = {
            BackupPreviewRow(stringResource(R.string.settings_backup_section_preferences), preview.preferenceCount, 0)
            BackupPreviewRow(stringResource(R.string.settings_backup_section_providers), preview.providerCount, preview.providerConflicts)
            BackupPreviewRow(stringResource(R.string.settings_backup_section_saved), preview.favoriteCount + preview.groupCount + preview.protectedCategoryCount, preview.favoriteConflicts + preview.groupConflicts + preview.protectedCategoryConflicts)
            BackupPreviewRow(stringResource(R.string.settings_backup_section_history), preview.playbackHistoryCount, preview.historyConflicts)
            BackupPreviewRow(stringResource(R.string.settings_backup_section_multiview), preview.multiViewPresetCount, 0)
            BackupPreviewRow(stringResource(R.string.settings_backup_section_recordings), preview.scheduledRecordingCount, preview.recordingConflicts)
            Text(
                text = stringResource(R.string.settings_backup_conflict_strategy),
                style = MaterialTheme.typography.titleSmall,
                color = Primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupStrategyChip(
                    title = stringResource(R.string.settings_backup_keep_existing),
                    selected = plan.conflictStrategy == BackupConflictStrategy.KEEP_EXISTING,
                    onClick = { onStrategySelected(BackupConflictStrategy.KEEP_EXISTING) }
                )
                BackupStrategyChip(
                    title = stringResource(R.string.settings_backup_replace_existing),
                    selected = plan.conflictStrategy == BackupConflictStrategy.REPLACE_EXISTING,
                    onClick = { onStrategySelected(BackupConflictStrategy.REPLACE_EXISTING) }
                )
            }
            Text(
                text = stringResource(R.string.settings_backup_import_sections),
                style = MaterialTheme.typography.titleSmall,
                color = Primary
            )
            BackupToggleRow(stringResource(R.string.settings_backup_section_preferences), plan.importPreferences, onImportPreferencesChanged)
            BackupToggleRow(stringResource(R.string.settings_backup_section_providers), plan.importProviders, onImportProvidersChanged)
            BackupToggleRow(stringResource(R.string.settings_backup_section_saved), plan.importSavedLibrary, onImportSavedLibraryChanged)
            BackupToggleRow(stringResource(R.string.settings_backup_section_history), plan.importPlaybackHistory, onImportPlaybackHistoryChanged)
            BackupToggleRow(stringResource(R.string.settings_backup_section_multiview), plan.importMultiViewPresets, onImportMultiViewChanged)
            BackupToggleRow(stringResource(R.string.settings_backup_section_recordings), plan.importRecordingSchedules, onImportRecordingSchedulesChanged)
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_backup_import_confirm),
                onClick = onConfirm,
                emphasized = true
            )
        }
    )
}

@Composable
private fun BackupPreviewRow(
    title: String,
    itemCount: Int,
    conflictCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
            Text(
                text = if (conflictCount > 0) {
                    stringResource(R.string.settings_backup_conflict_count, conflictCount)
                } else {
                    stringResource(R.string.settings_backup_no_conflicts)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (conflictCount > 0) Secondary else OnSurfaceDim
            )
        }
        Text(
            text = stringResource(R.string.settings_backup_item_count, itemCount),
            style = MaterialTheme.typography.labelLarge,
            color = OnBackground
        )
    }
}

@Composable
private fun BackupStrategyChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Primary.copy(alpha = 0.2f) else SurfaceElevated,
            focusedContainerColor = Primary.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Primary else OnBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BackupToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingOverviewCard(
    treeLabel: String?,
    outputDirectory: String?,
    availableBytes: Long?,
    isWritable: Boolean,
    activeCount: Int,
    scheduledCount: Int,
    fileNamePattern: String,
    retentionDays: Int?,
    maxSimultaneousRecordings: Int,
    onChooseFolder: () -> Unit,
    onUseAppStorage: () -> Unit,
    onChangePattern: () -> Unit,
    onChangeRetention: () -> Unit,
    onChangeConcurrency: () -> Unit,
    onRepairSchedule: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_recording_storage_title),
                style = MaterialTheme.typography.titleMedium,
                color = Primary
            )
            treeLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary
                )
            }
            Text(
                text = outputDirectory ?: stringResource(R.string.settings_recording_storage_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_recording_active_label),
                    value = activeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_recording_scheduled_label),
                    value = scheduledCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SettingsOverviewStat(
                    label = stringResource(R.string.settings_recording_space_label),
                    value = availableBytes?.let(::formatBytes) ?: stringResource(R.string.settings_recording_storage_unknown),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (isWritable) stringResource(R.string.settings_recording_storage_ready) else stringResource(R.string.settings_recording_storage_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (isWritable) Primary else ErrorColor
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_pattern_title),
                    value = fileNamePattern
                )
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_retention_title),
                    value = retentionDays?.let {
                        stringResource(R.string.settings_recording_retention_days, it)
                    } ?: stringResource(R.string.settings_recording_retention_keep_all)
                )
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_concurrency_title),
                    value = maxSimultaneousRecordings.toString()
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                RecordingActionButton(stringResource(R.string.settings_recording_choose_folder), Primary, onChooseFolder)
                RecordingActionButton(stringResource(R.string.settings_recording_use_app_storage), Secondary, onUseAppStorage)
                RecordingActionButton(stringResource(R.string.settings_recording_pattern_title), OnBackground, onChangePattern)
                RecordingActionButton(stringResource(R.string.settings_recording_retention_title), OnBackground, onChangeRetention)
                RecordingActionButton(stringResource(R.string.settings_recording_concurrency_title), OnBackground, onChangeConcurrency)
                RecordingActionButton(stringResource(R.string.settings_recording_reconcile), Secondary, onRepairSchedule)
            }
        }
    }
}

@Composable
private fun RecordingItemCard(
    item: RecordingItem,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onToggleSchedule: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.programTitle ?: item.channelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.programTitle != null && item.programTitle != item.channelName) {
                        Text(
                            item.channelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = when (item.status) {
                        RecordingStatus.SCHEDULED -> stringResource(R.string.settings_recording_status_scheduled)
                        RecordingStatus.RECORDING -> stringResource(R.string.settings_recording_status_recording)
                        RecordingStatus.COMPLETED -> stringResource(R.string.settings_recording_status_completed)
                        RecordingStatus.FAILED -> stringResource(R.string.settings_recording_status_failed)
                        RecordingStatus.CANCELLED -> stringResource(R.string.settings_recording_status_cancelled)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.status) {
                        RecordingStatus.RECORDING -> Primary
                        RecordingStatus.COMPLETED -> OnBackground
                        RecordingStatus.FAILED -> ErrorColor
                        RecordingStatus.CANCELLED -> OnSurfaceDim
                        RecordingStatus.SCHEDULED -> Secondary
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_recording_time_window,
                        formatTimestamp(item.scheduledStartMs),
                        formatTimestamp(item.scheduledEndMs)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.recurrence != RecordingRecurrence.NONE) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (item.recurrence) {
                            RecordingRecurrence.DAILY -> stringResource(R.string.settings_recording_recurrence_daily)
                            RecordingRecurrence.WEEKLY -> stringResource(R.string.settings_recording_recurrence_weekly)
                            RecordingRecurrence.NONE -> stringResource(R.string.settings_recording_recurrence_none)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary,
                        maxLines = 1
                    )
                }
            }
            item.failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = ErrorColor)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4
            ) {
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_source_label),
                    value = formatRecordingSourceType(item.sourceType)
                )
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_bytes_label),
                    value = formatBytes(item.bytesWritten)
                )
                RecordingMetaPill(
                    label = stringResource(R.string.settings_recording_speed_label),
                    value = if (item.averageThroughputBytesPerSecond > 0L) {
                        "${formatBytes(item.averageThroughputBytesPerSecond)}/s"
                    } else {
                        "–"
                    }
                )
                if (item.retryCount > 0) {
                    RecordingMetaPill(
                        label = stringResource(R.string.settings_recording_retry_count_label),
                        value = item.retryCount.toString()
                    )
                }
            }
            item.outputDisplayPath?.takeIf { it.isNotBlank() }?.let { output ->
                Text(
                    text = "${stringResource(R.string.settings_recording_output_label)}: ${summarizeRecordingOutputPath(output)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.failureCategory != RecordingFailureCategory.NONE) {
                Text(
                    text = "${stringResource(R.string.settings_recording_failure_label)}: ${formatRecordingFailureCategory(item.failureCategory)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorColor
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4
            ) {
                if (item.status == RecordingStatus.COMPLETED && (!item.outputUri.isNullOrBlank() || !item.outputPath.isNullOrBlank())) {
                    CompactRecordingActionChip(
                        label = "Play",
                        accent = Primary,
                        onClick = onPlay
                    )
                }
                if (item.status == RecordingStatus.RECORDING) {
                    CompactRecordingActionChip(
                        label = stringResource(R.string.settings_recording_stop),
                        accent = ErrorColor,
                        onClick = onStop
                    )
                }
                if (item.status == RecordingStatus.SCHEDULED) {
                    CompactRecordingActionChip(
                        label = stringResource(
                            if (item.scheduleEnabled) R.string.settings_recording_disable
                            else R.string.settings_recording_enable
                        ),
                        accent = Secondary,
                        onClick = { onToggleSchedule(!item.scheduleEnabled) }
                    )
                    CompactRecordingActionChip(
                        label = stringResource(R.string.settings_recording_cancel),
                        accent = OnBackground,
                        onClick = onCancel
                    )
                }
                if (item.status == RecordingStatus.COMPLETED || item.status == RecordingStatus.FAILED || item.status == RecordingStatus.CANCELLED) {
                    if (item.status == RecordingStatus.FAILED) {
                        CompactRecordingActionChip(
                            label = stringResource(R.string.settings_recording_retry),
                            accent = Primary,
                            onClick = onRetry
                        )
                    }
                    CompactRecordingActionChip(
                        label = stringResource(R.string.settings_recording_delete),
                        accent = OnBackground,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRecordingActionChip(label: String, accent: Color, onClick: () -> Unit) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accent.copy(alpha = 0.14f),
            focusedContainerColor = accent.copy(alpha = 0.3f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CompactSettingsActionChip(
    label: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accent.copy(alpha = if (enabled) 0.14f else 0.08f),
            contentColor = accent.copy(alpha = if (enabled) 1f else 0.42f),
            focusedContainerColor = accent.copy(alpha = if (enabled) 0.28f else 0.08f),
            focusedContentColor = accent.copy(alpha = if (enabled) 1f else 0.42f),
            disabledContainerColor = accent.copy(alpha = 0.08f),
            disabledContentColor = accent.copy(alpha = 0.42f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)),
                shape = RoundedCornerShape(8.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = label,
            color = accent.copy(alpha = if (enabled) 1f else 0.42f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun RecordingItem.playbackUrl(): String? {
    val persistedUri = outputUri?.trim()?.takeIf { it.isNotBlank() }
    if (persistedUri != null) {
        return persistedUri
    }
    val localPath = outputPath?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching { android.net.Uri.parse(localPath) }.getOrNull()
    return if (parsed?.scheme.isNullOrBlank()) {
        android.net.Uri.fromFile(java.io.File(localPath)).toString()
    } else {
        localPath
    }
}

private fun android.content.Context.findMainActivity(): MainActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is MainActivity) return current
        current = current.baseContext
    }
    return null
}

private fun displayableEpgUrl(url: String): String = when {
    url.startsWith("content://") -> {
        val lastSegment = try { android.net.Uri.parse(url).lastPathSegment } catch (_: Exception) { null }
        val decoded = lastSegment?.let { android.net.Uri.decode(it) }?.substringAfterLast("/")?.substringAfterLast("\\")
        if (!decoded.isNullOrBlank() && decoded.length < 60) "local: $decoded" else "local file"
    }
    else -> url
}

@Composable
private fun EpgSourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val isTelevisionDevice = com.streamvault.app.device.rememberIsTelevisionDevice()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var hasContainerFocus by remember { mutableStateOf(false) }
    var hasInputFocus by remember { mutableStateOf(false) }
    var acceptsInput by remember(isTelevisionDevice) { mutableStateOf(!isTelevisionDevice) }
    var pendingInputActivation by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val isFocused = hasContainerFocus || hasInputFocus

    fun requestBringIntoView(delayMillis: Long = 0L) {
        coroutineScope.launch {
            if (delayMillis > 0) {
                kotlinx.coroutines.delay(delayMillis)
            }
            runCatching { bringIntoViewRequester.bringIntoView() }
        }
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            val coercedSelectionStart = fieldValue.selection.start.coerceIn(0, value.length)
            val coercedSelectionEnd = fieldValue.selection.end.coerceIn(0, value.length)
            val coercedComposition = fieldValue.composition?.let { composition ->
                val compositionStart = composition.start.coerceIn(0, value.length)
                val compositionEnd = composition.end.coerceIn(0, value.length)
                if (compositionStart <= compositionEnd) {
                    TextRange(compositionStart, compositionEnd)
                } else {
                    null
                }
            }
            fieldValue = fieldValue.copy(
                text = value,
                selection = TextRange(coercedSelectionStart, coercedSelectionEnd),
                composition = coercedComposition
            )
        }
    }

    LaunchedEffect(acceptsInput, pendingInputActivation) {
        if (!isTelevisionDevice || !acceptsInput || !pendingInputActivation) {
            return@LaunchedEffect
        }
        focusRequester.requestFocus()
        keyboardController?.show()
        requestBringIntoView(120)
        pendingInputActivation = false
    }

    TvClickableSurface(
        onClick = {
            if (!isTelevisionDevice) {
                focusRequester.requestFocus()
                keyboardController?.show()
                requestBringIntoView()
                requestBringIntoView(180)
                return@TvClickableSurface
            }
            acceptsInput = true
            pendingInputActivation = true
            requestBringIntoView()
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { hasContainerFocus = it.isFocused }
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (value.isEmpty() && !isFocused) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceDim)
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { updatedValue ->
                    fieldValue = updatedValue
                    if (updatedValue.text != value) {
                        onValueChange(updatedValue.text)
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                singleLine = true,
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties {
                        canFocus = !isTelevisionDevice || acceptsInput
                        if (isTelevisionDevice && acceptsInput) {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (!isTelevisionDevice || !acceptsInput || event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }
                        val cursor = fieldValue.selection.end
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                val nextCursor = (cursor - 1).coerceAtLeast(0)
                                fieldValue = fieldValue.copy(selection = TextRange(nextCursor))
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                val nextCursor = (cursor + 1).coerceAtMost(fieldValue.text.length)
                                fieldValue = fieldValue.copy(selection = TextRange(nextCursor))
                                true
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged {
                        hasInputFocus = it.isFocused
                        if (it.isFocused) {
                            requestBringIntoView(120)
                        } else {
                            if (isTelevisionDevice) {
                                acceptsInput = false
                            }
                            keyboardController?.hide()
                        }
                    },
                readOnly = isTelevisionDevice && !acceptsInput
            )
        }
    }
}

@Composable
private fun LiveTvQuickFiltersDialog(
    filters: List<String>,
    onDismiss: () -> Unit,
    onAddFilter: (String) -> Unit,
    onRemoveFilter: (String) -> Unit
) {
    var pendingFilter by rememberSaveable { mutableStateOf("") }

    PremiumDialog(
        title = stringResource(R.string.settings_live_tv_quick_filters_dialog_title),
        subtitle = stringResource(R.string.settings_live_tv_quick_filters_dialog_subtitle),
        onDismissRequest = onDismiss,
        widthFraction = 0.5f,
        content = {
            EpgSourceTextField(
                value = pendingFilter,
                onValueChange = { pendingFilter = it },
                placeholder = stringResource(R.string.settings_live_tv_quick_filters_placeholder)
            )
            PremiumDialogActionButton(
                label = stringResource(R.string.settings_live_tv_quick_filters_add),
                enabled = pendingFilter.isNotBlank(),
                onClick = {
                    onAddFilter(pendingFilter)
                    pendingFilter = ""
                }
            )
            if (filters.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_live_tv_quick_filters_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_live_tv_quick_filters_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceDim
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters, key = { it }) { filter ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TvButton(onClick = { onRemoveFilter(filter) }) {
                                    Text(stringResource(R.string.settings_live_tv_quick_filters_remove))
                                }
                            }
                        }
                    }
                }
            }
        },
        footer = {
            PremiumDialogFooterButton(
                label = stringResource(R.string.settings_cancel),
                onClick = onDismiss
            )
        }
    )
}

private fun formatLiveTvQuickFiltersValue(filters: List<String>, context: android.content.Context): String {
    if (filters.isEmpty()) {
        return context.getString(R.string.settings_live_tv_quick_filters_none)
    }
    return context.resources.getQuantityString(
        R.plurals.settings_live_tv_quick_filters_count,
        filters.size,
        filters.size
    )
}

@Composable
private fun SettingsNavItem(
    label: String,
    badgeChar: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Primary.copy(alpha = 0.11f) else Color.Transparent,
            focusedContainerColor = Primary.copy(alpha = 0.22f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .mouseClickable(focusRequester = focusRequester, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(
                        color = if (isSelected) Primary else Color.Transparent,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Box(
                Modifier
                    .size(28.dp)
                    .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeChar,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Primary else OnBackground,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return "--:--"
    return java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(java.util.Date(timestampMs))
}

private data class AppLanguageOption(
    val tag: String,
    val label: String
)

private fun supportedAppLanguages(systemDefaultLabel: String): List<AppLanguageOption> {
    val localeTags = listOf("system") + supportedAppLanguageTags()

    return localeTags.map { tag ->
        AppLanguageOption(
            tag = tag,
            label = if (tag == "system") {
                systemDefaultLabel
            } else {
                val locale = localeForLanguageTag(tag)
                locale.getDisplayLanguage(locale)
                    .replaceFirstChar { character ->
                        if (character.isLowerCase()) {
                            character.titlecase(locale)
                        } else {
                            character.toString()
                        }
                    }
            }
        )
    }
}

private fun supportedAudioLanguages(autoLabel: String): List<AppLanguageOption> {
    return buildList {
        add(AppLanguageOption(tag = "auto", label = autoLabel))
        addAll(supportedAppLanguages(autoLabel).filterNot { it.tag == "system" })
    }
}

private data class SubtitleScaleOption(
    val scale: Float,
    val label: (android.content.Context) -> String
)

private data class SubtitleColorOption(
    val colorArgb: Int,
    val label: String
)

private fun subtitleSizeOptions(): List<SubtitleScaleOption> {
    return listOf(
        SubtitleScaleOption(0.85f) { it.getString(R.string.settings_subtitle_size_small) },
        SubtitleScaleOption(1f) { it.getString(R.string.settings_subtitle_size_default) },
        SubtitleScaleOption(1.15f) { it.getString(R.string.settings_subtitle_size_large) },
        SubtitleScaleOption(1.3f) { it.getString(R.string.settings_subtitle_size_extra_large) }
    )
}

private fun subtitleTextColorOptions(context: android.content.Context): List<SubtitleColorOption> {
    return listOf(
        SubtitleColorOption(0xFFFFFFFF.toInt(), context.getString(R.string.settings_subtitle_color_white)),
        SubtitleColorOption(0xFFFFEB3B.toInt(), context.getString(R.string.settings_subtitle_color_yellow)),
        SubtitleColorOption(0xFF80DEEA.toInt(), context.getString(R.string.settings_subtitle_color_cyan)),
        SubtitleColorOption(0xFFA5D6A7.toInt(), context.getString(R.string.settings_subtitle_color_green))
    )
}

private fun subtitleBackgroundColorOptions(context: android.content.Context): List<SubtitleColorOption> {
    return listOf(
        SubtitleColorOption(0x00000000, context.getString(R.string.settings_subtitle_background_transparent)),
        SubtitleColorOption(0x80000000.toInt(), context.getString(R.string.settings_subtitle_background_dim)),
        SubtitleColorOption(0xCC000000.toInt(), context.getString(R.string.settings_subtitle_background_black)),
        SubtitleColorOption(0xCC102A43.toInt(), context.getString(R.string.settings_subtitle_background_blue))
    )
}

private fun displayLanguageLabel(languageTag: String, defaultLabel: String): String {
    if (languageTag.isBlank() || languageTag == "system" || languageTag == "auto") return defaultLabel
    val locale = localeForLanguageTag(languageTag)
    if (locale.language.isBlank()) return defaultLabel
    return locale.getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase(Locale.getDefault())
            } else {
                character.toString()
            }
        }
}

private fun formatPlaybackSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${("%.2f".format(Locale.US, speed)).trimEnd('0').trimEnd('.')}x"
    }
}

@Composable
private fun RecordingMetaPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .widthIn(min = 92.dp, max = 160.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceDim)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = OnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun summarizeRecordingOutputPath(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return trimmed
    val decoded = runCatching { android.net.Uri.decode(trimmed) }.getOrDefault(trimmed)
    return decoded
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { decoded }
}

@Composable
private fun RecordingActionButton(label: String, accent: Color, onClick: () -> Unit) {
    TvButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 190.dp, max = 260.dp)
            .heightIn(min = 52.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = accent.copy(alpha = 0.14f),
            focusedContainerColor = accent.copy(alpha = 0.28f),
            contentColor = accent,
            focusedContentColor = accent
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SimpleTextValueDialog(
    title: String,
    subtitle: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    PremiumDialog(
        title = title,
        subtitle = subtitle,
        onDismissRequest = onDismiss,
        widthFraction = 0.58f,
        content = {
            EpgSourceTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = initialValue
            )
        },
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.settings_cancel),
                    onClick = onDismiss
                )
                PremiumDialogActionButton(
                    label = stringResource(R.string.settings_save),
                    onClick = { onConfirm(value.trim()) }
                )
            }
        }
    )
}

private fun formatRecordingSourceType(sourceType: RecordingSourceType): String = when (sourceType) {
    RecordingSourceType.TS -> "TS"
    RecordingSourceType.HLS -> "HLS"
    RecordingSourceType.DASH -> "DASH"
    RecordingSourceType.UNKNOWN -> "Auto"
}

private fun formatRecordingFailureCategory(category: RecordingFailureCategory): String = when (category) {
    RecordingFailureCategory.NONE -> "None"
    RecordingFailureCategory.NETWORK -> "Network"
    RecordingFailureCategory.STORAGE -> "Storage"
    RecordingFailureCategory.AUTH -> "Auth"
    RecordingFailureCategory.TOKEN_EXPIRED -> "Token"
    RecordingFailureCategory.DRM_UNSUPPORTED -> "DRM"
    RecordingFailureCategory.FORMAT_UNSUPPORTED -> "Format"
    RecordingFailureCategory.SCHEDULE_CONFLICT -> "Conflict"
    RecordingFailureCategory.PROVIDER_LIMIT -> "Connection limit"
    RecordingFailureCategory.UNKNOWN -> "Unknown"
}

private fun playerTimeoutOptions(): List<Int> = listOf(2, 3, 4, 5, 6, 8, 10, 15, 20, 30)

private fun formatDecoderModeLabel(mode: DecoderMode, context: android.content.Context): String {
    return when (mode) {
        DecoderMode.AUTO -> context.getString(R.string.settings_decoder_auto)
        DecoderMode.HARDWARE -> context.getString(R.string.settings_decoder_hardware)
        DecoderMode.SOFTWARE -> context.getString(R.string.settings_decoder_software)
    }
}

private fun formatTimeoutSecondsLabel(seconds: Int, context: android.content.Context): String {
    return context.resources.getQuantityString(
        R.plurals.settings_timeout_seconds,
        seconds,
        seconds
    )
}

private fun formatSubtitleSizeLabel(scale: Float, context: android.content.Context): String {
    return subtitleSizeOptions().firstOrNull { it.scale == scale }?.label?.invoke(context)
        ?: context.getString(R.string.settings_subtitle_size_default)
}

private fun formatSubtitleColorLabel(colorArgb: Int, options: List<SubtitleColorOption>): String {
    return options.firstOrNull { it.colorArgb == colorArgb }?.label ?: options.first().label
}

private fun formatQualityCapLabel(maxHeight: Int?, autoLabel: String): String {
    return maxHeight?.let { "${it}p" } ?: autoLabel
}

private fun formatSpeedTestValueLabel(speedTest: InternetSpeedTestUiModel): String {
    return String.format(Locale.getDefault(), "%.1f Mbps", speedTest.megabitsPerSecond)
}

private fun formatSpeedTestSummary(
    speedTest: InternetSpeedTestUiModel,
    context: android.content.Context
): String {
    val transportLabel = when (speedTest.transportLabel) {
        InternetSpeedTestTransport.WIFI.name -> context.getString(R.string.settings_speed_test_transport_wifi)
        InternetSpeedTestTransport.ETHERNET.name -> context.getString(R.string.settings_speed_test_transport_ethernet)
        InternetSpeedTestTransport.CELLULAR.name -> context.getString(R.string.settings_speed_test_transport_cellular)
        InternetSpeedTestTransport.OTHER.name -> context.getString(R.string.settings_speed_test_transport_other)
        else -> context.getString(R.string.settings_speed_test_transport_unknown)
    }
    val measuredAtLabel = formatTimestamp(speedTest.measuredAtMs)
    return if (speedTest.isEstimated) {
        context.getString(R.string.settings_speed_test_summary_estimated, transportLabel, measuredAtLabel)
    } else {
        context.getString(R.string.settings_speed_test_summary_measured, transportLabel, measuredAtLabel)
    }
}

private fun sortModeLabel(mode: CategorySortMode, context: android.content.Context): String {
    return when (mode) {
        CategorySortMode.DEFAULT -> context.getString(R.string.settings_category_sort_default)
        CategorySortMode.TITLE_ASC -> context.getString(R.string.settings_category_sort_az)
        CategorySortMode.TITLE_DESC -> context.getString(R.string.settings_category_sort_za)
        CategorySortMode.COUNT_DESC -> context.getString(R.string.settings_category_sort_most_items)
        CategorySortMode.COUNT_ASC -> context.getString(R.string.settings_category_sort_least_items)
    }
}

private fun formatCategorySortModeLabel(mode: CategorySortMode, context: android.content.Context): String {
    return sortModeLabel(mode, context)
}

private fun categoryTypeLabel(type: ContentType, context: android.content.Context): String {
    return when (type) {
        ContentType.LIVE -> context.getString(R.string.settings_category_sort_live)
        ContentType.MOVIE -> context.getString(R.string.settings_category_sort_movies)
        ContentType.SERIES -> context.getString(R.string.settings_category_sort_series)
        ContentType.SERIES_EPISODE -> context.getString(R.string.settings_category_sort_series)
    }
}

private fun categoryTypeDescription(type: ContentType, context: android.content.Context): String {
    return when (type) {
        ContentType.LIVE -> context.getString(R.string.settings_category_type_live_description)
        ContentType.MOVIE -> context.getString(R.string.settings_category_type_movies_description)
        ContentType.SERIES -> context.getString(R.string.settings_category_type_series_description)
        ContentType.SERIES_EPISODE -> context.getString(R.string.settings_category_type_series_description)
    }
}

private fun LiveTvChannelMode.labelResId(): Int = when (this) {
    LiveTvChannelMode.COMFORTABLE -> R.string.settings_live_tv_mode_comfortable
    LiveTvChannelMode.COMPACT -> R.string.settings_live_tv_mode_compact
    LiveTvChannelMode.PRO -> R.string.settings_live_tv_mode_pro
}

private fun LiveTvChannelMode.descriptionResId(): Int = when (this) {
    LiveTvChannelMode.COMFORTABLE -> R.string.settings_live_tv_mode_comfortable_desc
    LiveTvChannelMode.COMPACT -> R.string.settings_live_tv_mode_compact_desc
    LiveTvChannelMode.PRO -> R.string.settings_live_tv_mode_pro_desc
}

private fun LiveTvQuickFilterVisibilityMode.labelResId(): Int = when (this) {
    LiveTvQuickFilterVisibilityMode.HIDE -> R.string.settings_live_tv_quick_filter_visibility_hide
    LiveTvQuickFilterVisibilityMode.SHOW_WHEN_FILTERS_AVAILABLE -> R.string.settings_live_tv_quick_filter_visibility_available
    LiveTvQuickFilterVisibilityMode.ALWAYS_VISIBLE -> R.string.settings_live_tv_quick_filter_visibility_always
}

private fun LiveTvQuickFilterVisibilityMode.descriptionResId(): Int = when (this) {
    LiveTvQuickFilterVisibilityMode.HIDE -> R.string.settings_live_tv_quick_filter_visibility_hide_desc
    LiveTvQuickFilterVisibilityMode.SHOW_WHEN_FILTERS_AVAILABLE -> R.string.settings_live_tv_quick_filter_visibility_available_desc
    LiveTvQuickFilterVisibilityMode.ALWAYS_VISIBLE -> R.string.settings_live_tv_quick_filter_visibility_always_desc
}

private fun VodViewMode.labelResId(): Int = when (this) {
    VodViewMode.MODERN -> R.string.settings_vod_view_mode_modern
    VodViewMode.CLASSIC -> R.string.settings_vod_view_mode_classic
}

private fun VodViewMode.descriptionResId(): Int = when (this) {
    VodViewMode.MODERN -> R.string.settings_vod_view_mode_modern_desc
    VodViewMode.CLASSIC -> R.string.settings_vod_view_mode_classic_desc
}

private fun ChannelNumberingMode.labelResId(): Int = when (this) {
    ChannelNumberingMode.GROUP -> R.string.settings_live_channel_numbering_group
    ChannelNumberingMode.PROVIDER -> R.string.settings_live_channel_numbering_provider
}

private fun ChannelNumberingMode.descriptionResId(): Int = when (this) {
    ChannelNumberingMode.GROUP -> R.string.settings_live_channel_numbering_group_desc
    ChannelNumberingMode.PROVIDER -> R.string.settings_live_channel_numbering_provider_desc
}

