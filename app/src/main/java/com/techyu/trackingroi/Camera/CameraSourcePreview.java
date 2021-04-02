package com.techyu.trackingroi.Camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.common.images.Size;
import com.techyu.trackingroi.Utils.Utils;

import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
    private static final String TAG = "CameraSourcePreview";

    //PREVIEW VISUALIZERS FOR BOTH CAMERA1 AND CAMERA2 API.
    private final SurfaceView mSurfaceView;
    private final AutoFitTextureView mAutoFitTextureView;

    private boolean usingCameraOne;//判断用的camera还是camera2
    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private boolean viewAdded = false;

    //CAMERA SOURCES FOR BOTH CAMERA1 AND CAMERA2 API.
    private CameraSource mCameraSource;
    private Camera2Source mCamera2Source;
    private Camera2Source.CameraError mCamera2SourceErrorHandler;

    private GraphicOverlay mOverlay;
    private final int screenWidth;
    private final int screenHeight;
    private final int screenRotation;

    //设置预览尺寸
    private static int PREVIEW_WIDTH = 1080;
    private static int PREVIEW_HEIGHT = 1920;

    /**
     * CameraSourcePreview构造方法，实例化了SurfaceView和AutoFitTextureView
     * @param context
     */

    public CameraSourcePreview(Context context) {
        super(context);
        screenHeight = Utils.getScreenHeight(context);
        screenWidth = Utils.getScreenWidth(context);
        screenRotation = Utils.getScreenRotation(context);
        mStartRequested = false;
        mSurfaceAvailable = false;
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(mSurfaceViewListener);
        mAutoFitTextureView = new AutoFitTextureView(context);
        mAutoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        screenHeight = Utils.getScreenHeight(context);
        screenWidth = Utils.getScreenWidth(context);
        screenRotation = Utils.getScreenRotation(context);
        System.out.println("-----------height+width+rotation----------"+screenHeight+","+screenWidth+","+screenRotation);
        mStartRequested = false;
        mSurfaceAvailable = false;
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(mSurfaceViewListener);
        mAutoFitTextureView = new AutoFitTextureView(context);
        mAutoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    public void start(@NonNull CameraSource cameraSource, @NonNull GraphicOverlay overlay) {
        usingCameraOne = true;
        mOverlay = overlay;
        start(cameraSource);
    }

    public void start(@NonNull Camera2Source camera2Source, @NonNull GraphicOverlay overlay, @NonNull Camera2Source.CameraError errorHandler) {
        usingCameraOne = false;
        mOverlay = overlay;
        System.out.println("进入了camera2 start");
        start(camera2Source, errorHandler);
    }

    private void start(@NonNull CameraSource cameraSource) {
        mCameraSource = cameraSource;
        mStartRequested = true;
        if(!viewAdded) {
            addView(mSurfaceView);
            viewAdded = true;
        }
        try {startIfReady();} catch (IOException e) {Log.e(TAG, "Could not start camera source.", e);}
    }

    private void start(@NonNull Camera2Source camera2Source, Camera2Source.CameraError errorHandler) {
        mCamera2Source = camera2Source;
        mCamera2SourceErrorHandler = errorHandler;
        mStartRequested = true;
        if(!viewAdded) {
            addView(mAutoFitTextureView);
            viewAdded = true;
        }
        try {startIfReady();} catch (IOException e) {Log.e(TAG, "Could not start camera source.", e);}
    }

    public void stop() {
        mStartRequested = false;
        if(usingCameraOne) {
            if (mCameraSource != null) {
                mCameraSource.stop();
            }
        } else {
            if(mCamera2Source != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mCamera2Source.stop();
                }
            }
        }
    }

    private void startIfReady() throws IOException {
        if (mStartRequested && mSurfaceAvailable) {
            try {
                if(usingCameraOne) {
                    mCameraSource.start(mSurfaceView.getHolder());
                    if (mOverlay != null) {
                        Size size = mCameraSource.getPreviewSize();
                        if(size != null) {
                            int min = Math.min(size.getWidth(), size.getHeight());
                            int max = Math.max(size.getWidth(), size.getHeight());
                            // FOR GRAPHIC OVERLAY, THE PREVIEW SIZE WAS REDUCED TO QUARTER
                            // IN ORDER TO PREVENT CPU OVERLOAD
                            mOverlay.setCameraInfo(min/4, max/4, mCameraSource.getCameraFacing());
                            mOverlay.clear();
                        } else {
                            stop();
                        }
                    }
                    mStartRequested = false;
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mCamera2Source.start(mAutoFitTextureView, screenRotation, mCamera2SourceErrorHandler);
                        if (mOverlay != null) {
                            Size size = mCamera2Source.getPreviewSize();
                            if(size != null) {
                                int min = Math.min(size.getWidth(), size.getHeight());
                                int max = Math.max(size.getWidth(), size.getHeight());
                                // FOR GRAPHIC OVERLAY, THE PREVIEW SIZE WAS REDUCED TO QUARTER
                                // IN ORDER TO PREVENT CPU OVERLOAD
                                mOverlay.setCameraInfo(min/4, max/4, mCamera2Source.getCameraFacing());
                                mOverlay.clear();
                            } else {
                                stop();
                            }
                        }
                        mStartRequested = false;
                    }
                }
            } catch (SecurityException e) {Log.d(TAG, "SECURITY EXCEPTION: "+e);}
        }
    }

    private final SurfaceHolder.Callback mSurfaceViewListener = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surface) {
            mSurfaceAvailable = true;
            mOverlay.bringToFront();
            try {startIfReady();} catch (IOException e) {Log.e(TAG, "Could not start camera source.", e);}
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder surface) {
            mSurfaceAvailable = false;
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            mSurfaceAvailable = true;
            mOverlay.bringToFront();
            try {startIfReady();} catch (IOException e) {Log.e(TAG, "Could not start camera source.", e);}
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            mSurfaceAvailable = false;
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    };

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = 720;
        int width = 480;
        if(usingCameraOne) {
            if (mCameraSource != null) {
                Size size = mCameraSource.getPreviewSize();
                if (size != null) {
                    // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
                    height = size.getWidth();
                    System.out.println("-----------PreviewSize1宽度----------"+height);
                }
            }
        } else {
            if (mCamera2Source != null) {
                Size size = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    size = mCamera2Source.getPreviewSize();
                }
                if (size != null) {
                    // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
                    height = size.getWidth();
                    width = size.getHeight();
                    System.out.println("-----------PreviewSize2预览高度宽度----------"+height+","+width);
                }
            }
        }

        //RESIZE PREVIEW IGNORING ASPECT RATIO. THIS IS ESSENTIAL.
        int newWidth = (height * screenWidth) / screenHeight;
        System.out.println("-----------newWidth宽度----------"+newWidth);
        final int layoutWidth = right - left;
        final int layoutHeight = bottom - top;
        System.out.println("-----------layoutWidth----------"+layoutWidth);
        System.out.println("-----------layoutHeight----------"+layoutHeight);
        // Computes height and width for potentially doing fit width.
        int childWidth = layoutWidth;//设置自定义view的子空间大小位置
        int childHeight = layoutWidth*height/width;//用预览的纵横比来设置高度，保证人脸不拉升失真
        if(childHeight>layoutHeight){
            childWidth=layoutWidth;
            childHeight = (layoutWidth)*height/width-200;
            System.out.println("进入了高度修正"+childWidth+childHeight);
        }
        childWidth = PREVIEW_WIDTH;
        childHeight = PREVIEW_HEIGHT;


        System.out.println("-----------childHeight,childWidth----------"+childHeight+","+childWidth);
        // If height is too tall using fit width, does fit height instead.
//        if (childHeight > layoutHeight) {
//            childHeight = layoutHeight;
//            childWidth = (int)(((float) layoutHeight / (float) height) * newWidth);
//        }
        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }
        System.out.println("-----------childHeight,childWidth----------"+childHeight+","+childWidth);
        try {
            startIfReady();
        }
        catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }
}