/*
 * Copyright (C) 2012 Multimedia Communications Group (www.comm.upv.es).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package es.upv.comm.loku;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Process;

import io.reactivex.subscribers.DisposableSubscriber;

/**
 * A {@link Player} that plays audio in reactive extensions fashion.<P>
 * Just create the {@link Player} object and subscribe it to a {@link Recorder} object.
 * <pre>
 *   {@code
 *
 *   Player player = new Player(getApplicationContext());
 *   compositeDisposable.add(
 *     recorder.getAudioFlowable()
 *       .observeOn(Schedulers.newThread())
 *       .subscribeWith(player));
 *   }
 * </pre>
 */
public class Player extends DisposableSubscriber<short[]> {

  private AudioPlayerState audioPlayerState;

  /**
   * Creates a player with default audio settings
   *
   * @param context the app {@link Context}
   */
  public Player(Context context) {
    this(context, 0, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        AudioManager.STREAM_VOICE_CALL);
  }

  /**
   * Create a {@link Player} with these audio params
   *
   * @param context        the app {@link Context}
   * @param sampleRateInHz sample rate in Hz
   * @param channelConfig channel config as defined in {@link AudioTrack}
   * @param audioFormat audio format as defined in {@link AudioTrack}
   * @param streamType stream type ad defined in {@link AudioTrack}
   */
  public Player(Context context, int sampleRateInHz, int channelConfig,
                int audioFormat, int streamType) {
    audioPlayerState = new AudioPlayerState(context, sampleRateInHz, channelConfig, audioFormat,
        streamType);
  }

  @Override
  protected void onStart() {
    request(1);
  }

  @Override
  public void onNext(short[] shorts) {
    audioPlayerState.playAudio(shorts);
    request(1);
  }

  @Override
  public void onError(Throwable t) {
    audioPlayerState.stop();
  }

  @Override
  public void onComplete() {
    audioPlayerState.stop();
  }


  private static class AudioPlayerState {

    private final AudioTrack audioTrack;
    private final int sampleRate;

    private AudioPlayerState(Context context, int sampleRateInHz, int channelConfig,
                             int audioFormat, int streamType) {
      // set priority
      android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

      // recording frequency
      if (sampleRateInHz == 0) {
        sampleRate = getSampleRate(context);
      } else {
        sampleRate = sampleRateInHz;
      }

      // recording buffer
      int recordBuffer = AudioTrack.getMinBufferSize(
          sampleRate,
          channelConfig,
          audioFormat);

      // audio record
      audioTrack = new AudioTrack(
          streamType,
          sampleRate,
          channelConfig,
          audioFormat,
          recordBuffer, AudioTrack.MODE_STREAM);

      // start the audio track
      audioTrack.play();
    }

    private void playAudio(short[] audio) {
      int written = 0;
      while (audioTrack != null && written < audio.length) {
        written += audioTrack.write(audio,
            written,
            audio.length - written);
      }
    }

    private int getSampleRate(Context context) {
      AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      int sampleRate = 0;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        sampleRate = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
      }
      if (sampleRate == 0)
        sampleRate = 44100;
      return sampleRate;
    }

    private void stop() {
      audioTrack.stop();
      audioTrack.release();
    }

  }
}
