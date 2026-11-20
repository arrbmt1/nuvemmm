package com.exemplo.nuvem

import android.content.Context
import android.content.SharedPreferences

/** Ajustes salvos no celular. Tamanhos em dp. */
object Cfg {
    val PADRAO = mapOf(
        "larguraRepouso" to 84,
        "alturaRepouso" to 28,
        "larguraCompacta" to 150,
        "alturaAberta" to 84,
        "topoConteudo" to 30,
        "margemTopo" to 0,
        "tempoAberta" to 5,
        "temaOnda" to 0,
        "corPilula" to 0,
        "onda" to 1,
        "controles" to 1,
        "notificacoes" to 1
    )

    val TEMAS = listOf(
        Triple("Menta e violeta", "#38E0C0", "#8B7BFF"),
        Triple("Pôr do sol", "#FF9A5C", "#FF4D8D"),
        Triple("Oceano", "#4DA3FF", "#38E0F0"),
        Triple("Branco", "#FFFFFF", "#B0B8C8")
    )

    val CORES_PILULA = listOf(
        Pair("Preto", "#000000"),
        Pair("Grafite", "#1A1C24"),
        Pair("Azul-noite", "#0A1030")
    )

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("nuvem", Context.MODE_PRIVATE)
    fun get(c: Context, k: String): Int = sp(c).getInt(k, PADRAO.getValue(k))
    fun set(c: Context, k: String, v: Int) { sp(c).edit().putInt(k, v).apply() }
}
