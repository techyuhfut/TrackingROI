package com.example.myapplicationhr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.example.myapplicationhr.R;

import java.util.Vector;

/**动态波形绘制，支持滑动，自适应数值范围显示
 *目前没加入放大缩小功能
 *@author xuesong.han
 */

public class DynamicWave extends View {

    private float gap_x;//x轴上相邻两点像素间隔

    private int width,height;//本页面宽，高

    private Vector data;//数据点

    private static float startX;//手指touch屏幕时候的x坐标
    private static float startY;

    private float x_change =0.0f;//手指滑动时相对startX的像素变化
    private float y_change=0.0f;

    //设置显示窗口位移
    private float window_displaceX=0;
    private float window_displaceY=0;

    private float data_len_x;//x轴数据上所有数据像素总长度(data.size()*gap_x)
    private float data_len_y;

    private int draw_num_x=250;//一个窗口长度内数据点数目，决定每两个相邻数据点距离

    private float slider_speed_x=0.4f;
    private float slider_speed_y;
    private float slider_rect_speed_x=0.2f;
    private float slider_rect_speed_y;

    //x与y坐标轴上滑块大小
    private float slider_rect_height_x;
    private float slider_rect_width_x;
    private float slider_rect_displace_x=0.0f;
    private int slider_rect_alpha=20;//滑块透明度


    private float window_displace_threshold_ratioX=0.0f;//当data_len_x>(window_displace_threshold_ratioX*width)时，再传入数据window_displaceX开始增加
    private float window_displace_thresholdX=0.0f;//window_displace_thresholdX=(window_displace_threshold_ratioX*width)
    //是否正在传送数据给DynamicWave，传递数据时不可滑动
    private boolean isTransData=false;



    private boolean has_finger_slider=false;//判断手指是否滑动过，true：显示滑块

    //画笔
    Paint grid_paint;
    Paint wave_paint;
    Paint slider_rect_paint;

    public DynamicWave(Context context, AttributeSet attrs){
        super(context,attrs);
        initView();
    }

    public DynamicWave(Context context){
        super(context);
        initView();
    }

    public void initView(){

        this.setWillNotDraw(false);
        //背景色
        this.setBackgroundColor(Color.TRANSPARENT);
//        this.getBackground().setAlpha(20);

        data=new Vector();

        grid_paint=new Paint();
        grid_paint.setStyle(Paint.Style.STROKE);
        grid_paint.setAntiAlias(true);
        grid_paint.setStrokeWidth(1.0f);
        grid_paint.setColor(getResources().getColor(R.color.black));


        wave_paint=new Paint();
        wave_paint.setStyle(Paint.Style.STROKE);
        wave_paint.setStrokeWidth(9.0f);//波形宽度
        wave_paint.setColor(getResources().getColor(R.color.white));
        wave_paint.setAlpha(150);


        slider_rect_paint=new Paint();
        slider_rect_paint.setStyle(Paint.Style.FILL);
        slider_rect_paint.setStrokeWidth(1.0f);
        slider_rect_paint.setAntiAlias(true);
        slider_rect_paint.setColor(Color.TRANSPARENT);
        slider_rect_paint.setAlpha(slider_rect_alpha);//设置透明度要放在最后要不不生效

        postInvalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed){
            width = getWidth();
            height = getHeight();

            data_len_y=height;

            data_len_x=0;
            gap_x=width/(draw_num_x*1.0f);
            window_displaceX=-width;

            window_displace_thresholdX=window_displace_threshold_ratioX*width;
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        DrawDynamicWave(canvas);
    }

