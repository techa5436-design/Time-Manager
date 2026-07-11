package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Task
import com.example.data.TaskRepository
import com.example.receiver.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskViewModel(
    application: Application,
    private val repository: TaskRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Observe tasks for the selected date reactively
    val currentDayTasks: StateFlow<List<Task>> = _selectedDate
        .combine(repository.allTasks) { date, all ->
            all.filter { it.date == date }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Observe historical tasks (tasks before today's date) reactively
    val historicalTasks: StateFlow<List<Task>> = repository.allTasks
        .combine(_selectedDate) { all, _ ->
            val today = getTodayDateString()
            all.filter { it.date < today }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Lifetime score (total completed tasks * 10)
    val lifetimeScore: StateFlow<Int> = repository.allTasks
        .combine(_selectedDate) { all, _ ->
            all.count { it.status == "COMPLETED" } * 10
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun addTask(title: String, time: String, isAlarmEnabled: Boolean) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val task = Task(
                title = title,
                date = date,
                time = time,
                isAlarmEnabled = isAlarmEnabled,
                status = "PENDING"
            )
            val generatedId = repository.insert(task)
            val insertedTask = task.copy(id = generatedId.toInt())
            if (isAlarmEnabled) {
                AlarmScheduler.scheduleAlarm(context, insertedTask)
            }
        }
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(status = "COMPLETED")
            repository.update(updated)
            AlarmScheduler.cancelAlarm(context, task)
        }
    }

    fun failTask(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(status = "FAILED")
            repository.update(updated)
            AlarmScheduler.cancelAlarm(context, task)
        }
    }

    fun resetTask(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(status = "PENDING")
            repository.update(updated)
            if (updated.isAlarmEnabled) {
                AlarmScheduler.scheduleAlarm(context, updated)
            }
        }
    }

    fun toggleAlarm(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(isAlarmEnabled = !task.isAlarmEnabled)
            repository.update(updated)
            if (updated.isAlarmEnabled && updated.status == "PENDING") {
                AlarmScheduler.scheduleAlarm(context, updated)
            } else {
                AlarmScheduler.cancelAlarm(context, task)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
            AlarmScheduler.cancelAlarm(context, task)
        }
    }

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getReadableDateString(dateStr: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(dateStr) ?: return dateStr
                val readableSdf = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())
                readableSdf.format(date)
            } catch (e: Exception) {
                dateStr
            }
        }
    }
}

class TaskViewModelFactory(
    private val application: Application,
    private val repository: TaskRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
