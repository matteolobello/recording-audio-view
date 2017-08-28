package ml.matteolobello.recordingaudioviewsample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import ml.matteolobello.recordingaudioview.RecordingAudioView;

public class MainActivity extends AppCompatActivity implements
        RecordingAudioView.OnNewActionListener, RecordingAudioView.OnTimeTickListener,
        RecordingAudioView.CanRecordValidator, RecordingAudioView.OnReachMaxTimeListener {

    private static final String OUTPUT_FILE_PATH = "/sdcard/HelloWorld.3gp";

    private RecordingAudioView mRecordingAudioView;
    private TextView mTimeTextView;
    private CheckBox mEnableErrorCheckBox;
    private EditText mMaxMsEditText;
    private Button mMaxMsSetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecordingAudioView = (RecordingAudioView) findViewById(R.id.recording_audio_view);
        mRecordingAudioView.setFileName(OUTPUT_FILE_PATH);
        mRecordingAudioView.setMaxRecordingTimeReachListener(this);
        mRecordingAudioView.setNewActionListener(this);
        mRecordingAudioView.setOnTimeTickListener(this);
        mRecordingAudioView.setCanRecordValidator(this);

        mTimeTextView = (TextView) findViewById(R.id.ms_text_view);
        mEnableErrorCheckBox = (CheckBox) findViewById(R.id.enable_error_check_box);
        mMaxMsEditText = (EditText) findViewById(R.id.max_recording_time_edit_text);
        mMaxMsSetButton = (Button) findViewById(R.id.max_recording_set_button);
        mMaxMsSetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String inputMsString = mMaxMsEditText.getText().toString();
                if (TextUtils.isEmpty(inputMsString)) {
                    Toast.makeText(MainActivity.this, "Set a value, please", Toast.LENGTH_SHORT).show();
                    return;
                }

                int ms = Integer.valueOf(inputMsString);
                mRecordingAudioView.setMaxRecordingTime(ms);

                Toast.makeText(MainActivity.this, "Max Recording Time: " + ms + "ms", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onStartRecording() {
        Toast.makeText(this, "onStartRecording()", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDoneRecording(File outputFile) {
        Toast.makeText(this, "onDoneRecording() -> Output File = " + outputFile.getPath(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCancelRecording() {
        Toast.makeText(this, "onCancelRecording()", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTimeTick(long durationOfRecording) {
        mTimeTextView.setText(String.valueOf(durationOfRecording));
    }

    @Override
    public boolean canRecord() {
        return !mEnableErrorCheckBox.isChecked();
    }

    @Override
    public void onReachMaxTime() {
        Toast.makeText(this, mRecordingAudioView.getMaxRecordingTimeMs() + "ms reached", Toast.LENGTH_SHORT).show();
    }
}
