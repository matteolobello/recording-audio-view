package ml.matteolobello.recordingaudioview;

import android.Manifest;
import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.DrawableRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.IOException;

import io.codetail.animation.ViewAnimationUtils;

public class RecordingAudioView extends RelativeLayout implements View.OnTouchListener {

    /**
     * Log TAG
     */
    private static final String TAG = "RecordingAudioView";

    /**
     * Set to true for some logs
     */
    private static final boolean DEBUG = false;

    /**
     * Permissions we need to make the recording work
     */
    private static final String[] PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    /**
     * Total duration animation of the Shake animation
     */
    private static final int SHAKE_ERROR_ANIM_DURATION = 500;

    /**
     * The Context
     */
    private final Context mContext;

    /**
     * The Handler used to manage the update of the time
     */
    private final Handler mTimeUpdateHandler = new Handler();

    /**
     * The MediaRecorder object
     */
    private MediaRecorder mMediaRecorder;

    /**
     * The Views
     */
    private RelativeLayout mControlContainer;
    private AppCompatImageView mMicrophoneImageView;
    private AppCompatImageView mDestroyImageView;
    private View mRevealView;
    private View mRevealViewWrapper;

    /**
     * The Listener
     */
    private OnNewActionListener mOnNewActionListener;

    /**
     * The Validator
     */
    private CanRecordValidator mCanRecordValidator;

    /**
     * The Time Tick listener
     */
    private OnTimeTickListener mOnTimeTickListener;

    /**
     * The Reach Max time listener
     */
    private OnReachMaxTimeListener mOnReachTimeListener;

    /**
     * Recording output name
     */
    private String mFileName;

    /**
     * Save start ms of recording
     */
    private long mStartRecMs;

    /**
     * Max recording time in milliseconds
     */
    private int mMaxRecordingTimeMs = -1;

    /**
     * Icons color
     */
    private int mIconsColor = Color.WHITE;

    /**
     * Error icons color
     */
    private int mShakeForErrorIconColor = Color.RED;

    /**
     * Color of the reveal View when the recording has started/done
     */
    private int mRevealColor = Color.GREEN;

    /**
     * Color of the reveal View when the recording is destroyed
     */
    private int mRevealDestroyColor = Color.RED;

    /**
     * Float values to make calculations
     */
    private float mDeltaMicDragX;
    private float mViewCenter;

    /**
     * Boolean value to check if we should enable reveal animation
     */
    private boolean mEnableRevealAnimation = true;

    /**
     * Boolean value to check if we have ended our recording
     */
    private boolean mFinishedRecording;

    /**
     * Boolean value to check if we are currently recording
     */
    private boolean mIsRecording;

    /**
     * Boolean value to check if the Mic image is shaking
     */
    private boolean mIsShakingForError;

    /**
     * A dummy action Listener, makes us avoid null checking every time
     */
    private final OnNewActionListener DUMMY_ACTION_LISTENER = new OnNewActionListener() {
        @Override
        public void onDoneRecording(File outputFile) {
        }

        @Override
        public void onStartRecording() {
        }

        @Override
        public void onCancelRecording() {
        }
    };

    /**
     * A dummy Validator, makes us avoid null checking every time
     */
    private final CanRecordValidator DUMMY_CAN_RECORD_VALIDATOR = () -> true;

    /**
     * A dummy time tick Listener, makes us avoid null checking every time
     */
    private final OnTimeTickListener DUMMY_ON_TIME_TICK_LISTENER = msOfRecording -> {
    };

    /**
     * A dummy reach max time Listener, makes us avoid null checking every time
     */
    private final OnReachMaxTimeListener DUMMY_ON_REACH_MAX_TIME_LISTENER = () -> {
    };

    public RecordingAudioView(Context context) {
        this(context, null);
    }

    public RecordingAudioView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecordingAudioView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mOnNewActionListener = DUMMY_ACTION_LISTENER;
        mCanRecordValidator = DUMMY_CAN_RECORD_VALIDATOR;
        mOnTimeTickListener = DUMMY_ON_TIME_TICK_LISTENER;
        mOnReachTimeListener = DUMMY_ON_REACH_MAX_TIME_LISTENER;

