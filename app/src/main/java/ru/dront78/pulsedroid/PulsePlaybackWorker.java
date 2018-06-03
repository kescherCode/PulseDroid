package ru.dront78.pulsedroid;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.PowerManager.WakeLock;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class PulsePlaybackWorker implements Runnable {

    /**
     * Maximum block that socket's {@code InputStream} seems to read regardless of the
     * length parameter. If we try to read more than this, our alignment fails.
     */
    private static final int MAX_SOCKET_READ_LEN = 65536;

    private final String host;
    private final int port;
    private final WakeLock wakeLock;
    private final Handler handler;
    private final Listener listener;

    private Throwable error;
    private AtomicBoolean stopped = new AtomicBoolean(false);

    PulsePlaybackWorker(String host, String port, WakeLock wakeLock, Handler handler, Listener listener) {
        this.host = host;
        this.port = Integer.valueOf(port);
        this.wakeLock = wakeLock;
        this.handler = handler;
        this.listener = listener;
    }

    public void stop() {
        stopped.set(true);
    }

    private void stopWithError(Throwable e) {
        Log.e(PulsePlaybackWorker.class.getSimpleName(), "stopWithError", e);
        error = e;
        stopped.set(true);
        handler.post(() -> listener.onPlaybackError(this, e));
    }

    public Throwable getError() {
        return error;
    }

    public void run() {
        Socket sock = null;
        InputStream audioData = null;
        AudioTrack audioTrack = null;
        try {
            final int sampleRate = 48000;
            // bytes per second = sample rate * 2 bytes per sample * 2 channels
            final int byteRate = sampleRate * 2 * 2;

            final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);

            // Use a 2-second buffer. Larger if system needs it.
            final int bufferSize = Math.max(minBufferSize, byteRate * 2);

            // Try to receive 4 times per second.
            final int chunkSize = byteRate / 4;

            sock = new Socket(host, port);
            sock.setReceiveBufferSize(bufferSize);
            audioData = sock.getInputStream();

            // Always using minimum buffer size for minimum lag.
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, minBufferSize,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();

            boolean started = false;

            int bufPos = 0;
            int numSkip = 0;
            byte[] audioBuffer = new byte[MAX_SOCKET_READ_LEN];

            while (!stopped.get()) {
                wakeLock.acquire(1000);

                final int available = audioData.available();
                int wantRead;
                if (available > bufferSize) {
                    // We have more bytes than fit into our buffer. Skip forward so
                    // we don't get left behind.
                    // [bufferSize / 2]: Try to keep our buffer half-filled.
                    final long wantSkip = numSkip + (available - bufferSize / 2);
                    final long actual = audioData.skip(wantSkip | 3); // @DEBUG@: | 3
                    // If we happened to skip part of a pair of samples, we need
                    // to skip the remaining bytes of it when writing to audioTrack.
                    final int malign = (int) ((bufPos + actual) & 3L);
                    if (malign != 0) {
                        numSkip = 4 - malign;
                    } else {
                        numSkip = 0;
                    }
                    wantRead = Math.min(MAX_SOCKET_READ_LEN, (int) (available - actual));
                    Log.d("Worker", "skipped: available=" + available + " wantSkip=" + wantSkip + " actual=" + actual + " malign=" + malign + " numSkip=" + numSkip + " wantRead=" + wantRead + " bufPos=" + bufPos);
                    bufPos = 0;
                } else {
                    // Read all if we already have more than chunkSize.
                    wantRead = Math.min(MAX_SOCKET_READ_LEN, Math.max(available, chunkSize));
                }

                bufPos += audioData.read(audioBuffer, bufPos, wantRead - bufPos);

                int writeStart = numSkip;
                // [& ~3]: Only try to write full sample-pairs.
                int wantWrite = (bufPos - numSkip) & ~3;

                int sizeWrite = 0;
                if (wantWrite > 0) {
                    sizeWrite = audioTrack.write(audioBuffer, writeStart, wantWrite);
                }

                if (sizeWrite < 0) {
                    stopWithError(new IOException("audioTrack.write() returned " + sizeWrite));
                } else {
                    if (sizeWrite > 0) {
                        // Move remaining data to the start of the buffer.
                        int writeEnd = writeStart + sizeWrite;
                        Log.d("Worker", "shift: writeEnd=" + writeEnd + " bufPos=" + bufPos + " sizeWrite=" + sizeWrite);
                        System.arraycopy(audioBuffer, writeEnd, audioBuffer, 0, bufPos - writeEnd);
                        bufPos -= writeStart + sizeWrite;
                        numSkip = 0;
                    }
                    if (!started) {
                        started = true;
                        handler.post(() -> listener.onPlaybackStarted(this));
                    }
                }
            }

            handler.post(() -> listener.onPlaybackStopped(this));
        } catch (Exception e) {
            stopWithError(e);
        } finally {
            if (audioData != null) {
                try {
                    audioData.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (audioTrack != null) {
                // AudioTrack throws if we call stop() in stopped state. This happens if
                // audioTrack.play() fails.
                if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                    audioTrack.stop();
                }
            }
        }
    }

    public interface Listener {
        @MainThread
        void onPlaybackError(@NonNull PulsePlaybackWorker worker, @NonNull Throwable t);

        @MainThread
        void onPlaybackStarted(@NonNull PulsePlaybackWorker worker);

        @MainThread
        void onPlaybackStopped(@NonNull PulsePlaybackWorker worker);
    }
}
