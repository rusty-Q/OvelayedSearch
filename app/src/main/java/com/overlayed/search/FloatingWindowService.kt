package com.overlayed.search

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var currentProvider = "google"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "floating_search_channel"
    private val BOTTOM_OFFSET = 450
    private val KEYBOARD_OFFSET = 20
    private val KEYBOARD_HEIGHT_ESTIMATE = 1200 // Увеличили с 800 до 1200

    private var isDragging = false
    private var wasMovedByUser = false
    private var lastKnownY: Int = 0
    private var screenHeight = 0
    private var windowHeight = 0

    private val providers = mapOf(
        "google" to "https://www.google.com/search?q=",
        "yandex" to "https://yandex.ru/search/?text=",
        "duckduckgo" to "https://duckduckgo.com/?q=",
        "bing" to "https://www.bing.com/search?q="
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Search Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating search window"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Search")
            .setContentText("Search window is active")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun moveWindowAboveKeyboard() {
        if (wasMovedByUser) return

        // Поднимаем окно высоко над клавиатурой
        val targetY = screenHeight - windowHeight - KEYBOARD_HEIGHT_ESTIMATE - KEYBOARD_OFFSET

        // Не даем уехать слишком высоко (минимум 50px от верха)
        val finalY = targetY.coerceAtLeast(50)

        if (params.y != finalY) {
            params.y = finalY
            windowManager.updateViewLayout(floatingView, params)
            lastKnownY = finalY
            println("Окно поднято на Y=$finalY")
        }
    }

    private fun moveWindowToDefaultPosition() {
        if (wasMovedByUser) return

        val targetY = screenHeight - windowHeight - BOTTOM_OFFSET

        if (params.y != targetY) {
            params.y = targetY
            windowManager.updateViewLayout(floatingView, params)
            lastKnownY = targetY
            println("Окно опущено на Y=$targetY")
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingView() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_window, null)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val windowWidth = (screenWidth * 0.8).toInt().coerceAtMost(1000)

        floatingView.measure(
            View.MeasureSpec.makeMeasureSpec(windowWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        windowHeight = floatingView.measuredHeight

        params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                windowWidth,
                windowHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }

        params.gravity = Gravity.TOP or Gravity.START
        params.x = (screenWidth - windowWidth) / 2
        params.y = screenHeight - windowHeight - BOTTOM_OFFSET
        lastKnownY = params.y

        val etSearch = floatingView.findViewById<EditText>(R.id.etSearch)
        val btnSettings = floatingView.findViewById<ImageView>(R.id.btnSettings)
        val btnClose = floatingView.findViewById<ImageView>(R.id.btnClose)
        val dragHandle = floatingView.findViewById<View>(R.id.dragHandle)

        val prefs = getSharedPreferences("search_prefs", MODE_PRIVATE)
        currentProvider = prefs.getString("provider", "google") ?: "google"

        updateSearchHint(etSearch)

        // Обработка поиска
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotBlank()) {
                    performSearch(query)
                }
                true
            } else false
        }

        // При фокусе - поднимаем окно и показываем клавиатуру
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Делаем окно фокусируемым
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(floatingView, params)

                // Поднимаем окно над клавиатурой (ВЫШЕ)
                if (!wasMovedByUser) {
                    moveWindowAboveKeyboard()
                }

                // Показываем клавиатуру с задержкой
                etSearch.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            } else {
                // Возвращаем флаг
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)

                // Возвращаем в исходную позицию
                if (!wasMovedByUser) {
                    moveWindowToDefaultPosition()
                }
            }
        }

        // При клике на поле
        etSearch.setOnClickListener {
            etSearch.requestFocus()
        }

        btnSettings.setOnClickListener { view ->
            showCustomPopupMenu(view, etSearch)
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        // Перетаскивание окна
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    // Ограничиваем по вертикали
                    params.y = params.y.coerceIn(0, screenHeight - windowHeight)

                    windowManager.updateViewLayout(floatingView, params)
                    lastKnownY = params.y
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    wasMovedByUser = true
                    lastKnownY = params.y
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)

        // Автоматический фокус и поднятие
        etSearch.postDelayed({
            etSearch.requestFocus()
        }, 500)
    }

    private fun showCustomPopupMenu(anchor: View, editText: EditText) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)

        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            popupWindow.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }

        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        val btnGoogle = popupView.findViewById<TextView>(R.id.btnGoogle)
        val btnYandex = popupView.findViewById<TextView>(R.id.btnYandex)
        val btnDuck = popupView.findViewById<TextView>(R.id.btnDuck)
        val btnBing = popupView.findViewById<TextView>(R.id.btnBing)

        btnGoogle.setOnClickListener {
            currentProvider = "google"
            editText.hint = "Поиск в Google..."
            saveProvider()
            popupWindow.dismiss()
        }

        btnYandex.setOnClickListener {
            currentProvider = "yandex"
            editText.hint = "Поиск в Яндекс..."
            saveProvider()
            popupWindow.dismiss()
        }

        btnDuck.setOnClickListener {
            currentProvider = "duckduckgo"
            editText.hint = "Поиск в DuckDuckGo..."
            saveProvider()
            popupWindow.dismiss()
        }

        btnBing.setOnClickListener {
            currentProvider = "bing"
            editText.hint = "Поиск в Bing..."
            saveProvider()
            popupWindow.dismiss()
        }

        // Получаем координаты anchor view
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

        // Измеряем popupView
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        // Корректируем позицию по горизонтали
        var xOffset = anchorX - popupWidth + anchor.width
        if (xOffset < 0) {
            xOffset = anchorX
        }

        // Проверяем, не уходит ли меню за нижний край
        var yOffset = anchorY + anchor.height
        if (yOffset + popupHeight > screenHeight) {
            yOffset = anchorY - popupHeight // показываем над кнопкой
        }

        popupWindow.showAtLocation(
            floatingView,
            Gravity.NO_GRAVITY,
            xOffset,
            yOffset
        )
    }

    private fun saveProvider() {
        val prefs = getSharedPreferences("search_prefs", MODE_PRIVATE)
        prefs.edit().putString("provider", currentProvider).apply()
    }

    private fun updateSearchHint(editText: EditText) {
        val hint = when (currentProvider) {
            "google" -> "Поиск в Google..."
            "yandex" -> "Поиск в Яндекс..."
            "duckduckgo" -> "Поиск в DuckDuckGo..."
            "bing" -> "Поиск в Bing..."
            else -> "Поиск..."
        }
        editText.hint = hint
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(floatingView.windowToken, 0)

        val encodedQuery = Uri.encode(query, "UTF-8")
        val url = providers[currentProvider] + encodedQuery

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        floatingView.postDelayed({
            stopSelf()
        }, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}