    /**
     * 画动态波形
     */
    private void DrawDynamicWave(Canvas canvas){
        Path path = new Path();

        Vector draw_data_pos_x=new Vector();
        Vector draw_data_pose_id=new Vector();
        Vector draw_data_pos_y=new Vector();

        //获得当前窗口内需要绘制的点x轴的像素坐标和id
        for(int i=0;i<data.size();++i){
            if((i*gap_x)>(window_displaceX+width)){
                break;
            }else{
                float t_pos_x=(i*gap_x)-window_displaceX;
                if(t_pos_x>0){
                    draw_data_pose_id.add(i);
                    draw_data_pos_x.add(t_pos_x);
                }
            }
        }

        //计算要显示点的最大值和最小值
        float draw_y_min=0;
        float draw_y_max=0;
        boolean flag=false;
        for(int i=0;i<draw_data_pose_id.size();++i){
            int id=(int)draw_data_pose_id.get((i));
            float t_y=(float)data.get(id);
            if(flag==false){
                draw_y_min=t_y;
                draw_y_max=t_y;
                flag=true;
                continue;
            }
            if(draw_y_min>t_y){
                draw_y_min=t_y;
            }
            if(draw_y_max<t_y){
                draw_y_max=t_y;
            }
        }


        //根据求得的最大值和最小值确定绘制点y轴的像素坐标
        for(int i=0;i<draw_data_pose_id.size();++i){
            int id=(int)draw_data_pose_id.get((i));
            float t_y=(float)data.get(id);

            float t_pos_y=((t_y-draw_y_min)/(draw_y_max-draw_y_min))*data_len_y;
            t_pos_y=(height+window_displaceY)-t_pos_y;

            draw_data_pos_y.add(t_pos_y);
        }

        //绘图
        if(draw_data_pos_x.size()>0){
            path.moveTo((float)draw_data_pos_x.get(0),(float)draw_data_pos_y.get(0));
            for(int i=1;i<draw_data_pos_x.size();++i){//绘制点
                path.lineTo((float)draw_data_pos_x.get(i),(float)draw_data_pos_y.get(i));
            }

            canvas.drawPath(path,wave_paint);

//            if(!isTransData && has_finger_slider){//没在传递数据，且手指曾经滑动过，画下方拖动滑块
//                Path Rect_path = new Path();
//
//                Rect_path.moveTo(0+slider_rect_displace_x,height-slider_rect_height_x);
//                Rect_path.lineTo(slider_rect_width_x+slider_rect_displace_x,height-slider_rect_height_x);
//                Rect_path.lineTo(slider_rect_width_x+slider_rect_displace_x,height);
//                Rect_path.lineTo(0+slider_rect_displace_x,height);
//
//                canvas.drawPath(Rect_path,slider_rect_paint);
//            }

        }


    }

