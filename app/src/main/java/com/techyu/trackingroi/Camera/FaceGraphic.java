package com.techyu.trackingroi.Camera;

/**
 * Created by Techyu
 */

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;
import com.techyu.trackingroi.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private final Bitmap marker;
    private float isSmilingProbability = -1;
    private float eyeRightOpenProbability = -1;
    private float eyeLeftOpenProbability = -1;
    private PointF leftEyePos = null;
    private PointF rightEyePos = null;
    private PointF noseBasePos = null;
    private PointF leftMouthCorner = null;
    private PointF rightMouthCorner = null;
    private PointF mouthBase = null;
    private PointF leftEar = null;
    private PointF rightEar = null;
    private PointF leftEarTip = null;
    private PointF rightEarTip = null;
    private PointF leftCheek = null;
    private PointF rightCheek = null;
    private PointF faceCenter = null;
    int faceLeft = 0;
    int faceRight = 0;
    int faceTop = 0;
    int faceBottom = 0;
    private volatile Face mFace;

    public FaceGraphic(GraphicOverlay overlay, Context context) {
        super(overlay);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inScaled = false;
        Resources resources = context.getResources();
        marker = BitmapFactory.decodeResource(resources, R.drawable.marker, opt);
    }

    public void setId(int id) {
    }

    public float getSmilingProbability() {
        return isSmilingProbability;
    }

    public float getEyeRightOpenProbability() {
        return eyeRightOpenProbability;
    }

    public float getEyeLeftOpenProbability() {
        return eyeLeftOpenProbability;
    }

    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    public void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    public void goneFace() {
        mFace = null;
    }


    /**
     * 返回特征点位置信息
     * @return 人脸特征点位置
     */
    public List<PointF> getLandmarkPositions(){
//        System.out.println("左眼位置"+leftEyePos.x+"---"+leftEyePos.y);
//        System.out.println("右眼位置"+rightEyePos.x+"---"+rightEyePos.y);
//        System.out.println("鼻子位置"+noseBasePos.x+"---"+noseBasePos.y);
        List<PointF> positions =new ArrayList<>();
        positions.add(leftEyePos);
        positions.add(rightEyePos);
        positions.add(noseBasePos);
        positions.add(leftMouthCorner);
        positions.add(rightMouthCorner);
        positions.add(mouthBase);
        positions.add(leftCheek);
        positions.add(rightCheek);
        positions.add(faceCenter);
        return positions;
    }

    public int[] getFacePositions(){
        int[] positions =new int[4];
        positions[0] = faceLeft;
        positions[1] = faceRight;
        positions[2] = faceTop;
        positions[3] = faceBottom;
        System.out.println("faceLeft"+faceLeft+"faceRight"+faceRight+"faceTop"+faceTop+"faceBottom"+faceBottom);
        return positions;
    }


    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if(face == null) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            isSmilingProbability = -1;
            eyeRightOpenProbability= -1;
            eyeLeftOpenProbability = -1;
            return;
        }

        PointF facePosition = new PointF(translateX(face.getPosition().x), translateY(face.getPosition().y));
        float faceWidth = face.getWidth() * 4;//这里为什么要乘4
        float faceHeight = face.getHeight() * 4;
        faceCenter = new PointF(translateX(face.getPosition().x + faceWidth / 8), translateY(face.getPosition().y + faceHeight / 8));
        faceLeft = (int)translateX(face.getPosition().x + faceWidth / 8-0.125f*faceWidth);
        faceRight = (int)translateX(face.getPosition().x + faceWidth / 8+0.125f*faceWidth);
        faceTop = (int)translateY(face.getPosition().y + faceHeight / 8-0.125f*faceHeight);
        faceBottom = (int)translateY(face.getPosition().y + faceHeight / 8+0.125f*faceHeight);
        isSmilingProbability = face.getIsSmilingProbability();
        eyeRightOpenProbability = face.getIsRightEyeOpenProbability();
        eyeLeftOpenProbability = face.getIsLeftEyeOpenProbability();
        float eulerY = face.getEulerY();
        float eulerZ = face.getEulerZ();
        //DO NOT SET TO NULL THE NON EXISTENT LANDMARKS. USE OLDER ONES INSTEAD.
        for(Landmark landmark : face.getLandmarks()) {
            switch (landmark.getType()) {
                case Landmark.LEFT_EYE:
                    leftEyePos = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.RIGHT_EYE:
                    rightEyePos = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.NOSE_BASE:
                    noseBasePos = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.LEFT_MOUTH:
                    leftMouthCorner = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.RIGHT_MOUTH:
                    rightMouthCorner = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.BOTTOM_MOUTH:
                    mouthBase = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.LEFT_EAR:
                    leftEar = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.RIGHT_EAR:
                    rightEar = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.LEFT_EAR_TIP:
                    leftEarTip = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.RIGHT_EAR_TIP:
                    rightEarTip = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.LEFT_CHEEK:
                    leftCheek = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
                case Landmark.RIGHT_CHEEK:
                    rightCheek = new PointF(translateX(landmark.getPosition().x), translateY(landmark.getPosition().y));
                    break;
            }
        }

        Paint mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(4);
        mPaint.setStyle(Paint.Style.STROKE);
