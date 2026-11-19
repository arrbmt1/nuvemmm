package com.exemplo.nuvem

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private var ctrl: MediaController? = null

    private val sessoes = MediaSessionManager.OnActiveSessionsChangedListener { trocar(it) }
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = enviar()
        override fun onPlaybackStateChanged(state: PlaybackState?) = enviar()
    }

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        val cn = ComponentName(this, NotifListener::class.java)
        msm?.addOnActiveSessionsChangedListener(sessoes, cn)
        trocar(msm?.getActiveSessions(cn))
    }

    override fun onListenerDisconnected() {
        msm?.removeOnActiveSessionsChangedListener(sessoes)
        ctrl?.unregisterCallback(cb)
    }

    private fun trocar(lista: List<MediaController>?) {
        ctrl?.unregisterCallback(cb)
        ctrl = lista?.firstOrNull()
        ctrl?.registerCallback(cb)
        enviar()
    }

    private fun enviar() {
        val c = ctrl
        if (c == null) { IslandBus.onMidia?.invoke("", "", false, null); return }
        val m = c.metadata
        val titulo = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artista = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val tocando = c.playbackState?.state == PlaybackState.STATE_PLAYING
        IslandBus.onMidia?.invoke(titulo, artista, tocando, c)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val e = sbn.notification.extras
        val titulo = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val texto = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        IslandBus.onNotificacao?.invoke(Evento(titulo, texto))
    }
}