    /**
     * 手指滑动时更新窗口位移信息
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(!isTransData){//数据传完了再执行滑动操作

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:{
                    startX=event.getX();
                    startY=event.getY();
                    break;
                }

                case MotionEvent.ACTION_UP:{
                    break;
                }

                case MotionEvent.ACTION_MOVE:{
                    if(!isTransData && data_len_x>(window_displace_thresholdX) ){
                        x_change = event.getX()-startX;
                        y_change = event.getY()-startY;

                        if(has_finger_slider==false){
                            has_finger_slider=true;
                        }

                        boolean inSliderRegion=false;
                        //判断手指是否在滑块区域内
                        if(event.getX()>=slider_rect_displace_x && event.getX()<=slider_rect_displace_x+slider_rect_width_x){
                            if(event.getY()>=height-slider_rect_height_x && event.getY()<=height)
                            {
                                inSliderRegion=true;
                            }
                        }
                        if(inSliderRegion){//用滑块位移决定窗口位移
                            //没有缩放的话滑块大小不变
                            //判断滑动是否超出边界
                            if((x_change+slider_rect_displace_x+slider_rect_width_x)>width){
                                window_displaceX=data_len_x-width;
                                slider_rect_displace_x=width-slider_rect_width_x;
                            }else if((x_change+slider_rect_displace_x)<0){
                                window_displaceX=0;
                                slider_rect_displace_x=0;
                            }else{
                                slider_rect_displace_x+=(x_change)*slider_rect_speed_x;

                                window_displaceX+=((width/slider_rect_width_x)*x_change)*slider_rect_speed_x;
                            }

                        }else{//用窗口位移决定滑块位移

                            //判断滑动是否越界
                            if((-x_change+window_displaceX+width)>(data_len_x)){
                                window_displaceX=data_len_x-1*width;
                                slider_rect_displace_x=width-slider_rect_width_x;
                            }else if((-x_change+window_displaceX)<0){
                                window_displaceX=0;
                                slider_rect_displace_x=0;
                            }else{
                                window_displaceX+=(-x_change)*slider_speed_x;
                                slider_rect_displace_x+=((slider_rect_width_x/width)*(-x_change))*slider_speed_x;
                            }
                        }

                    }

                    postInvalidate();
                    break;
                }

            }
        }

        return true;
    }

    public  void transDataNotInvalidate(float data_input){//更新单个数据，不刷新
        if(isTransData==false){
            isTransData=true;
        }
        data_len_x+=gap_x;
        if(data_len_x>window_displace_thresholdX){//调整位移
            window_displaceX+=gap_x;
        }

        this.data.add(data_input);

    }

    //数据传递结束后需要调用finishTransData，之后画面可以滑动
    public void finishTransData(){
        this.isTransData=false;
        //设置滑块大小和位置
        slider_rect_height_x=60;
        if(data_len_x>(width)){
            slider_rect_width_x=(width/(data_len_x))*width;
            slider_rect_displace_x=(width/(data_len_x))*window_displaceX;
        }else{
            slider_rect_width_x=width;
            slider_rect_displace_x=0;
        }

        postInvalidate();
    }

    public void setSliderXSpeedRatio(float slider_speed_ratio){//调整手指在画面上左右滑动时画面的更新速度，大于1加速,小于1减速，数值要大于0
        slider_speed_x=slider_speed_x*slider_speed_ratio;
    }
    public void setSliderRectXSpeedRatio(float slider_rect_speed_ratio){//调整手指在下面滑块上左右滑动时画面的更新速度，大于1加速,小于1减速，数值要大于0
        slider_rect_speed_x=slider_rect_speed_x*slider_rect_speed_ratio;
    }
    public void setGridLineWidth(float gridLineWidth_input){//设置网格线宽度
        grid_paint.setStrokeWidth(gridLineWidth_input);
    }
    public void setWaveLineWidth(float waveLineWidth_input){//设置曲线宽度
        wave_paint.setStrokeWidth(waveLineWidth_input);
    }

    public void setGridColor(int color_id){//设置网格颜色
        grid_paint.setColor(color_id);
    }
    public void setWaveColor(int color_id){//设置曲线颜色
        wave_paint.setColor(color_id);
    }
    public void setSliderRectColor(int color_id){//设置下方滑块颜色
        slider_rect_paint.setColor(color_id);
        slider_rect_paint.setAlpha(slider_rect_alpha);
    }
    public void setSliderRectAlpha(int alpha){//设置下方滑块透明度
        slider_rect_alpha=alpha;
        slider_rect_paint.setAlpha(slider_rect_alpha);
    }

    public void reset(){//清空画布，回到绘图前的状态，但是之前设置的属性，如y轴范围等仍然保存
        data.clear();

        data_len_y=height;

        data_len_x=0;
        gap_x=width/(draw_num_x*1.0f);

        window_displaceX=-width;
        window_displaceY=0;

        slider_rect_displace_x=0;

        x_change = 0.0f ;
        y_change=0.0f;

        has_finger_slider=false;

        window_displace_thresholdX=window_displace_threshold_ratioX*width;

        postInvalidate();

    }
    public int getDataSize(){
        return data.size();
    }
    Vector getData(){
        return this.data;
    }
    boolean getTransDataFlag(){
        return this.isTransData;
    }

}