//        canvas.rotate(-eulerZ,faceCenter.x,faceCenter.y);//设置矩形旋转
        if(faceCenter != null){
            canvas.drawBitmap(marker, faceCenter.x, faceCenter.y, null);
//            绘制人脸矩形框
            canvas.rotate(-eulerZ,faceCenter.x,faceCenter.y);//设置矩形旋转
            canvas.drawRect(faceLeft,faceTop,faceRight,faceBottom,mPaint);
            //原始自适应的额头参数
//            canvas.drawRect(faceLeft+faceHeight/15,faceTop,faceRight-faceHeight/15,faceBottom-(faceHeight*2/7),mPaint);
            //修改为固定1920*1080的预览分辨率
            canvas.drawRect(faceLeft+faceHeight/8,faceTop,faceRight-faceHeight/8,faceBottom-(faceHeight*3/4),mPaint);
            canvas.rotate(eulerZ,faceCenter.x,faceCenter.y);//将画布转整，防止后面的面部特征点一起旋转
        }
        if(noseBasePos != null)
            canvas.drawBitmap(marker, noseBasePos.x, noseBasePos.y, null);
        if(leftEyePos != null)
            canvas.drawBitmap(marker, leftEyePos.x, leftEyePos.y, null);
        if(rightEyePos != null)
            canvas.drawBitmap(marker, rightEyePos.x, rightEyePos.y, null);
        if(mouthBase != null)
            canvas.drawBitmap(marker, mouthBase.x, mouthBase.y, null);
        if(leftMouthCorner != null)
            canvas.drawBitmap(marker, leftMouthCorner.x, leftMouthCorner.y, null);
        if(rightMouthCorner != null)
            canvas.drawBitmap(marker, rightMouthCorner.x, rightMouthCorner.y, null);
        //关闭显示耳朵
//        if(leftEar != null)
//            canvas.drawBitmap(marker, leftEar.x, leftEar.y, null);
//        if(rightEar != null)
//            canvas.drawBitmap(marker, rightEar.x, rightEar.y, null);
//        if(leftEarTip != null)
//            canvas.drawBitmap(marker, leftEarTip.x, leftEarTip.y, null);
//        if(rightEarTip != null)
//            canvas.drawBitmap(marker, rightEarTip.x, rightEarTip.y, null);
        if(leftCheek != null)
            canvas.drawBitmap(marker, leftCheek.x, leftCheek.y, null);
        if(rightCheek != null)
            canvas.drawBitmap(marker, rightCheek.x, rightCheek.y, null);
    }

}