package com.exemplo.nuvem

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        fun botao(t: String, acao: () -> Unit) {
            col.addView(Button(this).apply { text = t; setOnClickListener { acao() } })
        }

        col.addView(TextView(this).apply { text = "Nuvem – ilha flutuante"; textSize = 22f })
        botao("1. Permitir exibir sobre outros apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        botao("2. Permitir acesso às notificações") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        botao("3. Iniciar") {
            if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, IslandService::class.java))
            else Toast.makeText(this, "Faltou o passo 1", Toast.LENGTH_SHORT).show()
        }
        botao("Parar") { stopService(Intent(this, IslandService::class.java)) }
        setContentView(col)
    }
}
