package com.exemplo.nuvem

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

class IslandService : Service() {
    // ---- Ajustes rápidos (em dp) ----
    private val larguraRepouso = 90
    private val alturaRepouso = 26
    private val larguraAberta = 310
    private val alturaAberta = 60
    private val margemTopo = 6        // distância do topo da tela
    private val tempoAberta = 5000L   // ms até recolher sozinha

    private lateinit var wm: WindowManager
    private lateinit var raiz: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var ponto: View
    private lateinit var info: LinearLayout
    private lateinit var titulo: TextView
    private lateinit var sub: TextView
    private lateinit var botao: TextView

    private val h = Handler(Looper.getMainLooper())
    private val recolher = Runnable { fechar() }
    private var aberta = false
    private var ctrl: MediaController? = null
    private var tocando = false
    private var tituloMidia = ""
    private var artistaMidia = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nuvem", "Nuvem", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "nuvem")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nuvem ativa").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        montarView()

        IslandBus.onNotificacao = { e -> abrir(e.titulo, e.texto, false) }
        IslandBus.onMidia = { t, a, p, c ->
            val mudou = t != tituloMidia
            ctrl = c; tituloMidia = t; artistaMidia = a; tocando = p
            botao.text = if (p) "❚❚" else "▶"
            if (t.isNotEmpty() && p && mudou) abrir(t, a, true)
        }
    }

    private fun montarView() {
        wm = getSystemService(WindowManager::class.java)

        val fundo = GradientDrawable().apply {
            setColor(Color.parseColor("#0B0D14"))
            cornerRadius = dp(40).toFloat()
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = fundo
            setPadding(dp(14), 0, dp(10), 0)
            clipToOutline = true
        }

        ponto = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#38E0C0")) }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
        }
        titulo = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        sub = TextView(this).apply {
            setTextColor(Color.parseColor("#B3FFFFFF")); textSize = 12f
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titulo); addView(sub)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        botao = TextView(this).apply {
            text = "❚❚"; setTextColor(Color.WHITE); textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33FFFFFF")) }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                ctrl?.transportControls?.let { if (tocando) it.pause() else it.play() }
                h.removeCallbacks(recolher); h.postDelayed(recolher, tempoAberta)
            }
        }
        raiz.addView(ponto); raiz.addView(info); raiz.addView(botao)
        info.visibility = View.GONE; botao.visibility = View.GONE

        raiz.setOnClickListener {
            if (aberta) fechar()
            else if (tituloMidia.isNotEmpty()) abrir(tituloMidia, artistaMidia, true)
        }

        lp = WindowManager.LayoutParams(
            dp(larguraRepouso), dp(alturaRepouso),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(margemTopo) }
        wm.addView(raiz, lp)
    }

    private fun abrir(t: String, s: String, midia: Boolean) {
        titulo.text = t; sub.text = s
        botao.visibility = if (midia && ctrl != null) View.VISIBLE else View.GONE
        ponto.visibility = View.GONE; info.visibility = View.VISIBLE
        aberta = true
        animar(dp(larguraAberta), dp(alturaAberta))
        h.removeCallbacks(recolher); h.postDelayed(recolher, tempoAberta)
    }

    private fun fechar() {
        h.removeCallbacks(recolher)
        aberta = false
        info.visibility = View.GONE; botao.visibility = View.GONE; ponto.visibility = View.VISIBLE
        animar(dp(larguraRepouso), dp(alturaRepouso))
    }

    private fun animar(w: Int, hh: Int) {
        val w0 = lp.width; val h0 = lp.height
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                lp.width = (w0 + (w - w0) * f).toInt()
                lp.height = (h0 + (hh - h0) * f).toInt()
                if (raiz.isAttachedToWindow) wm.updateViewLayout(raiz, lp)
            }
            start()
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        IslandBus.onNotificacao = null; IslandBus.onMidia = null
        h.removeCallbacksAndMessages(null)
        if (raiz.isAttachedToWindow) wm.removeView(raiz)
        super.onDestroy()
    }
}
