package es.upv.comm.lokudemo;

import android.Manifest;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnTouch;
import comm.rxaudio.R;
import es.upv.comm.loku.Player;
import es.upv.comm.loku.Recorder;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 0;

  CompositeDisposable compositeDisposable;

  @BindView(R.id.audio_power)
  TextView powerView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.RECORD_AUDIO},
        MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
  }

  @OnTouch(R.id.button)
  public boolean onAudioBtnTouch(View v, MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        Timber.d("Down");
        subscribe();
        break;
      case MotionEvent.ACTION_UP:
        Timber.d("Up");
        unsubscribe();
        break;
    }
    return false;
  }

  private void subscribe() {
    compositeDisposable = new CompositeDisposable();

    // create a recorder
    Recorder recorder = new Recorder(getApplicationContext());
    compositeDisposable.add(recorder);

    // create a player and subscribe it to the loku recorder
    Player player = new Player(getApplicationContext());
    compositeDisposable.add(
        recorder.getAudioFlowable()
            .observeOn(Schedulers.newThread())
            .subscribeWith(player));

    // add another subscriber that maps audio samples to their power and shows on the ui
    compositeDisposable.add(
        recorder.getAudioFlowable()
            .map(AudioTools::audioPower)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSubscriber<Integer>() {
              @Override
              public void onNext(Integer integer) {
                powerView.setText(integer.toString());
              }

              @Override
              public void onError(Throwable t) {
                Timber.e(t, "Error on loku Recorder flowable");
              }

              @Override
              public void onComplete() {
                Timber.d("Recorder flowable completed");
              }
            }));
  }

  private void unsubscribe() {
    if (!compositeDisposable.isDisposed()) {
      compositeDisposable.dispose();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
      compositeDisposable.dispose();
    }
  }
}