        mContext = context;

        mControlContainer = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.recording_audio_layout, null);
        mMicrophoneImageView = mControlContainer.findViewById(R.id.microphone_icon);
        mDestroyImageView = mControlContainer.findViewById(R.id.destroy_icon);
        mRevealView = mControlContainer.findViewById(R.id.reveal_view);
        mRevealViewWrapper = mControlContainer.findViewById(R.id.reveal_view_wrapper);

        mMicrophoneImageView.setColorFilter(mIconsColor);
        mMicrophoneImageView.setOnTouchListener(this);

        mControlContainer.post(() -> mViewCenter = mControlContainer.getWidth() / 2 - mMicrophoneImageView.getWidth() / 2);

        mDestroyImageView.setColorFilter(mIconsColor);
        mDestroyImageView.animate().scaleX(0.0f).scaleY(0.0f).setDuration(0).start();

        mRevealColor = fetchPrimaryColor();

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RecordingAudioView);

        int iconsColor = typedArray.getColor(R.styleable.RecordingAudioView_recAudioViewIconsColor, mIconsColor);
        boolean revealEnabled = typedArray.getBoolean(R.styleable.RecordingAudioView_recAudioViewRevealAnimEnabled, mEnableRevealAnimation);
        int revealColor = typedArray.getColor(R.styleable.RecordingAudioView_recAudioViewRevealColor, mRevealColor);
        int revealDestroyColor = typedArray.getColor(R.styleable.RecordingAudioView_recAudioViewRevealDestroyColor, mRevealDestroyColor);
        Drawable micIconDrawable = typedArray.getDrawable(R.styleable.RecordingAudioView_recAudioViewMicImage);
        Drawable destroyIconDrawable = typedArray.getDrawable(R.styleable.RecordingAudioView_recAudioViewDestroyImage);
        String fileName = typedArray.getString(R.styleable.RecordingAudioView_recAudioViewFileName);
        int maxRecordingTimeMs = typedArray.getInteger(R.styleable.RecordingAudioView_recAudioViewMaxRecordingMs, -1);

        setIconsColor(iconsColor);
        setEnableRevealAnimation(revealEnabled);
        setRevealColor(revealColor);
        setRevealDestroyColor(revealDestroyColor);
        if (micIconDrawable != null) setMicImage(micIconDrawable);
        if (destroyIconDrawable != null) setDestroyImage(destroyIconDrawable);
        if (fileName != null) setFileName(fileName);
        setMaxRecordingTime(maxRecordingTimeMs);

        typedArray.recycle();

        addView(mControlContainer);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mIsShakingForError) {
                    return false;
                }

                if (!mCanRecordValidator.canRecord()) {
                    shakeForError();
                    return false;
                }

                if (!checkPermissions()) {
                    ActivityCompat.requestPermissions((Activity) mContext, PERMISSIONS, 200);
                    return false;
                }

                mDeltaMicDragX = view.getX() - motionEvent.getRawX();

                // When the finger is lifted down
                mMicrophoneImageView.animate().scaleX(1.4f).scaleY(1.4f).setDuration(100).start();
                mDestroyImageView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();

                mFinishedRecording = false;
                mStartRecMs = System.currentTimeMillis();

                mTimeUpdateHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mFinishedRecording) {
                            mOnTimeTickListener.onTimeTick(0);

                            mTimeUpdateHandler.removeCallbacks(this);
                            return;
                        }

                        long deltaMs = System.currentTimeMillis() - mStartRecMs;
                        mOnTimeTickListener.onTimeTick(deltaMs);

                        if (mMaxRecordingTimeMs != -1) {
                            if (deltaMs >= mMaxRecordingTimeMs) {
                                resetViewUi();

                                mOnReachTimeListener.onReachMaxTime();
                            }
                        }

                        mTimeUpdateHandler.postDelayed(this, 30);
                    }
                }, 30);

                startRecording();

                circularReveal(mMicrophoneImageView, mRevealColor, false);

                mIsRecording = true;

                mOnNewActionListener.onStartRecording();
                break;
            case MotionEvent.ACTION_MOVE:
                float newCalculatedX = motionEvent.getRawX() + mDeltaMicDragX;

                // Lock slide from left to right
                if (newCalculatedX > mViewCenter) {
                    log("Slide to right detected!");
                    return false;
                }

                // Check if microphone is overlapping with de destroy image
                if (checkMicCollision(newCalculatedX)) {
                    log("Collision of the mic detected!");

                    newCalculatedX = 0;
                }

                final float newOffset = newCalculatedX / mViewCenter;

                float newMicAlpha = newOffset - 0.3f;
                mMicrophoneImageView.animate()
                        .x(newCalculatedX)
                        .alpha(newMicAlpha)
                        .setDuration(0)
                        .start();
                break;
            case MotionEvent.ACTION_UP:
                // When the finger is lifted up, rescale microphone to default width/height
                // and collapse the destroy View
                mMicrophoneImageView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                mDestroyImageView.animate().scaleX(0.0f).scaleY(0.0f).setDuration(100).start();

                stopRecording();

                if (mMicrophoneImageView.getAlpha() < 0.15f) {
                    // Destroy recording
                    mOnNewActionListener.onCancelRecording();

                    circularReveal(mDestroyImageView, mRevealDestroyColor, true);

                    deleteRecordingFile();
                } else {
                    // Success
                    mOnNewActionListener.onDoneRecording(new File(mFileName));

                    circularHide(mMicrophoneImageView, mRevealColor);
                }

                mFinishedRecording = true;
                mIsRecording = false;

                centerMicrophone();
                break;
        }

        return true;
    }

    public void resetViewUi() {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = downTime + 10;

        MotionEvent motionEvent = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_UP, 0, 0, 0
        );

        dispatchTouchEvent(motionEvent);
    }

    public void shakeForError() {
        // In total, there are 3 movements/animations,
        // so divide the total amount of time by three
        final int singleMovementDuration = SHAKE_ERROR_ANIM_DURATION / 3;

        ValueAnimator anim = new ValueAnimator();
        anim.setIntValues(mIconsColor, mShakeForErrorIconColor);
        anim.setEvaluator(new ArgbEvaluator());
        anim.addUpdateListener(valueAnimator -> mMicrophoneImageView.setColorFilter((Integer) valueAnimator.getAnimatedValue()));
        anim.setDuration(SHAKE_ERROR_ANIM_DURATION / 2);
        anim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                ValueAnimator anim = new ValueAnimator();
                anim.setIntValues(mShakeForErrorIconColor, mIconsColor);
                anim.setEvaluator(new ArgbEvaluator());
                anim.addUpdateListener(valueAnimator -> mMicrophoneImageView.setColorFilter((Integer) valueAnimator.getAnimatedValue()));
                anim.setDuration(SHAKE_ERROR_ANIM_DURATION / 2);
                anim.start();
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        anim.start();

        mMicrophoneImageView.animate()
                .rotationBy(-30)
                .setDuration(singleMovementDuration)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                        mIsShakingForError = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        mMicrophoneImageView.animate()
                                .rotationBy(60)
                                .setDuration(singleMovementDuration)
                                .setListener(new Animator.AnimatorListener() {
                                    @Override
                                    public void onAnimationStart(Animator animator) {
                                    }

                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        mMicrophoneImageView.animate()
                                                .rotationBy(-30)
                                                .setDuration(singleMovementDuration)
                                                .setListener(new Animator.AnimatorListener() {
                                                    @Override
                                                    public void onAnimationStart(Animator animator) {
                                                    }

                                                    @Override
                                                    public void onAnimationEnd(Animator animator) {
                                                        mIsShakingForError = false;

                                                        // Clear the listener for the future animations
                                                        mMicrophoneImageView.animate().setListener(null);
                                                    }

                                                    @Override
                                                    public void onAnimationCancel(Animator animator) {
                                                    }

                                                    @Override
                                                    public void onAnimationRepeat(Animator animator) {
                                                    }
                                                })
                                                .start();
                                    }

                                    @Override
                                    public void onAnimationCancel(Animator animator) {
                                    }

                                    @Override
                                    public void onAnimationRepeat(Animator animator) {
                                    }
                                }).start();
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }
                }).start();
    }

    private boolean checkMicCollision(float newX) {
        Rect rect = new Rect();
        mDestroyImageView.getDrawingRect(rect);

        return rect.contains((int) newX, (int) mMicrophoneImageView.getY())
                || newX < rect.left;
    }

    private boolean checkPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private void stopRecording() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteRecordingFile() {
        new File(getFileName()).delete();
    }

    private void centerMicrophone() {
        log("Centering mic, center = " + mViewCenter);

        mDestroyImageView.setColorFilter(mIconsColor);

        mMicrophoneImageView.animate()
                .x(mViewCenter)
                .alpha(1.0f)
                .setDuration(100)
                .start();
    }

    private void startRecording() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setOutputFile(getFileName());
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.start();
    }

    private void circularReveal(View originView, int revealViewColor, boolean fadeAfter) {
        if (!mEnableRevealAnimation) {
            return;
        }

        if (mRevealColor != revealViewColor) {
            mRevealViewWrapper.setBackgroundColor(mRevealColor);
        }

        mRevealView.setVisibility(VISIBLE);
        mRevealView.setBackgroundColor(revealViewColor);

        int cx = (originView.getLeft() + originView.getRight()) / 2;
        int cy = (originView.getTop() + originView.getBottom()) / 2;

        int dx = Math.max(cx, originView.getWidth() - cx);
        int dy = Math.max(cy, originView.getHeight() - cy);

        float finalRadius = (float) Math.hypot(dx, dy) * 2;

        Animator animator =
                ViewAnimationUtils.createCircularReveal(mRevealView, cx, cy, 0, finalRadius);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(250);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (fadeAfter) {
                    mRevealView.animate()
                            .alpha(0.0f)
                            .setDuration(200)
                            .setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {
                                    mRevealViewWrapper.setBackgroundColor(Color.TRANSPARENT);
                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    mRevealView.setAlpha(1.0f);
                                    mRevealView.setVisibility(INVISIBLE);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {
                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {
                                }
                            }).start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.start();
    }

    private void circularHide(View originView, int revealViewColor) {
        if (!mEnableRevealAnimation) {
            return;
        }

        mRevealView.setVisibility(VISIBLE);
        mRevealView.setBackgroundColor(revealViewColor);

        int cx = (originView.getLeft() + originView.getRight()) / 2;
        int cy = (originView.getTop() + originView.getBottom()) / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(mRevealView.getWidth(), mRevealView.getHeight());

        Animator animator =
                ViewAnimationUtils.createCircularReveal(mRevealView, cx, cy, finalRadius, 0);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(250);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mRevealView.setVisibility(INVISIBLE);
                mRevealViewWrapper.setBackgroundColor(Color.TRANSPARENT);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.start();
    }

    private void log(Object what) {
        if (DEBUG) {
            Log.d(TAG, what != null ? String.valueOf(what) : "null");
        }
    }

    private int fetchPrimaryColor() {
        TypedValue typedValue = new TypedValue();
        TypedArray typedArray = mContext.obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorPrimary});
        int color = typedArray.getColor(0, 0);

        typedArray.recycle();

        return color;
    }

    public void setNewActionListener(OnNewActionListener onNewActionListener) {
        mOnNewActionListener = onNewActionListener;
    }

    public void setCanRecordValidator(CanRecordValidator canRecordValidator) {
        mCanRecordValidator = canRecordValidator;
    }

    public void setOnTimeTickListener(OnTimeTickListener onTimeTickListener) {
        mOnTimeTickListener = onTimeTickListener;
    }

    public void setMaxRecordingTimeReachListener(OnReachMaxTimeListener onReachMaxTimeListener) {
        mOnReachTimeListener = onReachMaxTimeListener;
    }

    public void setIconsColor(int color) {
        mIconsColor = color;

        mMicrophoneImageView.setColorFilter(mIconsColor);
        mDestroyImageView.setColorFilter(mIconsColor);
    }

    public void setEnableRevealAnimation(boolean value) {
        mEnableRevealAnimation = value;
    }

    public void setRevealColor(int revealColor) {
        mRevealColor = revealColor;
    }

    public void setRevealDestroyColor(int revealDestroyColor) {
        mRevealDestroyColor = revealDestroyColor;
    }

    public void setShakeErrorMicIconColor(int color) {
        if (mIsRecording) {
            Log.w(TAG, "Setting error icons while recording is not recommended");
        }

        mShakeForErrorIconColor = color;
    }

    public void setMicImage(@DrawableRes int resId) {
        mMicrophoneImageView.setImageResource(resId);
    }

    public void setMicImage(Drawable drawable) {
        mMicrophoneImageView.setImageDrawable(drawable);
    }

    public void setMicImage(Bitmap bitmap) {
        mMicrophoneImageView.setImageBitmap(bitmap);
    }

    public void setDestroyImage(@DrawableRes int resId) {
        mDestroyImageView.setImageResource(resId);
    }

    public void setDestroyImage(Drawable drawable) {
        mDestroyImageView.setImageDrawable(drawable);
    }

    public void setDestroyImage(Bitmap bitmap) {
        mDestroyImageView.setImageBitmap(bitmap);
    }

    public void setFileName(String fileName) {
        if (mIsRecording) {
            throw new IllegalStateException("You cannot set output File name while recording");
        }

        if (!fileName.startsWith("/sdcard/")) {
            fileName = "/sdcard/" + fileName;
        }

        if (!fileName.endsWith(".3gp")) {
            fileName += ".3gp";
        }

        mFileName = fileName;
    }

    public void setMaxRecordingTime(int maxRecordingTimeMs) {
        mMaxRecordingTimeMs = maxRecordingTimeMs;
    }

    public void disableMaxRecordingTime() {
        mMaxRecordingTimeMs = -1;
    }

    public OnNewActionListener getOnNewActionListener() {
        return mOnNewActionListener;
    }

    public CanRecordValidator getCanRecordValidator() {
        return mCanRecordValidator;
    }

    public OnTimeTickListener getOnTimeTickListener() {
        return mOnTimeTickListener;
    }

    public OnReachMaxTimeListener getOnReachMaxTimeListener() {
        return mOnReachTimeListener;
    }

    public int getIconsColor() {
        return mIconsColor;
    }

    public int getMicShakeErrorColor() {
        return mShakeForErrorIconColor;
    }

    public Drawable getMicDrawable() {
        return mMicrophoneImageView.getDrawable();
    }

    public Drawable getDestroyIconDrawable() {
        return mMicrophoneImageView.getDrawable();
    }

    public String getFileName() {
        if (mFileName == null) {
            return null;
        }

        return mFileName.endsWith(".3gp") ? mFileName : mFileName.concat(".3gp");
    }

    public int getMaxRecordingTimeMs() {
        return mMaxRecordingTimeMs;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    public interface OnNewActionListener {

        /**
         * When the user has started the recording
         */
        void onStartRecording();

        /**
         * When the user has finished the recording
         */
        void onDoneRecording(File outputFile);

        /**
         * When the user has dismissed the recording
         */
        void onCancelRecording();
    }

    public interface CanRecordValidator {

        /**
         * Add a validator to check if we can start recording
         *
         * @return true if we can start recording
         */
        boolean canRecord();
    }

    public interface OnTimeTickListener {

        /**
         * Update your views or handle time with this listener,
         * refreshed every 30ms
         *
         * @param durationOfRecording the ms passed from the start of the recording
         */
        void onTimeTick(long durationOfRecording);
    }

    public interface OnReachMaxTimeListener {

        /**
         * Called only if the Max recording time is set
         */
        void onReachMaxTime();
    }
}
