package com.example.mediacodecec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Enid on 2017/5/2.
 */

public class AvcEncoder {

    private  MediaCodec mMediaCodec;
    private int mWidth;
    private int mHeight;
    private int mFramerate;

    private static String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.h264";
    private BufferedOutputStream bufferedOutputStream;

    private boolean isRuning = false;

    private int TIMEOUT_USEC = 12000;

    public byte[] configbyte;


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public AvcEncoder(int width, int height, int framerate, int bitrate){
        mWidth = width;
        mHeight = height;
        mFramerate = framerate;

        //1.初始化硬件编码器，并配置编码格式，视频文件的长宽，编码率,帧率等
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",width,height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,width * height * 5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        mMediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

        //2.开启编码器
        mMediaCodec.start();

        //
        createFile();
    }

    private void createFile() {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }

        try {
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void stopEncoder() {
        mMediaCodec.stop();
        mMediaCodec.release();
    }

    public void stopEncoderThread() {
        isRuning = false;
        try {
            stopEncoder();
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startEncoderThread() {
        Thread encoderThread = new Thread(new Runnable() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                isRuning = true;
                byte[] input = null;
                long pts;
                long generateIndex = 0;
                while (true) {
                    if (MainActivity.YUVQueue.size() > 0) {
                        input = MainActivity.YUVQueue.poll();
                        byte[] yuv420sp = new byte[mWidth * mHeight * 3 / 2];
                        NV21ToNV12(input,yuv420sp,mWidth,mHeight);
                        input = yuv420sp;
                    }
                    if (input != null) {
                        //3.编码
                        try {
                            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                pts = computePresentation(generateIndex);
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();
                                inputBuffer.put(input);
                                mMediaCodec.queueInputBuffer(inputBufferIndex,0,input.length,pts,0);
                                generateIndex += 1;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo,TIMEOUT_USEC);
                            while (outputBufferIndex >= 0) {
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                                    configbyte = new byte[bufferInfo.size];
                                } else if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                                    bufferedOutputStream.write(keyframe,0,keyframe.length);
                                } else {
                                    bufferedOutputStream.write(outData,0,outData.length);
                                }
                                mMediaCodec.releaseOutputBuffer(outputBufferIndex,false);
                                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo,TIMEOUT_USEC);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        });
        encoderThread.start();
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if(nv21 == null || nv12 == null)return;
        int framesize = width*height;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for(i = 0; i < framesize; i++){
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j-1] = nv21[j+framesize];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j] = nv21[j+framesize-1];
        }
    }

    /**
     *
     * @param frameIndex
     * @return
     */
    private long computePresentation(long frameIndex) {
        return 132 + frameIndex * 1000000 / mFramerate;
    }
}
