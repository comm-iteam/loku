package es.upv.comm.lokudemo;

import android.app.Application;

import java.util.Properties;

import timber.log.Timber;

public class MainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
