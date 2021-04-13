package com.techyu.trackingroi.Utils;

import android.text.format.Time;

import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.ArrayList;
import java.util.Arrays;

import Jama.SingularValueDecomposition;
import biz.source_code.dsp.math.Complex;
import biz.source_code.dsp.transform.Dft;

public class Algorithm {

    //冒泡排序
    public static int[] BubbleSort(int[] input){
        int N=input.length;
        int temp;
        for(int i=N-1;i>0;i--) {
            for (int j = N - 2; j >= N - i - 1; j--) {
                if (input[j + 1] < input[j]) {
                    temp = input[j];
                    input[j] = input[j + 1];
                    input[j + 1] = temp;
                }
            }
        }

        return input;
    }

    //查找最大值
    public static float findMax(float[] array) {
        float max = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }
        return max;
    }

    public static int findMaxInt(int[] array) {
        int max = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }
        return max;
    }
	
    //返回最大值位置索引
    public static int findMaxLocation(double[] array) {
        int location = 0;
        double max = 0;
        for (int i = 0; i < array.length / 2; i++) {
            if (array[i] > max) {
                max = array[i];
                location = i;
            }
        }
        return location;
    }

    public static int findMaxLocationInt(int[] array) {
        int location = 0;
        int max = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                location = i;
            }
        }
        return location;
    }

    //查找最小值
    public static float findMin(float[] array) {
        float min = 999;
        for (int i = 0; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }
        return min;
    }

    public static int findMinInt(int[] array) {
        int min = 999;
        for (int i = 0; i < array.length; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }
        return min;
    }

    //计算均值 常用计算函数
    public static float calculateAvg(float[] array) {
        float sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        float avg = sum / array.length;
        return avg;
    }

    //计算均值 常用计算函数
    public static int calculateAvgInt(int[] array) {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        int avg = sum / array.length;
        return avg;
    }

    //计算信号标准差
    public static float calculateStd(float[] array) { //求序列标准差
        float avg = calculateAvg(array);
        float sum=0;
        for (int i = 0; i < array.length; i++) {
            sum += (array[i]-avg)*(array[i]-avg);
        }
        float std = (float)Math.sqrt(sum / array.length);
        return std;
    }

    // 基线消除
    public static float[] BaselineCancelation(float[] InputSig, int seg_len){
        float avg=calculateAvg(InputSig); //整体均值作为基准线，所有seg向该基准线对齐
        float[] seg=new float[seg_len]; //seg_len点作为一段
        float[] output=new float[InputSig.length];
        for(int i=0;i<InputSig.length/seg_len;i++){
            for(int j=seg_len*i;j<seg_len*(i+1);j++){
                seg[j-seg_len*i]=InputSig[j];
            }
            float avg_seg=calculateAvg(seg);
            float offset=avg_seg-avg;  //seg的基线漂移
            for(int k=seg_len*i;k<seg_len*(i+1);k++){
                output[k]=InputSig[k]-offset;
            }
        }

        return output;
    }

    //尖峰压缩
    public  static  float[] SharpCompress(float[] InputSig, int seg_len){
        float[] output=InputSig;
        float avg=Algorithm.calculateAvg(InputSig);
        float[] seg=new float[seg_len];
        float[] sd=new float[InputSig.length/seg_len];//sd是干嘛的？？
        float threshold=(float)2*Algorithm.calculateStd(InputSig);//以整个序列两倍的标准差作为门限
        for(int i=0;i<InputSig.length/seg_len;i++){//这里是进行几段压缩
            for(int j=seg_len*i;j<seg_len*(i+1);j++){//这里是定位每段的不同起始点
                seg[j-seg_len*i]=InputSig[j];
            }
            sd[i]=Algorithm.calculateStd(seg);//计算当前第i组的标准差
            if(sd[i]>threshold){
                float compress_rate=(float)(0.5+0.4*((sd[i]-threshold)/sd[i])); //压缩率，对尖峰进行压缩
                System.out.println("检测到尖峰，标准差为："+ Float.toString(sd[i])+"，压缩率为："+Float.toString(compress_rate));
                for(int k=seg_len*i;k<seg_len*(i+1);k++){//这里才是进行压缩后的新数组形成
                    output[k] = InputSig[k] - (InputSig[k] - avg) * compress_rate;
                }
            }
        }
        return output;
    }

    // 3点滑动平均滤波器
    public static float[] MovingAverageFilter_3P(float[] InputSig){
        int length = InputSig.length;
        float[] dbRt = new float[length];

        /** 三点平滑 */
        dbRt[0] = InputSig[0];
        for (int x = 1; x < length - 1; x++) {
            dbRt[x] = (InputSig[x - 1] + InputSig[x] + InputSig[x + 1]) / 3;
        }
        dbRt[length - 1] = InputSig[length - 1];

        return dbRt;
    }

    //DFT变换，求频谱，不做频率过滤
    public static double[] CalcPurePulseSpectrum(float[] array, float fps) {
        double[] converted=new double[4*array.length];
        for (int i = 0; i < array.length; i++) {
            converted[i] = array[i];
        }
        for (int i = array.length; i < 4*array.length; i++) {
            converted[i] = 0;
        }
        /*The Goertzel Algorithm is a DFT in disguise, with some numerical tricks to eliminate complex number arithmetic, roughly doubling the efficiency.
         Goertzel Algorithm has received a lot of attention recently for mobile telephone applications, */
        double[] output = new double[converted.length];
        Complex[] spec= Dft.goertzel(converted);  //戈泽尔算法求DFT
        for(int i=0;i<spec.length;i++){
            output[i]= spec[i].abs();
        }
        return output;
    }

    //DFT变换，求频谱，从而得到心率
    public static double[] CalcPulseSpectrum(float[] array, float fps) {
        double[] converted=new double[4*array.length];
        for (int i = 0; i < array.length; i++) {
            converted[i] = array[i];
        }
        for (int i = array.length; i < 4*array.length; i++) {
            converted[i] = 0;
        }
        /*The Goertzel Algorithm is a DFT in disguise, with some numerical tricks to eliminate complex number arithmetic, roughly doubling the efficiency.
         Goertzel Algorithm has received a lot of attention recently for mobile telephone applications, */
        double[] output = new double[converted.length];
        Complex[] spec= Dft.goertzel(converted);  //戈泽尔算法求DFT
        for(int i=0;i<spec.length;i++){
            output[i]= spec[i].abs();
        }
        for (int i = 0; i < output.length; i++) { //频域滤波
            float freq = fps * i / output.length;
            if (freq < 0.8 || freq > 4.0) {
                output[i] = 0;
            }
        }
        return output;
    }

    //理想带通滤波器
    public static float[] IdealPassing(float[] input_data, float RTfps, float lowpass, float highpass){
        int dataLen = input_data.length;
        double[] signal = new double[dataLen];
        for(int i = 0; i < dataLen; i++){
            signal[i] = input_data[i];
        }
        double frequencyResolution = RTfps / dataLen;
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        org.apache.commons.math3.complex.Complex[] complexArr = fft.transform(signal, TransformType.FORWARD);//正向傅里叶变换
        //进行滤波,注意条件
        if(highpass < RTfps / 2.0 && lowpass >= 0.0){
            for(int i = 0; i < complexArr.length; i++){
                if(i * frequencyResolution > highpass){
                    complexArr[i] = new org.apache.commons.math3.complex.Complex(0, 0);
                }
                if(i * frequencyResolution < lowpass){
                    complexArr[i] = new org.apache.commons.math3.complex.Complex(0, 0);
                }
            }
        }
        //反傅里叶变换
        org.apache.commons.math3.complex.Complex[] timeDomainArr = fft.transform(complexArr, TransformType.INVERSE);
        float[] output = new float[dataLen];
        for(int i = 0; i < timeDomainArr.length; i++){
            output[i] = (float) timeDomainArr[i].getReal();
        }

        return output;
    }

    //RGB转换CMYk: 青色Cyan、洋红色Magenta、黄色Yellow, 而K取的是黑色Black最后一个字母
    public static float[] RGB2CMYK(float [] rgb){
        float[] cmyk = new float[4];
        cmyk[3]=(float)(Math.min(Math.min(255-rgb[0],255-rgb[1]),255-rgb[2])/2.55);
        float MyR = (int)(rgb[0]/2.55);
        float Div = 100-cmyk[3];
        if (Div == 0)Div = 1;
        cmyk[0] = ((100-MyR-cmyk[3])/Div)*100;
        float MyG = (float)(rgb[1]/2.55);
        cmyk[1] = ((100-MyG-cmyk[3])/Div)*100;
        float MyB = (float)(rgb[2]/2.55);
        cmyk[2] = ((100-MyB-cmyk[3])/Div)*100;

        return cmyk;
    }

    //RGB颜色空间转YCrCb
    public static float[] RGB2YCrCb(float [] rgb){
        float tRet[] = new float[3];
        tRet[0]=(float)(0.257*rgb[0] + 0.504*rgb[1] + 0.098*rgb[2] + 16);
        tRet[1]=(float)(0.439*rgb[0] - 0.368*rgb[1] - 0.071*rgb[2] + 128);
        tRet[2]=(float)(-0.148*rgb[0] - 0.291*rgb[1] + 0.439*rgb[2] + 128);
        return tRet;
    }

    //RGB颜色空间转YUV
    public static float[] RGB2YUV(float [] rgb){
        float tRet[] = new float[3];
        tRet[0] = (float)(0.30*rgb[0]+0.59*rgb[1]+0.11*rgb[2]);
        tRet[1] = (float)(-0.15*rgb[0]-0.29*rgb[1]+0.44*rgb[2]);
        tRet[2] = (float)(0.62*rgb[0]-0.52*rgb[1]-0.10*rgb[2]);
        return tRet;
    }

    //RGB颜色空间转XYZ(CIE1931标准色度观察者)
    public static float[] RGB2XYZ(float[] sRGB) {
        float[] XYZ = new float[3];
        float sR, sG, sB;sR = sRGB[0];
        sG = sRGB[1];
        sB = sRGB[2];
        sR /= 255;
        sG /= 255;
        sB /= 255;
        if (sR <= 0.04045) {
            sR = (float)(sR / 12.92);
        } else {
            sR = (float)Math.pow(((sR + 0.055) / 1.055), 2.4);
        }
        if (sG <= 0.04045) {
            sG = (float)(sG / 12.92);
        } else {
            sG = (float)Math.pow(((sG + 0.055) / 1.055), 2.4);
        }
        if (sB <= 0.04045) {
            sB = (float)(sB / 12.92);
        } else {
            sB = (float) Math.pow(((sB + 0.055) / 1.055), 2.4);
        }
        XYZ[0] = (float)(41.24 * sR + 35.76 * sG + 18.05 * sB);
        XYZ[1] = (float)(21.26 * sR + 71.52 * sG + 7.2 * sB);
        XYZ[2] = (float)(1.93 * sR + 11.92 * sG + 95.05 * sB);

        return XYZ;
    }

    //XYZ转LAB, Lab颜色空间中的L分量用于表示像素的亮度，取值范围是[0,100],表示从纯黑到纯白；a表示从红色到绿色的范围，取值范围是[127,-128]；b表示从黄色到蓝色的范围，取值范围是[127,-128]
    public static float[] XYZ2Lab(float[] XYZ) {
        float[] Lab = new float[3];
        float X, Y, Z;
        X = XYZ[0];
        Y = XYZ[1];
        Z = XYZ[2];
        float Xn, Yn, Zn;
        Xn = (float)95.04;
        Yn = (float)100;
        Zn = (float)108.89;
        float XXn, YYn, ZZn;
        XXn = X / Xn;
        YYn = Y / Yn;
        ZZn = Z / Zn;
        float fx, fy, fz;
        if (XXn > 0.008856) {
            fx = (float)Math.pow(XXn, 0.333333);
        } else {
            fx = (float)(7.787 * XXn + 0.137931);
        }
        if (YYn > 0.008856) {
            fy = (float)Math.pow(YYn, 0.333333);
        } else {
            fy = (float)(7.787 * YYn + 0.137931);
        }
        if (ZZn > 0.008856) {
            fz = (float)Math.pow(ZZn, 0.333333);
        } else {
            fz = (float)(7.787 * ZZn + 0.137931);
        }
        Lab[0] = 116 * fy - 16;
        Lab[1] = 500 * (fx - fy);
        Lab[2] = 200 * (fy - fz);

        return Lab;
    }

    /*
     * RGB转HSV
     * 输入范围R,G,B, 0~255
     * 输出范围H:0~360,S:0~1,V:0~1
     */
    public static float[] RGB2HSV(float [] rgb){
        float tMax, tMin;
        float H=0,S=0,V=0;
        float delta;
        float tRet[] = new float[3];
        tMax= Math.max(rgb[0], Math.max(rgb[1],rgb[2]));
        tMin= Math.min(rgb[0], Math.min(rgb[1],rgb[2]));
        V = tMax;
        delta = tMax - tMin;

        if(tMax != 0){
            S= delta / tMax;
        }
        else{
            S=0;
            H=-1;
            tRet[0] = H;
            tRet[1] = S;
            tRet[2] = V;
            return tRet;
        }

        if(rgb[0] == tMax){
            H =  (rgb[1] - rgb[2]) / delta;     // between yellow & magenta
        }else if(rgb[1] == tMax ){
            H = 2 + (rgb[2] - rgb[0])  / delta; // between cyan & yellow
        }else
            H = 4 + (rgb[0] - rgb[1])  / delta; // between magenta & cyan

        H *= 60;
        if(H < 0 ){
            H += 360;
        }

        tRet[0] = H;
        tRet[1] = S;
        tRet[2] = V;
        return tRet;
    }

    //基于RGB颜色空间的皮肤检测
    public static boolean skinDetectionByRGB(float[] rgb){
        float tr=rgb[0];
        float tg=rgb[1];
        float tb=rgb[2];
        float max = Math.max(tr, Math.max(tg, tb));
        float min = Math.min(tr, Math.min(tg, tb));
        float rg = Math.abs(tr - tg);
        if(tr > 95 && tg > 40 && tb > 20 && rg > 15 &&
                (max - min) > 15 && tr > tg && tr > tb) {
            return true;
        } else {
            return false;
        }
    }

    //基于HSV颜色空间的皮肤检测
    public static boolean skinDetectionByHSV(float[] hsv){
        if((hsv[0] > 0.0f && hsv[0] < 50.0f ) && (hsv[1] > 0.23f && hsv[1] < 0.68f)){
            return true;
        } else {
            return false;
        }
    }

    //基于YCrCb颜色空间的皮肤检测
    public static boolean skinDetectionByYCrCb(float[] YCrCb){
        if(YCrCb[1]>133 && YCrCb[1]<173 && YCrCb[2]>77 && YCrCb[2]<127){
            return true;
        }else{
            return false;
        }
    }

    //计算信号与高频噪声的比值，本信噪比算法不考虑低频噪声
    public static double CalculateSNR(float[] input, float fr){
        float[] output=new float[input.length];

        //零均值归一化
        for(int i=0;i<input.length;i++){
            output[i]=input[i]/255;
        }
        float average = Algorithm.calculateAvg(output);
        float std=Algorithm.calculateStd(output);
        for(int i=0;i<output.length;i++){
            output[i]=(output[i]-average)/std;
        }

        //计算频谱
        double[] spec = Algorithm.CalcPurePulseSpectrum(output, fr);

        //根据频谱特性计算信噪比
        double N=0;
        double sig=0;
        for(int j=0;j<spec.length/2;j++){
            float freq = fr * j / spec.length;
            if(freq>0.8 && freq<4.0) { //脉搏信号功率(全心率频段)。
                sig += spec[j];
            }
            if(freq>=4.0){
                N += spec[j]; //仅统计高频噪声，光照不稳定或光照不足可能出现强烈的高频随机噪声（低频噪声多由运动干扰引起，此处不统计）
            }
        }
        double SNR;
        if(N==0){
            SNR = 30;
        }else{
            SNR = sig/N;
        }
        return SNR;
    }

    //奇异谱分析算法，用于信号降噪
    public static float[] SSA(float[] input_data, int window_size, int eigen_num){
        //构建轨迹矩阵
        int N=input_data.length;
        if(window_size>N/2){
            window_size = N-window_size;
        }
        int K=N-window_size+1;
        Jama.Matrix X=new Jama.Matrix(window_size, K);
        for(int i=0;i<K;i++){
            for(int j=0;j<window_size;j++){
                X.set(j,i,(double)input_data[j+i]);
            }
        }

        //奇异值分解
        Jama.Matrix S = X.times(X.transpose());
        SingularValueDecomposition svd=S.svd();
        Jama.Matrix U = svd.getU();
        Jama.Matrix V = (X.transpose()).times(U);
        System.out.println("U.m="+Integer.toString(U.getRowDimension())+" ,U.n="+Integer.toString(U.getColumnDimension())); //特征向量维度打印

        //以指定的特征值数量计算重构矩阵rca
        Jama.Matrix temp_U=new Jama.Matrix(U.getRowDimension(), eigen_num);
        for(int i=0;i<U.getRowDimension();i++){
            for(int j=0;j<eigen_num;j++){
                temp_U.set(i,j,U.get(i,j));
            }
        }
        Jama.Matrix Vt=V.transpose();
        Jama.Matrix temp_V=new Jama.Matrix(eigen_num, Vt.getColumnDimension());
        for(int i=0;i<eigen_num;i++){
            for(int j=0;j<Vt.getColumnDimension();j++){
                temp_V.set(i,j,Vt.get(i,j));
            }
        }
        Jama.Matrix rca=temp_U.times(temp_V);
        System.out.println("rca.m="+Integer.toString(rca.getRowDimension())+" ,rca.n="+Integer.toString(rca.getColumnDimension())); //重构矩阵维度打印

        //利用重构矩阵rca重构信号
        Jama.Matrix y=new Jama.Matrix(N,1);
        y.set(0,0,input_data[0]);
        int Lp=Math.min(window_size,K);
        int Kp=Math.max(window_size,K);
        //1. 重构 1~Lp-1
        for(int k=-1;k<Lp-2;k++){
            for(int m=0;m<k+1;m++){
                y.set(k+1,0,y.get(k+1,0)+rca.get(m,k-m+2)/(k+1));
            }
        }
        //2. 重构 Lp~Kp
        for(int k=Lp-2;k<Kp-1;k++){
            for(int m=0;m<Lp;m++){
                y.set(k+1,0,y.get(k+1,0)+rca.get(m,k-m+1)/Lp);
            }
        }
        //3. 重构 Kp+1~N
        for(int k=Kp-1;k<N-1;k++){
            for(int m=k-Kp+1;m<N-Kp+1;m++){
                y.set(k+1,0,y.get(k+1,0)+rca.get(m,k-m)/(N-k));
            }
        }

        //输出经奇异谱分析去燥后的信号
        float[] output=new float[y.getRowDimension()];
        for(int i=0;i<y.getRowDimension();i++){
            output[i] = (float)y.get(i,0);
        }

        return output;
    }

    //正弦波拟合，用于计算心率
    public static float[] CurveFittingForPulse(float[] input, float fps){
        ArrayList resultList=new ArrayList();
        float[] seg=new float[32];
        double[] y=new double[32];
        double scale=3;
        double best_freq=0;
        double best_phase=0;
        for(int p=0;p<input.length/32;p++) {
            //数据分段，每32点拟合一次
            for (int q = 32 * p; q < 32 * (p + 1); q++) {
                seg[q - 32 * p] = input[q];
            }
            //分段拟合，实现幅度、相位、频率对齐
            double max=0;
            //double scale=findMax(seg);
            for (int k = 0; k < 45; k++) {
                double phase = (Math.PI) * k / 22.5;  //相位分辨率8度
                for (int j = 0; j < 44; j++) {
                    double freq = 0.8 + 0.05 * j; //0.8~3Hz带宽等间隔切成44份，频率分辨率0.05Hz
                    for (int i = 0; i < seg.length; i++) {
                        double t = (double) i / fps;
                        y[i] = scale * Math.sin(2 * (Math.PI) * freq * t + phase);
                    }
                    double sum = 0;
                    for (int m = 0; m < seg.length; m++) {
                        sum = sum + seg[m] * y[m];
                    }
                    if (sum > max) {
                        max = sum;
                        best_freq = freq;
                        best_phase = phase;
                    }
                }
            }
            //找到最佳匹配后进行存储
            for (int r = 0; r < seg.length; r++) {
                double t = (double) r / fps;
                y[r] =scale*Math.sin(2 * (Math.PI) *best_freq* t+best_phase);
                resultList.add(y[r]);
            }
        }
        float[] output =new float[resultList.size()];
        for(int i=0;i<output.length;i++){
            output[i]=Float.parseFloat(resultList.get(i).toString());
        }
        System.out.println("拟合曲线："+Arrays.toString(output));
        return output;
    }

    /*根据多个数据点拟合最佳直线方程，返回值为直线的斜率和截距，用法示例：
    Double [][] arrPoints= {{1.0,2.0,3.0,5.0},{1.0,2.0,3.0,5.0}};
    System.out.println("w = "+LinearRegression(arrPoints)[0]);
    System.out.println("b = "+LinearRegression(arrPoints)[1]);*/
    public static double [] LinearRegression(double [][] points ) {
        double dbRt [] =new double [2];
        double dbXSum=0;
        for(int i=0;i<points[0].length;i++) {
            dbXSum=dbXSum+points[0][i];
        }
        double dbXAvg=dbXSum/points[0].length;
        double dbWHeadVal=0;
        for(int i=0;i<points[0].length;i++) {
            dbWHeadVal=dbWHeadVal+(points[0][i]-dbXAvg)*points[1][i];
        }
        double dbWDown=0;
        double dbWDownP=0;
        dbXSum=0;
        for(int i=0;i<points[0].length;i++) {
            dbWDownP=dbWDownP+points[0][i]*points[0][i];
            dbXSum=dbXSum+points[0][i];
        }
        dbWDown=dbWDownP-(dbXSum*dbXSum/points[0].length);
        double dbW=dbWHeadVal/dbWDown;
        dbRt[0]=dbW;
        double dbBSum=0;
        for(int i=0;i<points[0].length;i++) {
            dbBSum=dbBSum+(points[1][i]-dbW*points[0][i]);
        }
        double dbB=dbBSum/points[0].length;
        dbRt[1]=dbB;
        return dbRt;
    }

    /**
     * 判断当前系统时间是否在特定时间的段内
     *
     * @param beginHour 开始的小时，例如5
     * @param beginMin 开始小时的分钟数，例如00
     * @param endHour 结束小时，例如 8
     * @param endMin 结束小时的分钟数，例如00
     * @return true表示在范围内，否则false
     */
    public static boolean isCurrentInTimeScope(int beginHour, int beginMin, int endHour, int endMin) {
        boolean result = false;// 结果
        final long aDayInMillis = 1000 * 60 * 60 * 24;// 一天的全部毫秒数
        final long currentTimeMillis = System.currentTimeMillis();// 当前时间

        Time now = new Time();// 注意这里导入的时候选择android.text.format.Time类,而不是java.sql.Time类
        now.set(currentTimeMillis);

        Time startTime = new Time();
        startTime.set(currentTimeMillis);
        startTime.hour = beginHour;
        startTime.minute = beginMin;

        Time endTime = new Time();
        endTime.set(currentTimeMillis);
        endTime.hour = endHour;
        endTime.minute = endMin;

        if (!startTime.before(endTime)) {
            // 跨天的特殊情况（比如22:00-8:00）
            startTime.set(startTime.toMillis(true) - aDayInMillis);
            result = !now.before(startTime) && !now.after(endTime); // startTime <= now <= endTime
            Time startTimeInThisDay = new Time();
            startTimeInThisDay.set(startTime.toMillis(true) + aDayInMillis);
            if (!now.before(startTimeInThisDay)) {
                result = true;
            }
        } else {
            // 普通情况(比如 8:00 - 14:00)
            result = !now.before(startTime) && !now.after(endTime); // startTime <= now <= endTime
        }
        return result;
    }

    //计算射血时间（Ejection time），用于血压估计
    public static int getET(float[] input_data, float fr){
        int N = input_data.length;
        ArrayList ValleyList = new ArrayList();
        int sum=0;
        int count=0;
        for(int i=1;i<N-1;i++){
            if(input_data[i-1]>input_data[i] && input_data[i]<input_data[i+1]){
                ValleyList.add(i);
                int len=ValleyList.size();
                if(len>1){
                    int last_loc=Integer.parseInt(ValleyList.get(len-2).toString());
                    int current_loc=Integer.parseInt(ValleyList.get(len-1).toString());
                    //限定ET范围
                    int th1=(int)(60*fr/96);
                    int th2=(int)(60*fr/360);
                    if(current_loc-last_loc<=th2){
                        ValleyList.remove(ValleyList.size()-1);
                    } else{
                        float max=-999;
                        int max_location=0;
                        for(int k=last_loc; k<current_loc;k++) {
                            if (input_data[k] > max) {
                                max = input_data[k];
                                max_location=k-last_loc;
                            }
                        }
                        if(max_location>0) {
                            sum = sum + max_location;
                            count++;
                        }
                    }
                }
            }
        }
        if(count==0){
            return -1;
        }
        int ET_len=sum/count;
        int ET=(int)(1000*ET_len/fr);
        return ET;
    }

    public static float getK(float[] input_data, float fr){
        int N = input_data.length;
        float[] reverse = new float[N];
        for(int i=0;i<N;i++){
            reverse[i] = -input_data[i];
        }
        float k1=get_blood_value(input_data,fr);
        float k2=get_blood_value(reverse,fr);
        if(k1==-1 && k2!=-1){
            return k2;
        }
        if(k1!=-1 && k2==-1){
            return k1;
        }
        if(k1==-1 && k2==-1){
            return -1;
        }
        if(k1<k2){
            return k1;
        }else{
            return k2;
        }
    }

    //优化后的K值计算方法，采用所有单周期波形的K值平均值而不是最小值作为最终计算结果
    public static float get_blood_value(float[] input_data, float fr){
        float[] output = input_data;
        int N=output.length;
        float min_=Algorithm.findMin(output);
        float max_=Algorithm.findMax(output);

        //归一化
        for(int i=0;i<N;i++){
            output[i]= (output[i]-min_)/(max_-min_);
        }
        ArrayList PeakList = new ArrayList();
        float K_sum=0;
        float K_num=0;
        for(int i=1;i<N-1;i++){
            if(output[i-1]<output[i] && output[i]>output[i+1]){
                PeakList.add(i);
                double th=(float)0.9*Algorithm.calculateStd(output); //幅度阈值
                int len=PeakList.size();
                if(len>1){
                    int last_loc=Integer.parseInt(PeakList.get(len-2).toString());
                    int current_loc=Integer.parseInt(PeakList.get(len-1).toString());
                    //限定心率在48~180之间
                    int th1=(int)(60*fr/48);
                    int th2=(int)(60*fr/180);
                    if(current_loc-last_loc<=th2 || output[i]<=th){
                        PeakList.remove(PeakList.size()-1);
                    }else if(current_loc-last_loc>th1){

                    } else{
                        float sum=0;
                        float max=0;
                        float min=1;
                        for(int k=last_loc; k<current_loc;k++){
                            if (output[k] > max) {
                                max = output[k];
                            }
                            if (output[k] < min) {
                                min = output[k];
                            }
                            sum+=output[k];
                        }
                        float avg=sum/(current_loc-last_loc);
                        float temp_k =(avg-min)/(max-min);
                        K_sum+=temp_k;
                        K_num++;
                    }
                }
            }
        }
        if(K_num==0){
            return -1;
        }
        return K_sum/K_num;
    }

    //实时峰值检测算法
    public static boolean IsPeak(float[] input_data, int index, int len){
        if(index<len){
            return false;
        }
        for(int i=index-len;i<index;i++){
            if(input_data[i]>input_data[index]){
                return false;
            }
        }
        for(int i=index+1;i<=index+len;i++){
            if(input_data[i]>input_data[index]){
                return false;
            }
        }
        return true;
    }

    //从峰值位置序列中获取RR间期序列
    public static int[] getRrArrayFromPeakList(int[] peak_list, float fs){
        int N=peak_list.length;
        int peak_detect_range=10;
        if(fs>25){
            peak_detect_range=10;
        }else if(fs>20){
            peak_detect_range=8;
        }else{
            peak_detect_range=6;
        }
        ArrayList arrayList=new ArrayList();
        for(int i=1;i<N;i++){
            int rr_len=peak_list[i]-peak_list[i-1];
            if(rr_len>peak_detect_range && rr_len<4*peak_detect_range){ //限定心率在45到180之间
                int T = (int)(1000*rr_len/fs); //单位：毫秒
                arrayList.add(T);
            }
        }
        int[] RrArray=new int[arrayList.size()];
        for(int i=0;i<arrayList.size();i++){
            RrArray[i]=(int)arrayList.get(i);
        }

        return RrArray;
    }

}
