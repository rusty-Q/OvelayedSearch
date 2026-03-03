package com.overlayed.search

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    private lateinit var prefs: SharedPreferences
    private var isReturningFromSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверяем, возвращаемся ли мы из настроек
        isReturningFromSettings = intent.getBooleanExtra("returning", false)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Проверяем разрешение
        if (checkOverlayPermission()) {
            // Если разрешение есть - сразу запускаем сервис
            startFloatingWindowService()
            finish()
        } else if (!isReturningFromSettings) {
            // Первый запуск - пытаемся открыть настройки
            openAppSettings()
        } else {
            // Возвращаемся из настроек, но разрешения все еще нет - показываем инструкцию
            showManualInstructions()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        intent.putExtra("returning", true)

        try {
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            showManualInstructions()
        }
    }

    private fun showManualInstructions() {
        // Делаем Activity видимым, устанавливая layout
        setContentView(android.R.layout.simple_list_item_1) // Простой layout чтобы Activity был видим

        AlertDialog.Builder(this)
            .setTitle("⚠️ Требуется разрешение")
            .setMessage("""
                Для работы плавающего окна поиска необходимо разрешение "Поверх других окон".
                
                👆 Нажмите "Открыть настройки", затем:
                
                1. Выберите "Разрешения"
                2. Найдите "Поверх других окон" 
                3. Включите разрешение
                4. Вернитесь в приложение
            """.trimIndent())
            .setPositiveButton("Открыть настройки") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Выйти") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (checkOverlayPermission()) {
                Toast.makeText(this, "✅ Разрешение получено!", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    startFloatingWindowService()
                    finish()
                }, 100)
            } else {
                // Разрешение не получено - показываем инструкцию
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("returning", true)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}