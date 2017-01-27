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
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;

import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;

/**
 * A {@link Recorder} records audio in reactive extensions fashion.<P>
 * The {@link Recorder} instance starts recording audio as soon it is created
 * <pre>
 *   {@code
 *
 *    Recorder recorder = new Recorder(getApplicationContext());
 *    compositeDisposable.add(recorder);
 *   }
 * </pre>
 *
 * Remember to unsubscribe it once do don't use it anymore
 */
public class Recorder implements Disposable {

  private PublishProcessor<short[]> publishProcessor;
  private Disposable publishProcessorDisposable;

  private AudioRecorderState audioRecorderState;

  /**
   * Creates a {@link Recorder} with default audio settings
   *
   * @param context the app {@link Context}
   */
  public Recorder(Context context) {
    this(context, 0, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        MediaRecorder.AudioSource.MIC);
  }

  /**
   * Creates a {@link Recorder} with these audio settings
   *
   * @param context        the app {@link Context}
   * @param sampleSizeInHz the number of samples of each buffer
   * @param channelConfig  channel config as defined in {@link AudioTrack}
   * @param audioFormat    audio format as defined in {@link AudioTrack}
   * @param audioSource    audio source ad define in {@link android.media.MediaRecorder.AudioSource}
   */
  public Recorder(Context context, int sampleSizeInHz, int channelConfig, int audioFormat,
                  int audioSource) {
    audioRecorderState = new AudioRecorderState(context, sampleSizeInHz, channelConfig, audioFormat,
        audioSource);

    Flowable<short[]> recorderFlowable = Flowable.generate(
        () -> audioRecorderState,
        new BiConsumer<AudioRecorderState, Emitter<short[]>>() {
          @Override
          public void accept(AudioRecorderState audioRecorderState, Emitter<short[]> objectEmitter)
              throws Exception {
            short[] data = audioRecorderState.readSample();
            objectEmitter.onNext(data);
          }
        },
        AudioRecorderState::stop
    );

    publishProcessor = PublishProcessor.create();
    publishProcessorDisposable = recorderFlowable
        .subscribeOn(Schedulers.newThread())
        .onBackpressureDrop()
        .subscribeWith(new DisposableSubscriber<short[]>() {
          @Override
          public void onNext(short[] shorts) {
            publishProcessor.onNext(shorts);
          }

          @Override
          public void onError(Throwable t) {
            publishProcessor.onError(t);
          }

          @Override
          public void onComplete() {
            publishProcessor.onComplete();
          }
        });
  }

  public Flowable<short[]> getAudioFlowable() {
    return publishProcessor.hide();
  }

  @Override
  public void dispose() {
    publishProcessorDisposable.dispose();
  }

  @Override
  public boolean isDisposed() {
    return publishProcessorDisposable.isDisposed();
  }

  /**
   * Gets the sample rate used for the recording
   * @return the sample rate
   */
  public int getSampleRate() {
    return audioRecorderState.getSampleRate();
  }

  /**
   * Gets the size of each sample
   * @return the size of the recorded samples
   */
  public int getSampleSize() {
    return audioRecorderState.getSampleSize();
  }

  private static class AudioRecorderState {

    private final AudioRecord audioRecord;
    private final int sampleRate;
    private final int sampleSize;

    private AudioRecorderState(Context context, int sampleSizeInHz, int channelConfig,
                               int audioFormat, int audioSource) {
      // set priority
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

      // recording frequency
      if (sampleSizeInHz == 0) {
        sampleRate = getSampleRate(context);
      } else {
        sampleRate = sampleSizeInHz;
      }

      // sample size
      sampleSize = sampleRate / 50;

      // recording buffer
      int recordBuffer = AudioRecord.getMinBufferSize(
          sampleRate,
          channelConfig,
          audioFormat);

      // audio record
      audioRecord = new AudioRecord(
          audioSource,
          sampleRate,
          channelConfig,
          audioFormat,
          recordBuffer);

      // start recording
      audioRecord.startRecording();
    }

    private short[] readSample() {
      // read recordSample
      short[] audioData = new short[sampleSize];
      int audioDataRead = 0;
      int audioPartialDataRead;

      // recorded sample
      while (audioDataRead != sampleSize) {
        audioPartialDataRead = audioRecord.read(
            audioData,
            audioDataRead,
            sampleSize - audioDataRead);
        switch (audioPartialDataRead) {
          case AudioRecord.ERROR_INVALID_OPERATION:
            break;
          case AudioRecord.ERROR_BAD_VALUE:
            break;
          case AudioRecord.ERROR:
            break;
          default:
            audioDataRead += audioPartialDataRead;
            break;
        }
      }
      return audioData;
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

    private int getSampleRate() {
      return sampleRate;
    }

    private int getSampleSize() {
      return sampleSize;
    }

    private void stop() {
      audioRecord.stop();
      audioRecord.release();
    }
  }

}
