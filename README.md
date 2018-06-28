Loku Audio Library for Android
==============================
Loku is a simple audio stream API for Android using rxjava 2

Download
--------
You can import the Loku library from Maven using:
```
compile 'es.upv.comm:loku:0.0.1'
```
 
Usage
-----
* Download and import it. Right now you can do it manually
* Add permissions
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```
* Use it
```java
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
```
* Don't forget to unsubscribe
```java
    if (!compositeDisposable.isDisposed()) {
      compositeDisposable.dispose();
    }
```

## LICENSE

    Copyright 2016 Multimedia Communications Group (https://www.comm.upv.es/es/)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
