package es.upv.comm.loku;


import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;

public class Recorder implements Disposable {

  private PublishProcessor<short[]> publishProcessor;
  private Disposable publishProcessorDisposable;

  private AudioRecorderState audioRecorderState;

  public Recorder(Context context) {
    this(context, 0, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        MediaRecorder.AudioSource.MIC);
  }

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

  public int getSampleRate() {
    return audioRecorderState.getSampleRate();
  }

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
