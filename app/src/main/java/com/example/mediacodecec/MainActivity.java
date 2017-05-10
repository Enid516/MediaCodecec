package com.example.mediacodecec;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final int REQUEST_CODE = 1;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private int mWidth = 1280;
    private int mHeight = 720;
    private int mFrameRate = 30;
    private int mBiteRate = 10000000;
    private static int yuvQueueSize = 10;
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<>(yuvQueueSize);
    private AvcEncoder mAvcEncoder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        supportAvcCodec();
    }

    //implement PreviewCallBack

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        putYUVData(data,data.length);
    }

    //implement SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                requestCameraPermission();
            else
                start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mAvcEncoder.stopEncoderThread();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CODE)return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            start();
        }
    }

    private boolean supportAvcCodec() {
        if (Build.VERSION.SDK_INT >= 18) {
            for (int i = MediaCodecList.getCodecCount() - 1; i >= 0; i--) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    if (types[j].equalsIgnoreCase("video/avc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CODE);
    }

    private Camera getBackCamera() {
        Camera c = null;
        c = Camera.open(0);
        return c;
    }

    private void start() {
        mCamera = getBackCamera();
        startCamera(mCamera);
        mAvcEncoder = new AvcEncoder(mWidth, mHeight, mFrameRate, mBiteRate);
        mAvcEncoder.startEncoderThread();
    }

    private void startCamera(Camera camera) {
        if (null != camera) {
            try {
                camera.setPreviewCallback(this);
                camera.setDisplayOrientation(90);
                if (null == mParameters) {
                    mParameters = camera.getParameters();
                }
                mParameters.setPreviewFormat(ImageFormat.NV21);
                mParameters.setPreviewSize(mWidth, mHeight);
                camera.setParameters(mParameters);
                camera.setPreviewDisplay(mSurfaceHolder);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void putYUVData(byte[] buffer, int length) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }
}
