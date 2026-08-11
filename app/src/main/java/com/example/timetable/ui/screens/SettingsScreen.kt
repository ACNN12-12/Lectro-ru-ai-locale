package com.example.timetable.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import android.widget.Toast
import com.example.timetable.R
import com.example.timetable.model.Week
import com.example.timetable.utils.AppConstants
import com.example.timetable.utils.DbHelper
import com.example.timetable.utils.ScheduleExporter
import com.example.timetable.utils.SemesterArchiveManager
import com.example.timetable.utils.TimeUtils
import com.example.timetable.utils.WakeUpAlarmReceiver
import java.text.SimpleDateFormat
import java.util.*

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = DbHelper(application)
    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(application)
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var sevenDaysEnabled by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_SEVEN_DAYS_SETTING, false))
    var personalDetailsEnabled by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_PERSONAL_DETAILS_SETTING, true))
    var schoolWebsite by mutableStateOf(sharedPref.getString(AppConstants.KEY_SCHOOL_WEBSITE_SETTING, "") ?: "")

    var notificationsEnabled by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_NOTIFICATIONS_ENABLED, false))
    var scheduleReminder by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_SCHEDULE_REMINDER, true))
    var assignmentReminder by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_ASSIGNMENT_REMINDER, true))
    var examReminder by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_EXAM_REMINDER, true))
    var attendanceAlert by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_ATTENDANCE_ALERT, true))
    var autoSilentEnabled by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_AUTO_SILENT_ENABLED, false))
    var silentMediaVolume by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_SILENT_MEDIA_VOLUME, true))
    var silentRingNotificationVolume by mutableStateOf(sharedPref.getBoolean(AppConstants.KEY_SILENT_RING_NOTIFICATION_VOLUME, true))

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun updateSevenDays(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_SEVEN_DAYS_SETTING, enabled).apply()
        sevenDaysEnabled = enabled
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        notificationsEnabled = enabled
        if (enabled) {
            WakeUpAlarmReceiver.scheduleAlarm(getApplication())
        } else {
            WakeUpAlarmReceiver.cancelAlarm(getApplication())
        }
    }

    fun updateScheduleReminder(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_SCHEDULE_REMINDER, enabled).apply()
        scheduleReminder = enabled
    }

    fun updateAssignmentReminder(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_ASSIGNMENT_REMINDER, enabled).apply()
        assignmentReminder = enabled
    }

    fun updateExamReminder(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_EXAM_REMINDER, enabled).apply()
        examReminder = enabled
    }

    fun updateAttendanceAlert(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_ATTENDANCE_ALERT, enabled).apply()
        attendanceAlert = enabled
    }

    fun updateAutoSilentEnabled(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_AUTO_SILENT_ENABLED, enabled).apply()
        autoSilentEnabled = enabled
        if (enabled) {
            WakeUpAlarmReceiver.scheduleAlarm(getApplication())
        } else {
            // Restore volume immediately if disabled
            val notificationHelper = com.example.timetable.utils.NotificationHelper(getApplication())
            notificationHelper.setSilentMode(false)
        }
    }

    fun updateSilentMediaVolume(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_SILENT_MEDIA_VOLUME, enabled).apply()
        silentMediaVolume = enabled
    }

    fun updateSilentRingNotificationVolume(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_SILENT_RING_NOTIFICATION_VOLUME, enabled).apply()
        silentRingNotificationVolume = enabled
    }

    fun updatePersonalDetails(enabled: Boolean) {
        sharedPref.edit().putBoolean(AppConstants.KEY_PERSONAL_DETAILS_SETTING, enabled).apply()
        personalDetailsEnabled = enabled
    }

    fun updateSchoolWebsite(url: String) {
        sharedPref.edit().putString(AppConstants.KEY_SCHOOL_WEBSITE_SETTING, url).apply()
        schoolWebsite = url
    }

    fun archiveSemester() {
        db.removeFullSchedule()
    }

    fun resetData() {
        db.resetAllData()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToArchives: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var resetType by remember { mutableStateOf<ResetType?>(null) }
    var archiveName by remember { mutableStateOf("") }
    var showConflictDialog by remember { mutableStateOf<List<Pair<Week, Week>>?>(null) }
    var pendingImportWeeks by remember { mutableStateOf<List<Week>?>(null) }

    var shouldClearAfterExport by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    ScheduleExporter.exportSchedule(context, os)
                    Toast.makeText(context, "Расписание экспортировано в файл .lec", Toast.LENGTH_SHORT).show()
                    if (shouldClearAfterExport) {
                        viewModel.archiveSemester()
                        shouldClearAfterExport = false
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                shouldClearAfterExport = false
            }
        } ?: run {
            shouldClearAfterExport = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { `is` ->
                    val weeks = ScheduleExporter.parseLecFile(`is`)
                    val conflicts = ScheduleExporter.findConflicts(context, weeks)
                    if (conflicts.isNotEmpty()) {
                        pendingImportWeeks = weeks
                        showConflictDialog = conflicts
                    } else {
                        ScheduleExporter.importWeeks(context, weeks)
                        Toast.makeText(context, "Расписание успешно импортировано", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showConflictDialog != null && pendingImportWeeks != null) {
        AlertDialog(
            onDismissRequest = { 
                showConflictDialog = null
                pendingImportWeeks = null
            },
            title = { Text("Конфликты расписания") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Следующие занятия в файле накладываются на ваше текущее расписание. Выберите, какую версию сохранить.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val conflictsByDay = showConflictDialog!!.groupBy { it.first.fragment }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        conflictsByDay.forEach { (day, dayConflicts) ->
                            val dayTranslation = mapOf(
                                "Monday" to "Понедельник",
                                "Tuesday" to "Вторник",
                                "Wednesday" to "Среда",
                                "Thursday" to "Четверг",
                                "Friday" to "Пятница",
                                "Saturday" to "Суббота",
                                "Sunday" to "Воскресенье"
                            )
                            Column {
                                Text(
                                    text = dayTranslation[day] ?: day ?: "Неизвестный день",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                dayConflicts.forEach { (newW, existing) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Текущее: ${existing.subject}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                text = "${TimeUtils.formatTo12Hour(existing.fromTime)} - ${TimeUtils.formatTo12Hour(existing.toTime)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                            
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Импорт: ${newW.subject}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = "${TimeUtils.formatTo12Hour(newW.fromTime)} - ${TimeUtils.formatTo12Hour(newW.toTime)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val db = DbHelper(context)
                    val existingToReplace: List<Week> = showConflictDialog!!.map { it.second }
                    existingToReplace.forEach { db.deleteWeekById(it) }
                    ScheduleExporter.importWeeks(context, pendingImportWeeks!!)
                    Toast.makeText(context, "Новое расписание импортировано (пересекающиеся занятия заменены)", Toast.LENGTH_SHORT).show()
                    showConflictDialog = null
                    pendingImportWeeks = null
                }) {
                    Text("Сохранить новое")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clashingNew: List<Week> = showConflictDialog!!.map { it.first }
                    val nonClashing = pendingImportWeeks!!.filter { it !in clashingNew }
                    if (nonClashing.isNotEmpty()) {
                        ScheduleExporter.importWeeks(context, nonClashing)
                        Toast.makeText(context, "Импортированы только непересекающиеся занятия", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Все занятия пересекаются, ничего не импортировано", Toast.LENGTH_SHORT).show()
                    }
                    showConflictDialog = null
                    pendingImportWeeks = null
                }) {
                    Text("Оставить текущее")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateNotificationsEnabled(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Оформление") {
                SettingsItem(
                    title = stringResource(R.string.sevendays_setting),
                    control = {
                        Switch(
                            checked = viewModel.sevenDaysEnabled,
                            onCheckedChange = { viewModel.updateSevenDays(it) }
                        )
                    }
                )
                SettingsItem(
                    title = stringResource(R.string.enable_personal_details),
                    control = {
                        Switch(
                            checked = viewModel.personalDetailsEnabled,
                            onCheckedChange = { viewModel.updatePersonalDetails(it) }
                        )
                    }
                )
            }

            HorizontalDivider()

            SettingsSection(title = "Настройки") {
                OutlinedTextField(
                    value = viewModel.schoolWebsite,
                    onValueChange = { viewModel.updateSchoolWebsite(it) },
                    label = { Text(stringResource(R.string.school_website_setting)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://site-vuza.ru") }
                )
                Text(
                    text = stringResource(R.string.school_website_setting_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            HorizontalDivider()

            SettingsSection(title = "Уведомления") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItem(
                            title = "Включить уведомления",
                            control = {
                                Switch(
                                    checked = viewModel.notificationsEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                return@Switch
                                            }
                                        }
                                        viewModel.updateNotificationsEnabled(checked)
                                    }
                                )
                            }
                        )

                        AnimatedVisibility(
                            visible = viewModel.notificationsEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (!viewModel.canScheduleExactAlarms()) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Отсутствует разрешение на точные будильники. Уведомления могут приходить с задержкой.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            TextButton(
                                                onClick = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                            data = Uri.fromParts("package", context.packageName, null)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Предоставить разрешение", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }

                                SettingsItem(
                                    title = "Напоминание о парах",
                                    control = {
                                        Switch(
                                            checked = viewModel.scheduleReminder,
                                            onCheckedChange = { viewModel.updateScheduleReminder(it) }
                                        )
                                    }
                                )
                                SettingsItem(
                                    title = "Напоминание о заданиях",
                                    control = {
                                        Switch(
                                            checked = viewModel.assignmentReminder,
                                            onCheckedChange = { viewModel.updateAssignmentReminder(it) }
                                        )
                                    }
                                )
                                SettingsItem(
                                    title = "Напоминание об экзаменах",
                                    control = {
                                        Switch(
                                            checked = viewModel.examReminder,
                                            onCheckedChange = { viewModel.updateExamReminder(it) }
                                        )
                                    }
                                )
                                SettingsItem(
                                    title = "Предупреждения о посещаемости",
                                    control = {
                                        Switch(
                                            checked = viewModel.attendanceAlert,
                                            onCheckedChange = { viewModel.updateAttendanceAlert(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Авто-беззвучный режим") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItem(
                            title = "Беззвучный режим во время пар",
                            control = {
                                Switch(
                                    checked = viewModel.autoSilentEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                                context.startActivity(intent)
                                                Toast.makeText(context, "Пожалуйста, предоставьте доступ к режиму 'Не беспокоить', чтобы использовать эту функцию", Toast.LENGTH_LONG).show()
                                                return@Switch
                                            }
                                        }
                                        viewModel.updateAutoSilentEnabled(checked)
                                    }
                                )
                            }
                        )

                        AnimatedVisibility(
                            visible = viewModel.autoSilentEnabled,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SettingsItem(
                                    title = "Громкость медиа",
                                    control = {
                                        Switch(
                                            checked = viewModel.silentMediaVolume,
                                            onCheckedChange = { viewModel.updateSilentMediaVolume(it) }
                                        )
                                    }
                                )
                                SettingsItem(
                                    title = "Громкость звонка и уведомлений",
                                    control = {
                                        Switch(
                                            checked = viewModel.silentRingNotificationVolume,
                                            onCheckedChange = { viewModel.updateSilentRingNotificationVolume(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Резервное копирование") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Импортируйте или экспортируйте свое расписание в виде файла .lec, чтобы поделиться им или сохранить резервную копию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("timetable.lec") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Экспорт расписания")
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Импорт расписания")
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Опасная зона") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Archive Semester
                    DangerCard(
                        title = "Архивировать текущий семестр",
                        description = "Сохраняет все текущие данные (расписание, предметы, заметки, экзамены, задания, посещаемость) в архив и начинает новый семестр с чистого листа.",
                        buttonText = "Архивировать семестр",
                        onClick = { 
                            val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                            archiveName = "Семестр, завершенный в ${sdf.format(Date())}"
                            resetType = ResetType.ARCHIVE 
                        }
                    )

                    val archives = remember(resetType) { SemesterArchiveManager.getArchives(context) }
                    if (archives.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onNavigateToArchives,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Посмотреть архивы семестров (${archives.size})")
                        }
                    }

                    // Reset All Data
                    DangerCard(
                        title = stringResource(R.string.reset_data),
                        description = "Удаляет все данные приложения (расписание, предметы, домашние работы, заметки, историю посещаемости), но сохраняет ваши личные данные профиля.",
                        buttonText = stringResource(R.string.reset_data),
                        onClick = { resetType = ResetType.ALL }
                    )
                }
            }
        }
    }

    resetType?.let { type ->
        val title = when (type) {
            ResetType.ARCHIVE -> "Архивировать текущий семестр?"
            ResetType.ALL -> stringResource(R.string.reset_data)
        }
        val message = when (type) {
            ResetType.ARCHIVE -> "Это сохранит ВСЕ ваши текущие данные в архив истории приложения. Данное действие очистит ваше текущее расписание и предметы."
            ResetType.ALL -> stringResource(R.string.reset_warning)
        }

        AlertDialog(
            onDismissRequest = { resetType = null },
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    if (type == ResetType.ARCHIVE) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = archiveName,
                            onValueChange = { archiveName = it },
                            label = { Text("Название архива (например, Осенний семестр 2026)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (type) {
                            ResetType.ARCHIVE -> {
                                if (archiveName.isBlank()) {
                                    Toast.makeText(context, "Пожалуйста, введите название архива", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                val success = SemesterArchiveManager.archiveCurrentSemester(context, archiveName)
                                if (success) {
                                    Toast.makeText(context, "Семестр успешно архивирован!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Не удалось архивировать семестр", Toast.LENGTH_SHORT).show()
                                }
                            }
                            ResetType.ALL -> viewModel.resetData()
                        }
                        resetType = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { resetType = null }) { Text(stringResource(R.string.no)) }
            }
        )
    }
}

enum class ResetType { ARCHIVE, ALL }

@Composable
fun DangerCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(title: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        control()
    }
}
