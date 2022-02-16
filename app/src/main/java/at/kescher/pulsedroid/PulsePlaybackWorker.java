package at.kescher.pulsedroid;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import at.kescher.pulsedroid.exception.StoppedException;

public class PulsePlaybackWorker implements Runnable {

    // How often we try to receive per second.
    private static final int LOOPS_PER_SECOND = 8;
    // How many loop iterations in the buffering state should be a timeout.
    private static final int BUFFERING_LOOP_TIMEOUT = LOOPS_PER_SECOND * 5;

    private final String host;
    private final int port;
    private final WakeLock wakeLock;
    private final Handler handler;
    private final Listener listener;

    private volatile boolean stopped = false;
    private volatile long bufferSizeMillisAhead = 125, bufferSizeMillisBehind = 1000;
    private volatile int sampleRate = 44100, channels = 1;
    private boolean waitingForBufferFill = true;

    private Throwable error;
    private Socket sock;
    private InputStream audioData;
    private AudioTrack audioTrack;
    private byte[] audioBuffer;

    private int numSkip, bufferPos, chunkSize, minBufferSize, byteRate;

    PulsePlaybackWorker(String host, int port, WakeLock wakeLock, Handler handler, Listener listener) {
        this.host = host;
        this.port = port;
        this.wakeLock = wakeLock;
        this.handler = handler;
        this.listener = listener;
    }

    @MainThread
    public void stop() {
        synchronized (this) {
            stopped = true;
            Socket s = sock;
            if (s != null) {
                // Close our socket to force-stop a long read().
                try {
                    // NOTE: Hopefully, this is not an "I/O-Operation" because
                    // we run this on the main thread. It seems to work fine on android 7.1.2.
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Throwable getError() {
        return error;
    }

    public void run() {
        try {
            setup();

            int bufferingIterations = 0;
            boolean didBuffer = false;
            boolean started = false;
            while (!stopped) {
                wakeLock.acquire(1000);

                manageBufferSize();

                if (!waitingForBufferFill) {
                    readFromSocket();

                    writeToAudioTrack();

                    if (!started) {
                        started = true;
                        handler.post(() -> listener.onPlaybackStarted(this));
                    }
                } else {
                    if (!didBuffer) {
                        didBuffer = true;
                        handler.post(() -> listener.onPlaybackBuffering(this));
                    }
                    bufferingIterations++;
                    if (bufferingIterations > BUFFERING_LOOP_TIMEOUT) {
                        throw new TimeoutException("Buffering went on for longer than 5 seconds");
                    }
                    // Sleep for about the time this loop runs under normal conditions.
                    Thread.sleep(1000 / LOOPS_PER_SECOND);
                }
            }

            handler.post(() -> listener.onPlaybackStopped(this));
        } catch (StoppedException e) {
            handler.post(() -> listener.onPlaybackStopped(this));
        } catch (Exception e) {
            // Suppress exception caused by stop() closing our socket.
            if (stopped && e instanceof SocketException) {
                handler.post(() -> listener.onPlaybackStopped(this));
            } else {
                Log.e(PulsePlaybackWorker.class.getSimpleName(), "stopWithError", e);
                error = e;
                handler.post(() -> listener.onPlaybackError(this, e));
            }
        } finally {
            cleanup();
        }
    }

    private void setup() throws IOException {
        // bytes per second = sample rate * 2 bytes per sample * 2 channels
        byteRate = sampleRate * 2 * 2;

        minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        int channelConfig;
        switch (channels) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
                break;
            default:
                channelConfig = AudioFormat.CHANNEL_CONFIGURATION_INVALID;
                break;
        }

        chunkSize = byteRate / LOOPS_PER_SECOND;

        connect();

        audioData = sock.getInputStream();

        // Always using minimum buffer size for minimum lag.
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT, minBufferSize,
                AudioTrack.MODE_STREAM);
        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException(
                    "Could not initialize AudioTrack."
                            + " state == " + audioTrack.getState());
        }
        audioTrack.play();

        bufferPos = 0;
        numSkip = 0;
        audioBuffer = new byte[chunkSize];
    }

    private void manageBufferSize() throws IOException {
        long bufferSizeMillisBehind = this.bufferSizeMillisBehind;

        // bufferSizeMillisBehind < 0 means infinite buffer: never skip anything
        if (bufferSizeMillisBehind >= 0) {
            final long bufMillisTotal = bufferSizeMillisAhead + bufferSizeMillisBehind;
            final long bufferBehindSize = Math.max(minBufferSize, byteRate * bufferSizeMillisBehind / 1000);
            final int bufferSize = Math.max(minBufferSize, (int) (byteRate * bufMillisTotal / 1000));

            final int available = audioData.available();

            if (available > bufferBehindSize) {
                waitingForBufferFill = false;
            }

            if (available > bufferSize) {
                // We have more bytes than fit into our buffer. Skip forward so
                // we don't get left behind.
                // Keep our buffer bufferSizeMillisBehind filled.
                final long wantSkip = numSkip + available - bufferBehindSize;
                final long actual = audioData.skip(wantSkip);
                // If we happened to skip part of a pair of samples, we need
                // to skip the remaining bytes of it when writing to audioTrack.
                final int malign = (int) ((bufferPos + actual) & 3L);
                if (malign != 0) {
                    numSkip = 4 - malign;
                } else {
                    numSkip = 0;
                }
                Log.d("Worker", "skipped: wantSkip=" + wantSkip + " actual=" + actual + " numSkip=" + numSkip + " bufferPos=" + bufferPos);
                bufferPos = 0;
            }
        } else {
            // Don't wait for infinite buffer.
            waitingForBufferFill = false;
        }
    }

    private void readFromSocket() throws IOException {
        // Never aim to write more than chunkSize to audioTrack, so we don't get
        // blocked any longer than needed.
        int wantRead = chunkSize - bufferPos;

        int nRead = audioData.read(audioBuffer, bufferPos, wantRead);
        if (nRead < 0) {
            throw new EOFException("Connection closed");
        }
        bufferPos += nRead;
    }

    private void writeToAudioTrack() throws IOException {
        int writeStart = numSkip;
        // [& ~3]: Only try to write full sample-pairs.
        int wantWrite = (bufferPos - numSkip) & ~3;

        int sizeWrite = 0;
        if (wantWrite > 0) {
            sizeWrite = audioTrack.write(audioBuffer, writeStart, wantWrite);
        }

        if (sizeWrite < 0) {
            throw new IOException("audioTrack.write() returned " + sizeWrite);
        } else {
            if (sizeWrite > 0) {
                // Move remaining data to the start of the buffer.
                int writeEnd = writeStart + sizeWrite;
                int len = bufferPos - writeEnd;
                System.arraycopy(audioBuffer, writeEnd, audioBuffer, 0, len);
                bufferPos = len;
                numSkip = 0;
            }
        }
    }

    private void cleanup() {
        audioBuffer = null;
        if (audioData != null) {
            try {
                audioData.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            audioData = null;
        }
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            sock = null;
        }
        if (audioTrack != null) {
            // AudioTrack throws if we call stop() in stopped state. This happens if
            // audioTrack.play() fails.
            if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                audioTrack.stop();
            }
            audioTrack.release();
            audioTrack = null;
        }
    }

