# RecordingAudioView
[![Download](https://api.bintray.com/packages/ohmylob/RecordingAudioView/RecordingAudioView/images/download.svg?version=RecordingAudioView) ](https://bintray.com/ohmylob/RecordingAudioView/RecordingAudioView/RecordingAudioView/link)

### Setup:
Add these lines to your build.gradle file.
```
repositories {
    maven { url "http://dl.bintray.com/ohmylob/RecordingAudioView" }
    maven { url "https://jitpack.io" }
}
```
```
compile 'ml.matteolobello.recordingaudioview:recordingaudioview:1.0'
```

### How to use
##### XML Attributes
```xml
<ml.matteolobello.recordingaudioview.RecordingAudioView
    android:id="@+id/record_audio_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:recAudioViewIconsColor="@color/white"
    app:recAudioViewMicImage="@drawable/ic_mic"
    app:recAudioViewDestroyImage="@drawable/ic_destroy"
    app:recAudioViewRevealAnimEnabled="true"
    app:recAudioViewRevealColor="@color/colorPrimary"
    app:recAudioViewRevealDestroyColor="@color/red"
    app:recAudioViewMaxRecordingMs="3000"
    app:recAudioViewFileName="/sdcard/Hello.3gp" />
```
##### Java
```java
mRecordingAudioView.setIconsColor(Color.WHITE);
mRecordingAudioView.setMicImage(R.drawable.ic_mic);
mRecordingAudioView.setDestroyImage(R.drawable.ic_destroy);
mRecordingAudioView.setEnableRevealAnimation(true);
mRecordingAudioView.setRevealColor(Color.BLUE);
mRecordingAudioView.setRevealDestroyColor(Color.RED);
mRecordingAudioView.setMaxRecordingTime(3000);
mRecordingAudioView.setFileName("/sdcard/Hello.3gp");
mRecordingAudioView.setNewActionListener(new RecordingAudioView.OnNewActionListener() {
        @Override
        public void onStartRecording() {
        }

        @Override
        public void onDoneRecording(File outputFile) {
        }

        @Override
        public void onCancelRecording() {
        }
});
mRecordingAudioView.setMaxRecordingTimeReachListener(new RecordingAudioView.OnReachMaxTimeListener() {
        @Override
        public void onReachMaxTime() {
        }
});
mRecordingAudioView.setCanRecordValidator(new RecordingAudioView.CanRecordValidator() {
        @Override
        public boolean canRecord() {
            return true;
        }
});
mRecordingAudioView.setOnTimeTickListener(new RecordingAudioView.OnTimeTickListener() {
        @Override
        public void onTimeTick(long durationOfRecording) {
        }
});
```
