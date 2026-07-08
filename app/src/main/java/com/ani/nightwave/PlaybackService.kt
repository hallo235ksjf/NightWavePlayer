package com.ani.nightwave

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Wraps the app's single ExoPlayer instance (owned/created in MainActivity)
 * in a MediaSession so the system builds the standard media notification for
 * us: title, cover art, and Play/Pause/Skip-Next/Skip-Previous controls that
 * work from the notification shade and lock screen, even with the app in
 * the background.
 *
 * MainActivity hands its ExoPlayer to PlaybackService.player before starting
 * this service - the service never creates its own player, it just exposes
 * the existing one through a session.
 *
 * IMPORTANT: the app always plays a single MediaItem at a time (each track
 * is loaded individually, not as a multi-item ExoPlayer playlist), so
 * ExoPlayer itself reports hasNextMediaItem()/hasPreviousMediaItem() as
 * false and the system notification would hide the Next/Previous buttons.
 * NextPrevCommandPlayer forces those commands to always be available and
 * routes them to MainActivity's playNext()/playPrev(), which do the actual
 * track switching - so the notification buttons always show and always work.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        /** Set by MainActivity right after it builds the ExoPlayer, so the
         *  service can attach a session to the same instance instead of
         *  needing its own. */
        var player: Player? = null

        /** Set by MainActivity so the notification's Next/Previous buttons
         *  can drive the same track-switching logic the in-app buttons use. */
        var onNext: (() -> Unit)? = null
        var onPrev: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        val p = player ?: return
        val controllablePlayer = NextPrevCommandPlayer(
            wrapped = p,
            nextAction = { onNext?.invoke() },
            prevAction = { onPrev?.invoke() }
        )
        // Tapping the notification reopens the app instead of just the
        // system "resume playback" screen.
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, controllablePlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Stop the service once the app is swiped away from Recents and nothing
    // is playing anymore - otherwise the notification would linger forever.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return
        if (session.player.playWhenReady.not() || session.player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Forces COMMAND_SEEK_TO_NEXT(_MEDIA_ITEM) / COMMAND_SEEK_TO_PREVIOUS(_MEDIA_ITEM)
 * to always be reported as available (so the system media notification always
 * draws the Skip-Next / Skip-Previous buttons), and forwards those actions to
 * the app's own next/previous track logic instead of ExoPlayer's built-in
 * (single-item) playlist navigation.
 */
private class NextPrevCommandPlayer(
    wrapped: Player,
    private val nextAction: () -> Unit,
    private val prevAction: () -> Unit
) : ForwardingPlayer(wrapped) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = nextAction()
    override fun seekToNextMediaItem() = nextAction()
    override fun seekToPrevious() = prevAction()
    override fun seekToPreviousMediaItem() = prevAction()
}
