package com.exemplo.nuvem

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var col: LinearLayout
    private lateinit var botaoMusica: Button
    private val atualizadores = mutableListOf<() -> Unit>()
    private var musicaTeste = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        montar()
        val rolagem = ScrollView(this).apply { addView(col) }
        rolagem.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }
        setContentView(rolagem)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        if (!IslandService.rodando && musicaTeste) {
            musicaTeste = false
            botaoMusica.text = "Testar música: ligar"
        }
        atualizadores.forEach { it() }
    }

    private fun montar() {
        col.addView(texto("Nuvem", 28f, true, Color.BLACK))
        col.addView(texto("Ilha flutuante para o seu Android"))

        secao("Status")
        linhaStatus("Aparecer sobre outros apps", { Settings.canDrawOverlays(this) }, "Ativar") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        linhaStatus("Acesso às notificações", { acessoNotificacoes() }, "Ativar") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            linhaStatus("Avisos do app", { permNotificacao() }, "Ativar") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        linhaStatus("Nuvem ligada", { IslandService.rodando }, "Ligar") { iniciar() }
        col.addView(texto("Se o botão Ativar ficar cinza: Configurações › Aplicativos › Nuvem › ⋮ › Permitir configurações restritas.", 12f))

        val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val liga = Button(this).apply {
            text = "Ligar nuvem"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { iniciar() }
        }
        val para = Button(this).apply {
            text = "Parar nuvem"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                stopService(Intent(this@MainActivity, IslandService::class.java))
                col.postDelayed({ atualizar() }, 600)
            }
        }
        linha.addView(liga); linha.addView(para)
        col.addView(linha)

        secao("Testar")
        col.addView(Button(this).apply {
            text = "Testar notificação"; isAllCaps = false
            setOnClickListener {
                if (!IslandService.rodando) aviso("Ligue a nuvem primeiro")
                else IslandBus.onNotificacao?.invoke(Evento("Marina", "Bora almoçar hoje? Achei um lugar novo!"))
            }
        })
        botaoMusica = Button(this).apply {
            text = "Testar música: ligar"; isAllCaps = false
            setOnClickListener {
                if (!IslandService.rodando) { aviso("Ligue a nuvem primeiro"); return@setOnClickListener }
                musicaTeste = !musicaTeste
                IslandBus.onMidia?.invoke(
                    if (musicaTeste) "Música de teste" else "",
                    if (musicaTeste) "Nuvem" else "",
                    musicaTeste, null
                )
                text = if (musicaTeste) "Testar música: desligar" else "Testar música: ligar"
            }
        }
        col.addView(botaoMusica)
        col.addView(texto("Dica: deixe “Testar música” ligado para ver a nuvem aberta enquanto mexe nos ajustes.", 12f))

        secao("Tamanho e posição")
        slider("Largura parada", "larguraRepouso", 30, 160, "dp")
        slider("Altura parada", "alturaRepouso", 16, 48, "dp")
        slider("Largura com música", "larguraCompacta", 100, 260, "dp")
        slider("Altura aberta", "alturaAberta", 60, 160, "dp")
        slider("Espaço da câmera (aberta)", "topoConteudo", 0, 60, "dp")
        slider("Distância do topo", "margemTopo", 0, 40, "dp")

        secao("Visual")
        escolha("Cor da pílula", "corPilula", Cfg.CORES_PILULA.map { it.first })
        escolha("Cor da onda", "temaOnda", Cfg.TEMAS.map { it.first })
        interruptor("Mostrar a onda da música", "onda")
        interruptor("Mostrar botão de pausa", "controles")
        interruptor("Mostrar notificações", "notificacoes")
        slider("Tempo aberta (notificação)", "tempoAberta", 2, 15, "s")

        col.addView(Button(this).apply {
            text = "Restaurar padrões"; isAllCaps = false
            setOnClickListener {
                Cfg.sp(this@MainActivity).edit().clear().apply()
                recreate()
            }
        })
    }

    private fun iniciar() {
        if (!Settings.canDrawOverlays(this)) {
            aviso("Ative “Aparecer sobre outros apps” primeiro")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, IslandService::class.java))
        col.postDelayed({ atualizar() }, 600)
    }

    private fun aviso(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun acessoNotificacoes(): Boolean {
        val lista = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return lista.contains(packageName)
    }

    private fun permNotificacao() =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // ---------- pedaços de tela ----------

    private fun texto(t: String, tam: Float = 14f, negrito: Boolean = false, cor: Int = Color.DKGRAY): TextView =
        TextView(this).apply {
            text = t; textSize = tam; setTextColor(cor)
            if (negrito) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun secao(t: String) {
        col.addView(texto(t, 18f, true, Color.BLACK).apply { setPadding(0, dp(24), 0, dp(6)) })
    }

    private fun linhaStatus(nome: String, ok: () -> Boolean, rotuloBotao: String, ativar: () -> Unit) {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val rotulo = TextView(this).apply {
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            text = rotuloBotao; isAllCaps = false
            setOnClickListener { ativar() }
        }
        linha.addView(rotulo); linha.addView(btn)
        col.addView(linha)
        atualizadores.add {
            val v = ok()
            rotulo.text = (if (v) "✓ " else "✗ ") + nome
            rotulo.setTextColor(Color.parseColor(if (v) "#1B8A5A" else "#C62828"))
            btn.visibility = if (v) View.GONE else View.VISIBLE
        }
    }

    private fun slider(rotulo: String, chave: String, minimo: Int, maximo: Int, unidade: String) {
        val atual = Cfg.get(this, chave).coerceIn(minimo, maximo)
        val tv = texto("$rotulo: $atual $unidade")
        val sb = SeekBar(this)
        sb.max = maximo - minimo
        sb.progress = atual - minimo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + minimo
                tv.text = "$rotulo: $v $unidade"
                if (fromUser) Cfg.set(this@MainActivity, chave, v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        col.addView(tv)
        col.addView(sb)
    }

    private fun escolha(rotulo: String, chave: String, opcoes: List<String>) {
        col.addView(texto(rotulo))
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcoes)
        sp.setSelection(Cfg.get(this, chave).coerceIn(0, opcoes.size - 1))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos != Cfg.get(this@MainActivity, chave)) Cfg.set(this@MainActivity, chave, pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(sp)
    }

    private fun interruptor(rotulo: String, chave: String) {
        col.addView(Switch(this).apply {
            text = rotulo
            isChecked = Cfg.get(this@MainActivity, chave) == 1
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, ligado -> Cfg.set(this@MainActivity, chave, if (ligado) 1 else 0) }
        })
    }
}
