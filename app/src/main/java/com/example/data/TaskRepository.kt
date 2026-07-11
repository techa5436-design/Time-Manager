package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    fun getTasksByDate(date: String): Flow<List<Task>> = taskDao.getTasksByDate(date)

    fun getHistoricalTasks(date: String): Flow<List<Task>> = taskDao.getHistoricalTasks(date)

    suspend fun getTaskById(id: Int): Task? = taskDao.getTaskById(id)

    suspend fun insert(task: Task): Long = taskDao.insertTask(task)

    suspend fun update(task: Task) = taskDao.updateTask(task)

    suspend fun delete(task: Task) = taskDao.deleteTask(task)

    suspend fun deleteById(id: Int) = taskDao.deleteTaskById(id)
}
