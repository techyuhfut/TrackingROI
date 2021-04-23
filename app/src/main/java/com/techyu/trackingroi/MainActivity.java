package com.techyu.trackingroi;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.techyu.trackingroi.Camera.Camera2Source;
import com.techyu.trackingroi.Camera.FaceGraphic;
import com.techyu.trackingroi.Utils.Algorithm;
import com.techyu.trackingroi.Utils.DynamicWave;
import com.techyu.trackingroi.databinding.ActivityMainBinding;
import com.techyu.trackingroi.Utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.techyu.trackingroi.Camera.CameraSource;
import com.techyu.trackingroi.Camera.CameraSourcePreview;
import com.techyu.trackingroi.Camera.GraphicOverlay;

import org.apache.commons.math3.stat.ranking.NaNStrategy;

import static java.lang.Float.NaN;
import static java.lang.Float.isNaN;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Camera2 Vision";
    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    private float mEulerX;
    private float mEulerY;
    private float mEulerZ;

    private Handler AvgRGBHandler; //计算像素平均线程
    private Handler RealtimeHRHandler; //计算实时心率线程
    private Handler DWTransHandler;//绘制动态波形线程



    private String image_test;
    private String image_test1;

    private Bitmap mbitmap;
    private Bitmap bitmap;

    public boolean ROIFlag = true;
    public boolean AvgRGBFlag = false;
    public boolean frameFlag = true;

    final public int FRAME_NUM=512;
    final public int WINDOW_SIZE=256;
    final public int DATA_PREPARE=60;  //受相机自动变焦影响，前60个数据舍弃

    public int cntFrame = 0;
    public int cntFace = 0;
    public int cntPrepare = 0;
    public long mRecordTime = 0;

    long timer = 0;

    private DynamicWave dynamicWave;


    public float[][] pulse_rgb=new float[3][FRAME_NUM];
    public float[][] dynamic_rgb=new float[3][WINDOW_SIZE]; //存储滑动数组，用于计算实时心率
//    public static volatile int heartRateValue;
    public static int heartRateValue;



    // CAMERA VERSION ONE DECLARATIONS
    private CameraSource mCameraSource = null;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private FaceDetector previewFaceDetector = null;
    private FaceGraphic mFaceGraphic;
    private boolean wasActivityResumed = false;
    private boolean isRecordingVideo = false;
    private boolean flashEnabled = true;

    // DEFAULT CAMERA BEING OPENED
    private boolean usingFrontCamera = true;

    // MUST BE CAREFUL USING THIS VARIABLE.
    // ANY ATTEMPT TO START CAMERA2 ON API < 21 WILL CRASH.
    private boolean useCamera2 = false;

    private ActivityMainBinding binding;
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(binding.getRoot());
        context = getApplicationContext();
        String rootDir = this.getExternalFilesDir("pic").getAbsolutePath();
        Log.d(TAG, "onCreate: rootDir"+rootDir);
        image_test = rootDir+"/test.png";
        image_test1 = rootDir+"/test1.png";
        //绘制动态波形
        dynamicWave=(DynamicWave)findViewById(R.id.dynamic_wave);
        dynamicWave.setWaveColor(0x7fFF9A18);
        Handler DWTrans_handle = getDWTransHandler();
        if (DWTrans_handle != null) {
            DWTrans_handle.post(new DWTransThread(-1, true, true));
        }


        //设置按键相关的点击事件
        if(checkGooglePlayAvailability()) {
            //请求权限+打开相机
            requestPermissionThenOpenCamera();
            //切换前后相机
            binding.switchButton.setOnClickListener(v -> {
                if(usingFrontCamera) {
                    stopCameraSource();//先停止preview
                    createCameraSourceBack();
                    usingFrontCamera = false;
                } else {
                    stopCameraSource();
                    createCameraSourceFront();
                    usingFrontCamera = true;
                }
            });
            //打开闪光灯选项
            binding.flashButton.setOnClickListener(v -> {
                if(useCamera2) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if(flashEnabled) {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_OFF);
                            flashEnabled = false;
                            Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                        } else {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_ON);
                            flashEnabled = true;
                            Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    if(flashEnabled) {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_OFF);
                        flashEnabled = false;
                        Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                    } else {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_ON);
                        flashEnabled = true;
                        Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            //设置拍照按键
            binding.takePictureButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                if(useCamera2) {
                    if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mCamera2Source.takePicture(camera2SourceShutterCallback, camera2SourcePictureCallback);
                    }
                } else {
                    if(mCameraSource != null)mCameraSource.takePicture(cameraSourceShutterCallback, cameraSourcePictureCallback);
                }
            });
            //设置获取视频按钮
            binding.videoButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                if(isRecordingVideo) {
                    if(useCamera2) {
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.stopVideo();
                        }
                    } else {
                        if(mCameraSource != null)mCameraSource.stopVideo();
                    }
                }
                else {
                    if(useCamera2){
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.recordVideo(camera2SourceVideoStartCallback, camera2SourceVideoStopCallback, camera2SourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                        }
                    } else {
                        if(mCameraSource != null) {
                            if(mCameraSource.canRecordVideo(CamcorderProfile.QUALITY_720P)) {
                                mCameraSource.recordVideo(cameraSourceVideoStartCallback, cameraSourceVideoStopCallback, cameraSourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                            }
                        }
                    }
                }
            });
            binding.mPreview.setOnTouchListener(CameraPreviewTouchListener);
        }