    /**
     * Creates {@code sock}, and connects it to our {@code host} and {@code port}.
     * <p>
     * This behaves like the {@link Socket Socket(String, int)} constructor, but
     * allows referencing the socket and can be interrupted with {@link #stop()}.
     *
     * @throws IOException      If connection to the host fails.
     * @throws StoppedException If {@link #stopped} was set.
     */
    private void connect() throws IOException {

        // We may hang here to resolve a host name. No way to interrupt this so far.
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new UnknownHostException("No addresses returned by InetAddress.getAllByName()");
        }

        for (int i = 0; i < addresses.length; i++) {
            InetAddress address = addresses[i];
            try {
                synchronized (this) {
                    sock = null;
                    if (stopped) {
                        throw new StoppedException();
                    }
                    sock = new Socket();
                }
                sock.connect(new InetSocketAddress(address, port));

                // We are now connected.
                return;
            } catch (IOException connException) {
                try {
                    sock.close();
                } catch (IOException e) {
                    connException.addSuppressed(e);
                }

                // Only throw on last address.
                if (i == addresses.length - 1) {
                    throw connException;
                }
            }
        }

        throw new AssertionError("should never happen");
    }

    public void setBufferSettings(int millisAhead, int millisBehind, int sampleRate, int channels) {
        bufferSizeMillisAhead = millisAhead;
        bufferSizeMillisBehind = millisBehind;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public interface Listener {
        @MainThread
        void onPlaybackError(@NonNull PulsePlaybackWorker worker, @NonNull Throwable t);

        @MainThread
        void onPlaybackBuffering(@NonNull PulsePlaybackWorker worker);

        @MainThread
        void onPlaybackStarted(@NonNull PulsePlaybackWorker worker);

        @MainThread
        void onPlaybackStopped(@NonNull PulsePlaybackWorker worker);
    }
}
