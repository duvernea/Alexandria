package it.jaschke.alexandria.CameraPreview;

/*
 * Barebones implementation of displaying camera preview.
 *
 * Created by lisah0 on 2012-02-24
 */

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = CameraPreview.class.getSimpleName();
    private Context mContext;

    private SurfaceHolder mHolder;
    public Camera mCamera;
    private Camera.PreviewCallback previewCallback;
    private Camera.AutoFocusCallback autoFocusCallback;
    private int mDefaultDisplayRotation;

    public CameraPreview(Context context, Camera camera,
                         Camera.PreviewCallback previewCb,
                         Camera.AutoFocusCallback autoFocusCb) {
        super(context);
        mCamera = camera;
        mContext = context;
        previewCallback = previewCb;
        autoFocusCallback = autoFocusCb;

        /*
         * Set camera to continuous focus if supported, otherwise use
         * software auto-focus. Only works for API leveAUD_SAMP_PRIMARY_MIC_BRD_LEVEL_AMPL_LPAl >=9.
         */

        Log.d(TAG, "Camera Preview constructor running");

        Camera.Parameters parameters = camera.getParameters();
        for (String f : parameters.getSupportedFocusModes()) {
            if (f.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                mCamera.setParameters(parameters);
                autoFocusCallback = null;
                break;
            }
        }
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    public void surfaceCreated(SurfaceHolder holder) {

        // get default Camera parameters
        Camera.Parameters param = mCamera.getParameters();

        // set format to NV21
        param.setPictureFormat(ImageFormat.NV21);
        param.setPreviewFormat(ImageFormat.NV21);

        // check the default display orientation and rotate if necessary
        Display display = ((WindowManager) mContext.getSystemService(mContext.WINDOW_SERVICE)).getDefaultDisplay();
        mDefaultDisplayRotation = display.getRotation();

        try {

            mCamera.setPreviewCallback(previewCallback);
            mCamera.setParameters(param);

            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        }
        catch (Exception e){
            System.err.println(e);
            return;
        }
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        /*
         * If your preview can change or rotate, take care of those events here.
         * Make sure to stop the preview before resizing or reformatting it.
         */
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }
        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }
        try {
            // make any changes
            setReaderSize();
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(previewCallback);
            // start preview again
            mCamera.startPreview();
        } catch (Exception e){
            Log.d("DBG", "Error starting camera preview: " + e.getMessage());
        }
    }
    public void surfaceDestroyed(SurfaceHolder holder) {
        // stop preview and release camera
//        if (mCamera != null) {
//            mCamera.stopPreview();
//            mCamera.setPreviewCallback(null);
//            mCamera.release();
//            mCamera = null;
//        }
    }
    public void hide() {
        mHolder.setFixedSize(0,0);
    }

    public void show() {
        if (mHolder.getSurface() == null) {
            return;
        }
        setReaderSize();
    }
    public void start() {
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }
        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }
        try {
            // make any changes
            setReaderSize();
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(previewCallback);
            // start preview again
            mCamera.startPreview();
        } catch (Exception e){
            Log.d("DBG", "Error starting camera preview: " + e.getMessage());
        }
    }


    public void setReaderSize() {
        // for fragment instead of activity
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;

        Camera.Parameters param = mCamera.getParameters();
        List<Camera.Size> previewSizes = param.getSupportedPreviewSizes();
        float desiredAspectRatio = (float) 1.5;
        int preferredIndex = -1;
        for (int i=0; i<previewSizes.size(); i++) {
            float aspectRatio = (float) previewSizes.get(i).width/previewSizes.get(i).height;
            float aspectRatioDelta = Math.abs(aspectRatio-desiredAspectRatio);
            if (aspectRatioDelta < .15) {
                preferredIndex = i;
            }
        }
        if (preferredIndex == -1) {
            // use a medium size
            preferredIndex = previewSizes.size()/2;
        }
        int width=10;
        int height=10;
        width = previewSizes.get(preferredIndex).width;
        height = previewSizes.get(preferredIndex).height;
        float aspectRatio = (float) height/width;

        Log.d(TAG, "Preview Width: " + width);
        Log.d(TAG, "Preview height: " + height);
        Log.d(TAG, "Aspect Ratio: " + aspectRatio);

        param.setPreviewSize(width, height);
        mCamera.setParameters(param);
        if (mDefaultDisplayRotation == Surface.ROTATION_0) {
            mCamera.setDisplayOrientation(90);
        } else if (mDefaultDisplayRotation == Surface.ROTATION_90) {
            mCamera.setDisplayOrientation(0);
        } else if (mDefaultDisplayRotation == Surface.ROTATION_270) {
            mCamera.setDisplayOrientation(180);
        } else if (mDefaultDisplayRotation == Surface.ROTATION_180) {
            mCamera.setDisplayOrientation((270));
        }
        int padding=20;
        int readerWidth = screenWidth-2*padding;
        int readerHeight = Math.round(readerWidth * aspectRatio);
        Log.d(TAG, "Surface Width: " + readerWidth);
        Log.d(TAG, "Surface Height: " + readerHeight);
        mHolder.setFixedSize(readerWidth,readerHeight );
        mCamera.setParameters(param);
    }

    public void setCamera(Camera camera) {
        if (mCamera == camera) { return; }

        stopPreviewAndFreeCamera();

        mCamera = camera;

        if (mCamera != null) {
            List<Camera.Size> localSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();

            try {
                mCamera.setPreviewDisplay(mHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Important: Call startPreview() to start updating the preview
            // surface. Preview must be started before you can take a picture.
            mCamera.startPreview();
        }
    }
    private void stopPreviewAndFreeCamera() {

        if (mCamera != null) {
            // Call stopPreview() to stop updating the preview surface.
            mCamera.stopPreview();

            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            mCamera.release();

            mCamera = null;
        }
    }
}