//        //此处代码测试用，保存ROI图片到本地查看，仅开发用，发布版本绝不能包含（因为涉嫌侵犯用户隐私）
//        while(true){
//            if(bitFlag&&onceFlag){
//                File file = new File(image_test);
//                onceFlag = false;
//                try {
//                    file.createNewFile();
//                    FileOutputStream fos = new FileOutputStream(file);
//                    mbitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//                    fos.close();
//                    Log.d(TAG, "onCreate: 执行了保存");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            break;
//        }

    }
    //This is the end of onCreatView

    /**
     * 安卓初始Camera api相关回调设置
     */

    //api拍照声回调
    final CameraSource.ShutterCallback cameraSourceShutterCallback = () -> {
        //you can implement here your own shutter triggered animation
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback!");
            }
        });
    };
    //拍照回调
    final CameraSource.PictureCallback cameraSourcePictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(Bitmap picture) {
            Log.d(TAG, "Taken picture is ready!");
            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera_picture.png"));
                picture.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    //视频回调
    final CameraSource.VideoStartCallback cameraSourceVideoStartCallback = new CameraSource.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    //关闭相机录制回调
    final CameraSource.VideoStopCallback cameraSourceVideoStopCallback = new CameraSource.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    //相机error回调
    final CameraSource.VideoErrorCallback cameraSourceVideoErrorCallback = new CameraSource.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };

    /**
     * 安卓 camera2 api相关回调设置
     */
    //录制video开始的相机回调
    final Camera2Source.VideoStartCallback camera2SourceVideoStartCallback = new Camera2Source.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    //停止 录制视频的回调
    final Camera2Source.VideoStopCallback camera2SourceVideoStopCallback = new Camera2Source.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    //录制视频错误的回调函数
    final Camera2Source.VideoErrorCallback camera2SourceVideoErrorCallback = new Camera2Source.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };
    //相机拍照声的回调函数
    final Camera2Source.ShutterCallback camera2SourceShutterCallback = () -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback for CAMERA2");
            }
        });
    };
    //相机拍照的回调函数
    final Camera2Source.PictureCallback camera2SourcePictureCallback = new Camera2Source.PictureCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onPictureTaken(Bitmap image) {
            Log.d(TAG, "Taken picture is ready!");

            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera2_picture.png"));
                image.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    //相机开启错误的回调函数
    final Camera2Source.CameraError camera2SourceErrorCallback = new Camera2Source.CameraError() {
        //相机打开正常
        @Override
        public void onCameraOpened() {
            runOnUiThread(() -> binding.status.setText("camera2 open success"));
        }
        @Override
        public void onCameraDisconnected() {}
        @Override
        public void onCameraError(int errorCode) {
            runOnUiThread(() -> {
                binding.status.setText(String.format(getString(R.string.errorCode)+" ", errorCode));
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(false);
                builder.setTitle(getString(R.string.cameraError));
                builder.setMessage(String.format(getString(R.string.errorCode)+" ", errorCode));
                builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    binding.switchButton.setEnabled(false);
                    binding.takePictureButton.setEnabled(false);
                    binding.videoButton.setEnabled(false);
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            });
        }
    };

    /**
     * 检查google play是否支持
     * @return
     */
    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        if(resultCode == ConnectionResult.SUCCESS) {
            binding.status.setText("google play is available");
            return true;
        } else {
            if(googleApiAvailability.isUserResolvableError(resultCode)) {
                Objects.requireNonNull(googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404)).show();
            }
        }
        return false;
    }

    /**
     * 申请相机和读写权限
     */
    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                useCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                createCameraSourceFront();//权限判断完后，打开相机
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            binding.status.setText("requesting camera permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * 按照系统版本和支持情况开启前置相机和人脸特征点提取和追踪
     */
    private void createCameraSourceFront() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)//首要识别主要面部
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
            binding.status.setText("face detector not available");
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_AUTO)//原始为CAMERA_AF_CONTINUOUS_PICTURE,自动对焦太频繁
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_FRONT)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_FRONT)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_AUTO)//原始CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }

    /**
     * 按照系统版本和支持情况开启后置
     * 相机和人脸特征点提取和追踪
     */
    private void createCameraSourceBack() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            binding.status.setText("face detector not available");
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_OFF)
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_BACK)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }
    /*
    开启相机预览，判断使用camera API还是camera2
     */
    private void startCameraSource() {
        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//安卓版本大于5.0启用Camera2
                if (mCamera2Source != null) {
                    binding.cameraVersion.setText(context.getString(R.string.cameraTwo));
                    binding.mPreview.start(mCamera2Source, binding.mGraphicOverlay, camera2SourceErrorCallback);
                }
            }
        } else {
            if (mCameraSource != null) {
                binding.cameraVersion.setText(context.getString(R.string.cameraOne));
                binding.mPreview.start(mCameraSource, binding.mGraphicOverlay);
            }
        }
    }
    /*
    停止相机预览
     */
    private void stopCameraSource() {
        binding.mPreview.stop();
    }

    /*
    人脸跟踪，工厂类
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(@NonNull Face face) {
            return new GraphicFaceTracker(binding.mGraphicOverlay);
        }
    }
    /*
    内部类，人脸特征点追踪
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private final GraphicOverlay mOverlay;
//        private Handler AvgRGBHandler; //计算心率平均线程
        /*
        构造函数
         */
        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, context);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, @NonNull Face item) {
            mFaceGraphic.setId(faceId);
            Log.d(TAG, "NEW FACE ID: "+faceId);
        }

        /**
         * 更新人脸特征点位置
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(@NonNull FaceDetector.Detections<Face> detectionResults, @NonNull Face face) {
            long times = System.currentTimeMillis();
            if(cntPrepare<DATA_PREPARE){
//                timer.setBase(SystemClock.elapsedRealtime());//计时器复位
            }
            cntFace++;
            Log.d(TAG, "onUpdate: 获取第"+cntFace+"帧人脸");
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            List<PointF> landmarkPositions =  mFaceGraphic.getLandmarkPositions();
            int[] facePositions =  mFaceGraphic.getFacePositions();
            mbitmap = mCamera2Source.getBitmap();
//            if(ROIFlag&cntFrame==100){
//                File file = new File(image_test);
//                try {
//                    file.createNewFile();
//                    FileOutputStream fos = new FileOutputStream(file);
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//                    fos.close();
//                    Log.d(TAG, "保存ROI区域");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            float RTfps = (float)cntFrame / duration_sum;
//            float RTfps = 30;
            //启动RGB均值计算线程
            if(frameFlag){
                AvgRGBHandler = getAvgRGBHandler();
                if (AvgRGBHandler != null) {
                    AvgRGBHandler.post(new AvgRGBThread(mbitmap,facePositions,landmarkPositions)); //后台进程，实现实时getRGB
                }
            }
            System.out.println("face位置"+face.getWidth()+"---"+face.getHeight()+"---"+face.getPosition());
            BigDecimal b0= new BigDecimal(face.getEulerX());//获取欧拉角
            BigDecimal b1= new BigDecimal(face.getEulerY());
            BigDecimal b2= new BigDecimal(face.getEulerZ());
            mEulerX = b0.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();//保留两位小数，方便阅读
            mEulerY = b1.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();
            mEulerZ = b2.setScale(2,BigDecimal.ROUND_HALF_UP).floatValue();
            runOnUiThread(() -> {
                binding.eulerX.setText("上下点头:"+mEulerX);
                binding.eulerY.setText("左右转头:"+mEulerY);
                binding.eulerZ.setText("左右摆头:"+mEulerZ);
                if(heartRateValue!=0)
                binding.heartrate.setText("HeartRate:"+heartRateValue);
            });

            //getRGB
            //获取rgb值 像素点个数，输出数组长度
//            if(frameFlag){//当处理来不及时自动丢弃部分 图像帧不计算
//                frameFlag = false;
//                int left = facePositions[2];
//                int right = facePositions[3];
//                int top = mbitmap.getHeight()-facePositions[1];
//                int bottom = mbitmap.getHeight()-facePositions[0];
//                int height = bottom-top;
//                int width = right-left;
//                if(top+height>mbitmap.getHeight()) height = mbitmap.getHeight()-top;
//                if(left+width>mbitmap.getWidth()) width = mbitmap.getWidth()-left;
//                System.out.println("left"+left+"right"+right+"top"+top+"bottom"+bottom+"height"+height+"width"+width);
//                bitmap = Bitmap.createBitmap(mbitmap, left, top, width, height);
//                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
//                System.out.println("---cntFrame---"+cntFrame);
//                int N = width * height;
//                int[] pixels = new int[N];
//                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//                float[][] rgb_raw= new float[3][N];
//                float[] rgb=new float[3];
//                int NonROIPointsNum = 0;
//                for (int i = 0; i < N; i++) {//这里相当于对原始的rgb数据分开储存
//                    int clr = pixels[i];//此处都还相当于int型数据
//                    int R = (clr & 0x00ff0000) >> 16;
//                    int G = (clr & 0x0000ff00) >> 8;
//                    int B = clr & 0x000000ff;
//                    //YCrCb颜色空间用于皮肤检测
//                    float[] rgb_frame={R,G,B};
////                float[] YCrCb = Algorithm.RGB2YCrCb(rgb_frame);
//
//                    rgb_raw[0][i] = R; // red 取高两位
//                    rgb_raw[1][i] = G; // green 取中两位
//                    rgb_raw[2][i] = B; // blue 取低两位
//
//                /*if((Algorithm.skinDetectionByYCrCb(YCrCb))) { //皮肤检测
//                    rgb_raw[0][i] = R; // red 取高两位
//                    rgb_raw[1][i] = G; // green 取中两位
//                    rgb_raw[2][i] = B; // blue 取低两位
//                }else{
//                    NonROIPointsNum++;
//                    rgb_raw[0][i] = 0;
//                    rgb_raw[1][i] = 0;
//                    rgb_raw[2][i] = 0;
//                }*/
//                }
//                //计算完整bitmap像素均值
//                rgb[0] = Algorithm.calculateAvg(rgb_raw[0]);
//                rgb[1] = Algorithm.calculateAvg(rgb_raw[1]);
//                rgb[2] = Algorithm.calculateAvg(rgb_raw[2]);
//
//            /*//获取皮肤区域像素均值
//            rgb[0] = (rgb[0] * N) / (N - NonROIPointsNum);
//            rgb[1] = (rgb[1] * N) / (N - NonROIPointsNum);
//            rgb[2] = (rgb[2] * N) / (N - NonROIPointsNum);
//            }*/
//
//                times =System.currentTimeMillis()-times;
//                Log.d(TAG, "getRGB: 耗时"+times);
//                frameFlag = true;
//            }
            Log.d(TAG, "NEW KNOWN FACE UPDATE: "+face.getId());
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(@NonNull FaceDetector.Detections<Face> detectionResults) {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            runOnUiThread(() -> {
                binding.eulerX.setText("EulerX:"+0);//丢失
                binding.eulerY.setText("EulerY:"+0);
                binding.eulerZ.setText("EulerZ:"+0);
                cntFrame=0;
                cntPrepare=0;
                heartRateValue = 0;
                binding.heartrate.setText(" ");
                Toast.makeText(MainActivity.this,"FACE MISSING",Toast.LENGTH_SHORT).show();
            });
            Log.d(TAG, "FACE MISSING");
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            mOverlay.clear();
            Log.d(TAG, "FACE GONE");
        }

        /**
         * 按照人脸区域进行裁剪
         * @param mBitmap height-1080 width-1920
         * @para landmarkPositions 人脸框位置信息 width-1080 height-1920
         *                          List<PointF> landmarkPositions,
         * @return
         */
        public Bitmap cropBitmap(Bitmap mBitmap,int[] facePositions){
//            int left = mBitmap.getWidth() / 2-100;
//            int top = mBitmap.getHeight() / 2-mBitmap.getHeight()/4;
//            int right = left+mBitmap.getWidth() / 4;
//            int bottom = top+mBitmap.getHeight() / 2;
            Bitmap bitmap;
            int left = facePositions[2];
            int right = facePositions[3];
            int top = facePositions[1];
            int bottom = facePositions[0];
            System.out.println("left"+left+"right"+right+"top"+top+"bottom"+bottom);
            bitmap = Bitmap.createBitmap(mBitmap, left, top, right-left, top-bottom);
            Log.d("裁剪","bitmap width="+bitmap.getWidth()+" bitmap height="+bitmap.getHeight());
            return bitmap;
        }

    }
    //计算像素平均线程
    public Handler getAvgRGBHandler() {
        if (AvgRGBHandler == null) {
            HandlerThread thread = new HandlerThread("AvgRGB");
            thread.start();
            AvgRGBHandler = new Handler(thread.getLooper());
        }
        return AvgRGBHandler;
    }

    //计算实时心率进程
    public Handler getRealtimeHRHandler() {
        if (RealtimeHRHandler == null) {
            HandlerThread thread = new HandlerThread("RTHr");
            thread.start();
            RealtimeHRHandler = new Handler(thread.getLooper());
        }
        return RealtimeHRHandler;
    }

    //向DynamicWave传递数据和状态信息
    public Handler getDWTransHandler() {
        if (DWTransHandler == null) {
            HandlerThread thread = new HandlerThread("DWTrans");
            thread.start();
            DWTransHandler = new Handler(thread.getLooper());
        }
        return DWTransHandler;
    }

    //本线程用于绘制DynamicWave
    private class DWTransThread implements Runnable{
        float data;
        boolean isTransFinish;
        boolean isReset;
        public DWTransThread(float data_input,boolean isTransFinish_input,boolean isReset_input){
            data=data_input;
            isTransFinish=isTransFinish_input;
            isReset=isReset_input;
        }
        @Override
        public void run() {
            if(isReset){//reset状态优先级最高
                dynamicWave.reset();
            }else{
                dynamicWave.transDataNotInvalidate(data);
                if(isTransFinish){//结束数据传入，此时可滑动数据
                    dynamicWave.finishTransData();
                }
                dynamicWave.postInvalidate();//更新绘图
            }
        }
    }

    //本线程专门处理获取实时像素平均值
    private class AvgRGBThread implements Runnable {
        Bitmap mbitmap;
        int[] facePositions;//getFacePositions left right top bottom
        float[] input_data;
        List<PointF> landmarkPositions;

        public AvgRGBThread(Bitmap mbitmap,int[] facePositions,List<PointF> landmarkPositions) {
            this.mbitmap=mbitmap;
            this.facePositions=facePositions;
            this.landmarkPositions=landmarkPositions;
        }

        @Override
        public void run() {
            frameFlag = false;
            long times = System.currentTimeMillis();
            try {
                if (cntPrepare < DATA_PREPARE) {//丢弃前面60帧数据
                    cntPrepare++;
                    Log.d("cntPrepare准备", ""+cntPrepare);
                } else{
                    if (cntFrame < FRAME_NUM) {
                        if(cntFrame==0){
                            timer = System.currentTimeMillis();
                        }
                        Log.d(TAG, "run: 执行次数");
//                        int left = facePositions[2];//对应屏幕的底部边缘
//                        int right = facePositions[3]-100;//对应屏幕的上部
//                        int top = mbitmap.getHeight() - facePositions[1];//对应屏幕的右边缘
//                        int bottom = mbitmap.getHeight() - facePositions[0];//对应屏幕的左边缘
                        int left = mbitmap.getWidth()-facePositions[3];//小
                        int right = mbitmap.getWidth()-facePositions[2];
                        int top = mbitmap.getHeight() - facePositions[1];//小
                        int bottom = mbitmap.getHeight() - facePositions[0];
                        int height = bottom - top;
                        int width = right - left;
                        if (top + height > mbitmap.getHeight()) height = mbitmap.getHeight() - top;
                        if (left + width > mbitmap.getWidth()) width = mbitmap.getWidth() - left;
                        System.out.println("left" + left + "right" + right + "top" + top + "bottom" + bottom + "height" + height + "width" + width);
                        Matrix matrix = new Matrix();//旋转矩阵
                        PointF faceCenter = landmarkPositions.get(8);
                        matrix.postRotate(-mEulerZ,faceCenter.x,faceCenter.y);
//                        Canvas cv = new Canvas( bitmap);
//                        cv.rotate(-mEulerZ,faceCenter.x,faceCenter.y);
//                        bitmap = Bitmap.createBitmap(mbitmap, 0, 0, mbitmap.getHeight(), mbitmap.getHeight() ,matrix,true);
                        bitmap = Bitmap.createBitmap(mbitmap, left, top, width, height,matrix,true);//旋转
                        bitmap = Bitmap.createBitmap(mbitmap, left, top, width, height);//仅裁剪
                        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        saveROI();
                        Log.d("cntFrame采集", ""+cntFrame);
                        //getRGB
                        //获取rgb值 像素点个数，输出数组长度
                        int N = width * height;
                        int[] pixels = new int[N];
                        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                        float[][] rgb_raw = new float[3][N];
                        float[] rgb = new float[3];
                        int NonROIPointsNum = 0;
                        for (int i = 0; i < N; i++) {//这里相当于对原始的rgb数据分开储存
                            int clr = pixels[i];//此处都还相当于int型数据
                            int R = (clr & 0x00ff0000) >> 16;
                            int G = (clr & 0x0000ff00) >> 8;
                            int B = clr & 0x000000ff;
                            //YCrCb颜色空间用于皮肤检测
                            float[] rgb_frame = {R, G, B};
                            float[] YCrCb = Algorithm.RGB2YCrCb(rgb_frame);

                            rgb_raw[0][i] = R; // red 取高两位
                            rgb_raw[1][i] = G; // green 取中两位
                            rgb_raw[2][i] = B; // blue 取低两位

                            if ((Algorithm.skinDetectionByYCrCb(YCrCb))) { //皮肤检测
                                rgb_raw[0][i] = R; // red 取高两位
                                rgb_raw[1][i] = G; // green 取中两位
                                rgb_raw[2][i] = B; // blue 取低两位
                            } else {
                                NonROIPointsNum++;
                                rgb_raw[0][i] = 0;
                                rgb_raw[1][i] = 0;
                                rgb_raw[2][i] = 0;}
                        }
                        //计算完整bitmap像素均值
                        rgb[0] = Algorithm.calculateAvg(rgb_raw[0]);
                        rgb[1] = Algorithm.calculateAvg(rgb_raw[1]);
                        rgb[2] = Algorithm.calculateAvg(rgb_raw[2]);

                        //获取皮肤区域像素均值
                        rgb[0] = (rgb[0] * N) / (N - NonROIPointsNum);
                        rgb[1] = (rgb[1] * N) / (N - NonROIPointsNum);
                        rgb[2] = (rgb[2] * N) / (N - NonROIPointsNum);

                    //计算实时采样率
                    long duration = System.currentTimeMillis();
                    duration = (duration - timer)/1000;//ms 转 s
                    if(duration==0) duration = 1;
                    Log.d("持续时间", ""+duration);
                    float RTfps = (float) cntFrame / duration;
                    Log.d("实时采样率", ""+RTfps);
                    //开启心率计算线程
//                    RealtimeHRHandler = getRealtimeHRHandler();
//                    if (RealtimeHRHandler != null) {
//                        RealtimeHRHandler.post(new RealtimeHRThread(rgb_raw,RTfps,N,NonROIPointsNum)); //后台进程，实现心率计算
//                    }
                    float[] hsv = Algorithm.RGB2HSV(rgb);
                    //保存每帧H通道pulse数据
                    if (cntFrame < FRAME_NUM) {
//                    pulse_rgb[0][cntFrame] = rgb_original[0]; //red
//                    pulse_rgb[2][cntFrame] = rgb_original[2]; //blue
//                    pulse_hsv[cntFrame] = hsv[0]; //调试用
//                    pulse_green_debug[cntFrame]=rgb_original[1]; //调试用
                    if(isNaN(hsv[0])){
                            cntFrame--;}
                    else{
                            pulse_rgb[1][cntFrame] = hsv[0];} //H通道
                    }
                    if(cntFrame>WINDOW_SIZE && cntFrame%10==0){
                        for (int i = cntFrame - WINDOW_SIZE; i < cntFrame; i++) {
//                        dynamic_rgb[0][i - cntFrame + WINDOW_SIZE] = pulse_rgb[0][i];
                            dynamic_rgb[1][i - cntFrame + WINDOW_SIZE] = pulse_rgb[1][i];
//                        dynamic_rgb[2][i - cntFrame + WINDOW_SIZE] = pulse_rgb[2][i];
                        }
                    }
                    input_data = dynamic_rgb[1];//H通道
                    Log.d("原始波形",Arrays.toString(input_data));
//                    float smoothed_data;
//                    if(cntFrame>=3 && cntFrame<FRAME_NUM){
//                        smoothed_data = (input_data[cntFrame - 2] + input_data[cntFrame-1] + input_data[cntFrame]) / 3;
//                    }else{
//                        smoothed_data = input_data[cntFrame];
//                    }
//                    if(cntFrame==(FRAME_NUM-1)){
//                        //当前批次数据传入完毕，传入数据后执行finish
//                        Handler DWTrans_handle = getDWTransHandler();
//                        if (DWTrans_handle != null) {
//                            DWTrans_handle.post(new DWTransThread(smoothed_data, true, false));
//                            //DWTrans_handle.post(new DWTransThread(pulse_rgb[0][cnt], true, false));
//                        }
//                    }else{
//                        //传入数据
//                        Handler DWTrans_handle = getDWTransHandler();
//                        if (DWTrans_handle != null) {
//                            DWTrans_handle.post(new DWTransThread(smoothed_data, false, false)); }
//                    }
                    if(cntFrame>WINDOW_SIZE&&cntFrame<FRAME_NUM) {
                        //计算实时心率
                        if(RTfps<10.0 ){
                            System.out.println("程序卡顿！！！");
                        }else if(RTfps<20){
                            System.out.println("温馨提示：您的手机性能较低，可能导致检测精度下降！");
                        }
                        //提取脉搏波
                        float[] output_data=ExtractPulseWave(input_data,RTfps);
                        Log.d("提取波形",Arrays.toString(output_data));
                        //计算心率
                        //heartRateValue=ComputeHrByPeakDetection(output_data,RTfps);
                        double[] spec = Algorithm.CalcPulseSpectrum(output_data,RTfps);
                        Log.d("实时频谱：",Arrays.toString(spec));
                        int loc = Algorithm.findMaxLocation(spec);
                        heartRateValue = (int) (60 * RTfps * loc / spec.length);
                        Log.d("HeartRate结果：",heartRateValue+" ,loc="+loc+" ,N="+spec.length);
                        //实时心率筛选、存储与显示
                    }
                    else if(cntFrame == FRAME_NUM){//最终心率结果
                        //计算实时心率
                        if(RTfps<10.0 ){
                            System.out.println("程序卡顿！！！");
                        }else if(RTfps<20){
                            System.out.println("温馨提示：您的手机性能较低，可能导致检测精度下降！");
                        }
                        //提取脉搏波
                        float[] output_data=ExtractPulseWave(pulse_rgb[1],RTfps);//选取全局计算
                        Log.d("提取波形",Arrays.toString(output_data));
                        //计算心率
                        //heartRateValue=ComputeHrByPeakDetection(output_data,RTfps);
                        double[] spec = Algorithm.CalcPulseSpectrum(output_data,RTfps);
                        Log.d("实时频谱：",Arrays.toString(spec));
                        int loc = Algorithm.findMaxLocation(spec);
                        heartRateValue = (int) (60 * RTfps * loc / spec.length);
                        Log.d("HeartRate结果：",heartRateValue+" ,loc="+loc+" ,N="+spec.length);
                    }

                    }
                    //开始计数
                    if(cntFrame==FRAME_NUM) {
                        cntFrame=0;
                        cntPrepare = 0;
                    }
                    cntFrame++;
                }
                }
        catch(Exception e){
            e.printStackTrace();}
            finally{
                times = System.currentTimeMillis() - times;
                Log.d(TAG, "getRGB: 耗时" + times);
            }
            frameFlag = true;
        }
    }


    //本线程专门处理获取实时心率的计算
    private class RealtimeHRThread implements Runnable {
        float[][] rgb_raw;
        float[] rgb_original = new float[3];
        float[] rgb = new float[3];
        float[] input_data;
        float RTfps;
        int N;
        int NonROIPointsNum;

        public RealtimeHRThread(float[][] rgb_raw, float fs,int N,int NonROIPointsNum) {
            this.rgb_raw=rgb_raw;
            this.RTfps=fs;
            this.N=N;
            this.NonROIPointsNum=NonROIPointsNum;
            if(this.RTfps>30){
                this.RTfps=30;
            }
            Log.d("实时采样率：",Float.toString(RTfps));
        }

        @Override
        public void run() {
            long times = System.currentTimeMillis();
            Log.d("HeartRate", "进入了");
            //计算完整bitmap像素均值
            rgb[0] = Algorithm.calculateAvg(rgb_raw[0]);
            rgb[1] = Algorithm.calculateAvg(rgb_raw[1]);
            rgb[2] = Algorithm.calculateAvg(rgb_raw[2]);
            //获取每一帧皮肤区域像素均值
            rgb_original[0] = (rgb[0] * N) / (N - NonROIPointsNum);
            rgb_original[1] = (rgb[1] * N) / (N - NonROIPointsNum);
            rgb_original[2] = (rgb[2] * N) / (N - NonROIPointsNum);
            //获取波形数据
            float[] hsv = Algorithm.RGB2HSV(rgb_original);
            if (cntFrame < FRAME_NUM) {
//                pulse_rgb[0][cntFrame] = rgb_original[0]; //red
//                pulse_rgb[2][cntFrame] = rgb_original[2]; //blue
//                  pulse_hsv[cntFrame] = hsv[0]; //调试用
//                  pulse_green_debug[cntFrame]=rgb_original[1]; //调试用
                if(hsv[0] == 0.0|| isNaN(hsv[0])){
                    cntFrame--;
                }
                else{
                    pulse_rgb[1][cntFrame] = hsv[0]; //H通道
                }
            }
            //当cnt长度大于滑动窗口时开始计算实时心率
//            for (int i = cntFrame - WINDOW_SIZE; i < cntFrame; i++) {
//                dynamic_rgb[0][i - cntFrame + WINDOW_SIZE] = pulse_rgb[0][i];
//                dynamic_rgb[1][i - cntFrame + WINDOW_SIZE] = pulse_rgb[1][i];
//                dynamic_rgb[2][i - cntFrame + WINDOW_SIZE] = pulse_rgb[2][i];
//            }
            //设置输入通道
            input_data = pulse_rgb[1];//H通道
            Log.d("原始波形",Arrays.toString(input_data));
            try {
                if(cntFrame>=600) {
//                    Log.d("R通道波形：",Arrays.toString(rgb_original[0]));
//                    Log.d("G通道波形：",Arrays.toString(rgb_original[1]));
//                    Log.d("B通道波形：",Arrays.toString(rgb_original[2]));
                    //计算实时心率
                    if(RTfps<10.0 ){
                        System.out.println("程序卡顿！！！");
                    }else if(RTfps<20){
                        System.out.println("温馨提示：您的手机性能较低，可能导致检测精度下降！");
                    }
                    //提取脉搏波
                    float[] output_data=ExtractPulseWave(input_data,RTfps);
                    Log.d("提取波形",Arrays.toString(output_data));
                    //计算心率
                    //heartRateValue=ComputeHrByPeakDetection(output_data,RTfps);
                    double[] spec = Algorithm.CalcPulseSpectrum(output_data,RTfps);
                    Log.d("实时频谱：",Arrays.toString(spec));
                    int loc = Algorithm.findMaxLocation(spec);
                    heartRateValue = (int) (60 * RTfps * loc / spec.length);
                    Log.d("HeartRate结果：",heartRateValue+" ,loc="+loc+" ,N="+spec.length);
                    //实时心率筛选、存储与显示
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                times =System.currentTimeMillis()-times;
                Log.d(TAG, "实时心率: 耗时"+times);
            }
        }
    }

    //标准脉搏波提取
    public float[] ExtractPulseWave(float[] input_data, float RTfps){

        float[] output_data = new float[input_data.length];

        //归一化处理
        float max=Algorithm.findMax(input_data);
        float min=Algorithm.findMin(input_data);
        float avg=Algorithm.calculateAvg(input_data);
        if(max==min){
//            error_flag=true;
            return input_data;
        }
        for(int i=0;i<input_data.length;i++){
            output_data[i]=(input_data[i]-avg)/(max-min);
        }
        Log.d("1、归一化：",Arrays.toString(output_data));

        output_data = Algorithm.MovingAverageFilter_3P(output_data);
        Log.d("2、平滑：" , Arrays.toString(output_data));

        //理想带通，采样点数必须2的指数幂
        output_data = Algorithm.IdealPassing(output_data, RTfps, (float) 0.8, (float) 3.0);
        Log.d("3、理想带通输出：" , Arrays.toString(output_data));

        //尖峰压缩（波形跳变时，理想带通输出出现尖峰，通过该算法进行平滑）
        output_data = Algorithm.SharpCompress(output_data,16);
        Log.d("4、尖峰压缩输出：" , Arrays.toString(output_data));
        return output_data;
    }
    /**
     * 保存ROI区域到本地
     */
    public void saveROI(){
        if(ROIFlag&&cntFrame==100){
            File file = new File(image_test);
            try {
                file.createNewFile();
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                Log.d(TAG, "保存ROI区域");
            } catch (IOException e) {
                e.printStackTrace();
            }
            ROIFlag =false;
        }
    }

    /**
     * 设置触摸点击对焦事件
     */
    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent pEvent) {
            v.onTouchEvent(pEvent);
            if (pEvent.getAction() == MotionEvent.ACTION_DOWN) {
                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);
                binding.ivAutoFocus.setTranslationX(autoFocusX);
                binding.ivAutoFocus.setTranslationY(autoFocusY);
                binding.ivAutoFocus.setVisibility(View.VISIBLE);
                binding.ivAutoFocus.bringToFront();
                binding.status.setText("focusing...");
                if(useCamera2) {
                    if(mCamera2Source != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            //needs to know in which zone of the screen is auto focus requested
                            // some Camera2 devices support multi-zone focusing.
                            mCamera2Source.autoFocus(success -> runOnUiThread(() -> {
                                binding.ivAutoFocus.setVisibility(View.GONE);
                                binding.status.setText("focus OK");
                            }), pEvent, v.getWidth(), v.getHeight());
                        }
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                } else {
                    if(mCameraSource != null) {
                        mCameraSource.autoFocus(success -> runOnUiThread(() -> {
                            binding.ivAutoFocus.setVisibility(View.GONE);
                            binding.status.setText("focus OK");
                        }));
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                }
            }
            if(pEvent.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return false;
        }
    };

    /**
     * 权限申请结果判断
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "CAMERA PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if(requestCode == REQUEST_STORAGE_PERMISSION) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "STORAGE PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(wasActivityResumed)
            //If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
            if(useCamera2) {
                if(usingFrontCamera) {
                    createCameraSourceFront();
                } else {
                    createCameraSourceBack();
                }
            } else {
                startCameraSource();
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
        if(previewFaceDetector != null) {
            previewFaceDetector.release();
        }
    }
}