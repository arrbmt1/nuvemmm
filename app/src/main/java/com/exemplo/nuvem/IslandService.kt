package com.exemplo.nuvem

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
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
    companion object {
        @Volatile var rodando = false
    }

    private lateinit var wm: WindowManager
    private lateinit var raiz: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var ponto: View
    private lateinit var info: LinearLayout
    private lateinit var titulo: TextView
    private lateinit var sub: TextView
    private lateinit var botao: TextView
    private lateinit var fundo: GradientDrawable
    private var raioTopo = 0f
    private lateinit var ondaE: OndaView
    private lateinit var ondaD: OndaView
    private lateinit var ondaMid: OndaView
    private lateinit var espaco: View

    private val h = Handler(Looper.getMainLooper())
    private val recolher = Runnable { voltar() }
    private var aberta = false
    private var abertaMidia = false
    private var ctrl: MediaController? = null
    private var tocando = false
    private var tituloMidia = ""
    private var artistaMidia = ""
    private var anim: ValueAnimator? = null
    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> aplicarConfig() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(k: String) = Cfg.get(this, k)
    private fun tempoAberta() = c("tempoAberta") * 1000L

    override fun onCreate() {
        super.onCreate()
        rodando = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nuvem", "Nuvem", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "nuvem")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nuvem ativa").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        montarView()
        aplicarConfig()
        Cfg.sp(this).registerOnSharedPreferenceChangeListener(ouvinte)

        IslandBus.onNotificacao = { e ->
            // ignora a notificação do próprio player (mesmo título da música)
            if (c("notificacoes") == 1 && !(tituloMidia.isNotEmpty() && e.titulo == tituloMidia))
                abrir(e.titulo, e.texto, false)
        }
        IslandBus.onMidia = { t, a, p, ctl ->
            val mudou = t != tituloMidia
            val comecou = p && !tocando
            ctrl = ctl; tituloMidia = t; artistaMidia = a; tocando = p
            botao.text = if (p) "❚❚" else "▶"
            atualizarOndas()
            when {
                t.isNotEmpty() && p && (mudou || comecou) -> abrir(t, a, true)  // música começou: expande e fica
                aberta && !p -> { h.removeCallbacks(recolher); h.postDelayed(recolher, tempoAberta()) }
                !aberta -> repouso()
                else -> {}
            }
        }
    }

    private fun montarView() {
        wm = getSystemService(WindowManager::class.java)

        raioTopo = dp(40).toFloat()
        fundo = GradientDrawable().apply {
            setColor(Color.parseColor("#000000"))
            cornerRadius = dp(40).toFloat()
        }
        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = fundo
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
                h.removeCallbacks(recolher)
            }
        }
        ondaE = OndaView(this, 4).apply { layoutParams = LinearLayout.LayoutParams(dp(22), dp(14)); visibility = View.GONE }
        ondaD = OndaView(this, 4).apply { layoutParams = LinearLayout.LayoutParams(dp(22), dp(14)); visibility = View.GONE }
        ondaMid = OndaView(this, 5).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(22)).apply { marginEnd = dp(10) }
            visibility = View.GONE
        }
        espaco = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f); visibility = View.GONE }
        raiz.addView(ondaE); raiz.addView(espaco); raiz.addView(ponto); raiz.addView(info)
        raiz.addView(ondaMid); raiz.addView(botao); raiz.addView(ondaD)
        info.visibility = View.GONE; botao.visibility = View.GONE; ponto.visibility = View.GONE

        raiz.setOnClickListener {
            if (aberta) fechar()
            else if (tituloMidia.isNotEmpty()) abrir(tituloMidia, artistaMidia, true)
        }

        lp = WindowManager.LayoutParams(
            dp(c("larguraRepouso")), dp(c("alturaRepouso")),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(c("margemTopo"))
            if (Build.VERSION.SDK_INT >= 30)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(raiz, lp)
    }

    /** Aplica na hora o que foi mexido na tela de ajustes. */
    private fun aplicarConfig() {
        if (!::raiz.isInitialized) return
        val corP = Cfg.CORES_PILULA[c("corPilula").coerceIn(0, Cfg.CORES_PILULA.size - 1)].second
        fundo.setColor(Color.parseColor(corP))
        val tema = Cfg.TEMAS[c("temaOnda").coerceIn(0, Cfg.TEMAS.size - 1)]
        val ca = Color.parseColor(tema.second)
        val cb = Color.parseColor(tema.third)
        ondaE.cores(ca, cb); ondaD.cores(ca, cb); ondaMid.cores(ca, cb)

        anim?.cancel()
        lp.y = dp(c("margemTopo"))
        if (aberta) {
            botao.visibility = if (abertaMidia && ctrl != null && c("controles") == 1) View.VISIBLE else View.GONE
            ondaMid.visibility = if (abertaMidia && c("onda") == 1) View.VISIBLE else View.GONE
            raiz.setPadding(dp(20), dp(c("topoConteudo")), dp(16), dp(8))
            lp.width = resources.displayMetrics.widthPixels
            lp.height = dp(c("alturaAberta"))
            raioTopo = 0f
            aplicarRaio()
        } else {
            repouso(false)
        }
        if (raiz.isAttachedToWindow) wm.updateViewLayout(raiz, lp)
    }

    private fun abrir(t: String, s: String, midia: Boolean) {
        titulo.text = t; sub.text = s
        abertaMidia = midia
        botao.visibility = if (midia && ctrl != null && c("controles") == 1) View.VISIBLE else View.GONE
        ponto.visibility = View.GONE; info.visibility = View.VISIBLE
        raiz.setPadding(dp(20), dp(c("topoConteudo")), dp(16), dp(8))
        ondaE.visibility = View.GONE; ondaD.visibility = View.GONE; espaco.visibility = View.GONE
        ondaMid.visibility = if (midia && c("onda") == 1) View.VISIBLE else View.GONE
        atualizarOndas()
        aberta = true
        animar(resources.displayMetrics.widthPixels, dp(c("alturaAberta")), true)  // largura total da tela
        h.removeCallbacks(recolher)
        if (!(midia && tocando)) h.postDelayed(recolher, tempoAberta())  // com música tocando, fica aberta
    }

    /** Depois de uma notificação: volta para a música (se tocando) ou recolhe. */
    private fun voltar() {
        if (tocando && tituloMidia.isNotEmpty()) abrir(tituloMidia, artistaMidia, true) else fechar()
    }

    private fun fechar() {
        h.removeCallbacks(recolher)
        aberta = false
        info.visibility = View.GONE; botao.visibility = View.GONE; ponto.visibility = View.GONE
        repouso()
    }

    private fun atualizarOndas() {
        ondaE.ativa = tocando; ondaD.ativa = tocando; ondaMid.ativa = tocando
    }

    /** Pílula parada: lisa, ou com ondas dos dois lados da câmera se tiver música tocando. */
    private fun repouso(animado: Boolean = true) {
        val musica = tocando && tituloMidia.isNotEmpty() && c("onda") == 1
        val vis = if (musica) View.VISIBLE else View.GONE
        ondaE.visibility = vis; ondaD.visibility = vis; espaco.visibility = vis
        ondaMid.visibility = View.GONE
        raiz.setPadding(if (musica) dp(12) else 0, 0, if (musica) dp(12) else 0, 0)
        val w = dp(c(if (musica) "larguraCompacta" else "larguraRepouso"))
        val hh = dp(c("alturaRepouso"))
        if (animado) animar(w, hh)
        else {
            lp.width = w; lp.height = hh
            raioTopo = dp(40).toFloat()
            aplicarRaio()
        }
    }

    private fun aplicarRaio() {
        val rb = dp(40).toFloat()
        fundo.cornerRadii = floatArrayOf(raioTopo, raioTopo, raioTopo, raioTopo, rb, rb, rb, rb)
    }

    private fun animar(w: Int, hh: Int, cheia: Boolean = false) {
        anim?.cancel()
        val w0 = lp.width; val h0 = lp.height
        val r0 = raioTopo
        val r1 = if (cheia) 0f else dp(40).toFloat()   // aberta: topo reto, cobre a barra de status
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                lp.width = (w0 + (w - w0) * f).toInt()
                lp.height = (h0 + (hh - h0) * f).toInt()
                raioTopo = (r0 + (r1 - r0) * f).coerceAtLeast(0f)
                aplicarRaio()
                if (raiz.isAttachedToWindow) wm.updateViewLayout(raiz, lp)
            }
            start()
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        rodando = false
        Cfg.sp(this).unregisterOnSharedPreferenceChangeListener(ouvinte)
        IslandBus.onNotificacao = null; IslandBus.onMidia = null
        anim?.cancel()
        h.removeCallbacksAndMessages(null)
        if (raiz.isAttachedToWindow) wm.removeView(raiz)
        super.onDestroy()
    }
}
