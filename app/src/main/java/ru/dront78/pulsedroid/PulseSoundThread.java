package ru.dront78.pulsedroid;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.PowerManager.WakeLock;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;

public class PulseSoundThread implements Runnable {
	private boolean mTerminate = false;
	private String mServer;
	private int mPort;
    private final WakeLock wakeLock;
	private Throwable error;

	PulseSoundThread(String Server, String Port, WakeLock wl) {
		mServer = Server;
		mPort = Integer.valueOf(Port);
		wakeLock = wl;
	}

	public void terminate() {
		mTerminate = true;
	}

	public void run() {
		Socket sock;
		BufferedInputStream audioData = null;
		try {
			sock = new Socket(mServer, mPort);
		} catch (UnknownHostException e) {
			// TODO if the host name could not be resolved into an IP address.
			terminate();
			e.printStackTrace();
			error = e;
			return;
		} catch (IOException e) {
			// TODO if an error occurs while creating the socket
			terminate();
			e.printStackTrace();
			error = e;
			return;
		} catch (SecurityException e) {
			// TODO if a security manager exists and it denies the permission to
			// connect to the given address and port.
			terminate();
			e.printStackTrace();
			error = e;
			return;
		}

        try {
            audioData = new BufferedInputStream(sock.getInputStream());
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            terminate();
            e.printStackTrace();
            return;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            terminate();
            e.printStackTrace();
            return;
        }

		// Create AudioPlayer
		/*
		 * final int sampleRate = AudioTrack
		 * .getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
		 */
		// TODO native audio?
		final int sampleRate = 48000;

		int musicLength = AudioTrack.getMinBufferSize(sampleRate,
				AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT);
		AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT, musicLength,
				AudioTrack.MODE_STREAM);
		audioTrack.play();

		try {
            // TODO buffer size computation
            byte[] audioBuffer = new byte[musicLength * 8];

            while (!mTerminate) {
                wakeLock.acquire(1000);
                try {
                    int sizeRead = audioData.read(audioBuffer, 0, musicLength * 8);
                    int sizeWrite = audioTrack.write(audioBuffer, 0, sizeRead);
                    if (sizeWrite == AudioTrack.ERROR_INVALID_OPERATION) {
                        sizeWrite = 0;
                    }
                    if (sizeWrite == AudioTrack.ERROR_BAD_VALUE) {
                        sizeWrite = 0;
                    }
                    if (sizeWrite < 0) {
                        terminate();
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    terminate();
                }
            }
        } finally {
            audioTrack.stop();
        }
	}
}
