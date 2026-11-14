package com.klisee.reloaded

import android.annotation.SuppressLint
import android.app.NativeActivity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.klisee.reloaded.gmk.AppSettings

private const val TAG = "ReloadedIme"

class GameActivity : NativeActivity() {
    private val settings by lazy { AppSettings(filesDir) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var focusRequest: AudioFocusRequest? = null
    private var focusRetries = 0
    private var resumed = false
    private var imeAnchor: EditText? = null
    private var loadingOverlay: LoadingOverlay? = null
    private val loadingGate = LoadingOverlayGate()
    private val loadingTimeout = Runnable { dismissLoadingOverlay() }

    @Volatile
    private var gameReadySignaled = false

    private val focusRetry = object : Runnable {
        override fun run() {
            if (!resumed || isFinishing || isDestroyed || focusRequest != null) return
            // Never fight an app that is still playing. Once it pauses/abandons focus,
            // ask again so our AAudio stream can resume.
            if (!audioManager.isMusicActive) {
                requestAudioFocus()
                if (focusRequest != null) {
                    focusRetries = 0
                    return
                }
            }
            if (++focusRetries <= 15) mainHandler.postDelayed(this, 1000)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss: release the request, then retry when the other app stops.
                nativeAudioFocus(true)
                abandonAudioFocus()
                focusRetries = 0
                mainHandler.removeCallbacks(focusRetry)
                mainHandler.postDelayed(focusRetry, 1000)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> nativeAudioFocus(true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> nativeAudioFocus(false)
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                focusRetries = 0
                mainHandler.removeCallbacks(focusRetry)
                nativeAudioFocus(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        focusRetries = 0
        window.decorView.post { requestAudioFocus() }
    }

    private fun requestAudioFocus() {
        if (!resumed || focusRequest != null) return
        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            val result = audioManager.requestAudioFocus(request)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
                result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            ) {
                focusRequest = request
            }
        }
    }

    private fun abandonAudioFocus() {
        runCatching { focusRequest?.let { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    override fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(focusRetry)
        abandonAudioFocus()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadingHost = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val rate = when (settings.fpsMode) {
                "60" -> 60f
                "120", "uncapped" -> detectedRefreshRate()
                else -> 0f
            }
            if (rate > 0f) {
                val attrs = window.attributes
                attrs.preferredRefreshRate = rate
                window.attributes = attrs
            }
        }
        super.onCreate(savedInstanceState)
        instance = this
        installLoadingOverlay()
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        installImeAnchor()
        watchImeHeight()
        hideSystemBars()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(loadingTimeout)
        loadingGate.destroy()
        loadingOverlay?.destroy()
        loadingOverlay = null
        if (loadingHost === this) loadingHost = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun installLoadingOverlay() {
        if (isFinishing || isDestroyed) return
        if (gameReadySignaled) {
            loadingGate.finish()
            return
        }
        if (!loadingGate.show()) return
        val overlay = LoadingOverlay(this)
        loadingOverlay = overlay
        overlay.show()
        mainHandler.postDelayed(loadingTimeout, LoadingOverlayGate.MAX_WAIT_MILLIS)
    }

    private fun dismissLoadingOverlay() {
        mainHandler.removeCallbacks(loadingTimeout)
        if (!loadingGate.finish()) return
        val overlay = loadingOverlay ?: return
        loadingOverlay = null
        if (isFinishing || isDestroyed) overlay.destroy() else overlay.dismiss()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun detectedRefreshRate(): Float =
        runCatching { windowManager.defaultDisplay.refreshRate }.getOrDefault(60f)

    private fun installImeAnchor() {
        val anchor = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
                outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    EditorInfo.IME_FLAG_NO_FULLSCREEN
                return ForwardingInputConnection(this)
            }
        }
        anchor.isFocusable = true
        anchor.isFocusableInTouchMode = true
        anchor.alpha = 0f
        addContentView(anchor, ViewGroup.LayoutParams(1, 1))
        imeAnchor = anchor
    }

    private inner class ForwardingInputConnection(target: View) : BaseInputConnection(target, false) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!text.isNullOrEmpty()) nativeCommitText(text.toString())
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { nativeBackspace() }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    nativeBackspace()
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    nativeEnter()
                    return true
                }
            }
            return super.sendKeyEvent(event)
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            nativeEnter()
            return true
        }
    }

    private fun watchImeHeight() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            reportImeHeight(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            insets
        }
    }

    private fun currentImeHeight(): Int =
        ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    private fun reportImeHeight(bottom: Int) {
        Log.i(TAG, "altura del teclado = $bottom")
        nativeImeInset(bottom)
    }

    private fun applyImeVisible(visible: Boolean) {
        val anchor = imeAnchor ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (visible) {
            anchor.requestFocus()
            imm.showSoftInput(anchor, 0)
            anchor.postDelayed({
                if (currentImeHeight() == 0) imm.showSoftInput(anchor, 0)
                reportImeHeight(currentImeHeight())
            }, 400)
        } else {
            imm.hideSoftInputFromWindow(anchor.windowToken, 0)
            anchor.clearFocus()
        }
    }

    external fun nativeAudioFocus(lost: Boolean)
    external fun nativeCommitText(text: String)
    external fun nativeBackspace()
    external fun nativeEnter()
    external fun nativeImeInset(height: Int)

    companion object {
        @JvmStatic
        fun touchControlPressed() {
            val activity = instance ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()) {
                    activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }

        @JvmStatic
        fun showTouchHint() {
            val activity = instance ?: return
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                Toast.makeText(activity, "Toca una pieza y pulsa Girar para rotarla.", Toast.LENGTH_LONG).show()
            }
        }

        init {
            System.loadLibrary("gm8emulator")
        }

        @Volatile
        private var instance: GameActivity? = null

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var loadingHost: GameActivity? = null

        @JvmStatic
        fun gameReady() {
            val activity = loadingHost ?: instance ?: return
            activity.gameReadySignaled = true
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.dismissLoadingOverlay()
                }
            }
        }

        @JvmStatic
        fun setImeVisible(visible: Boolean) {
            val activity = instance ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) activity.applyImeVisible(visible)
            }
        }

        @JvmStatic
        fun refreshImeHeight() {
            val activity = instance ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.reportImeHeight(activity.currentImeHeight())
                }
            }
        }

        @JvmStatic
        fun onGameRoute(@Suppress("UNUSED_PARAMETER") roomId: Int, @Suppress("UNUSED_PARAMETER") route: String?) = Unit

        @JvmStatic
        fun onGameCharacter(
            @Suppress("UNUSED_PARAMETER") prota: Int,
            @Suppress("UNUSED_PARAMETER") outfit: Int,
            @Suppress("UNUSED_PARAMETER") char2: Int,
        ) = Unit

        @JvmStatic
        fun onGamePlayerName(@Suppress("UNUSED_PARAMETER") name: String?) = Unit

        @JvmStatic
        fun onGameBattle(
            @Suppress("UNUSED_PARAMETER") inBattle: Boolean,
            @Suppress("UNUSED_PARAMETER") trainer: String?,
            @Suppress("UNUSED_PARAMETER") species: Int,
        ) = Unit
    }
}
