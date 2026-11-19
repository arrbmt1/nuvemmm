package com.exemplo.nuvem

import android.media.session.MediaController

data class Evento(val titulo: String, val texto: String)

/** Ponte simples entre o leitor de notificações e a ilha. */
object IslandBus {
    var onNotificacao: ((Evento) -> Unit)? = null
    var onMidia: ((titulo: String, artista: String, tocando: Boolean, ctrl: MediaController?) -> Unit)? = null
}
