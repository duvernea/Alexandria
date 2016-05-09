package com.example.duvernea.cameratest;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import it.jaschke.alexandria.R;
import me.dm7.barcodescanner.core.DisplayUtils;
import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;


public class MainActivity_temp extends ActionBarActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final String TAG = MainActivity_temp.class.getSimpleName();

    public static final int MEDIA_TYPE_IMAGE = 1;

    SurfaceView surfaceView;
    SurfaceHolder mSurfaceHolder;
    ImageScanner mScanner;
    private List<BarcodeFormat> mFormats;
    Camera mCamera;

    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        // surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Barcode formats read by scanner : need both EAN13 and ISBN13
        mFormats = new ArrayList<BarcodeFormat>();
        mFormats.add(BarcodeFormat.EAN13);
        mFormats.add(BarcodeFormat.ISBN13);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        //Log.d(TAG, "onPreviewFrame running...");
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        int width = size.width;
        int height = size.height;
        //Log.d(TAG, "Preview height: " + height);
        //Log.d(TAG, "Preview width: " + width);
        Camera.Size size2 = parameters.getPictureSize();
        int width2 = size2.width;
        int height2 = size2.height;
        //Log.d(TAG, "Picture height: " + height);
        //Log.d(TAG, "Picture width: " + width);
//        if(DisplayUtils.getScreenOrientation(mContext) == Configuration.ORIENTATION_PORTRAIT) {
//            Log.d(TAG, "ROTATING DATA");
//            byte[] rotatedData = new byte[data.length];
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++)
//                    rotatedData[x * height + height - y - 1] = data[x + y * width];
//            }
//            int tmp = width;
//            width = height;
//            height = tmp;
//            data = rotatedData;
//        }

        Image barcode = new Image(width, height, "NV21");
        barcode.setData(data);
        barcode = barcode.convert("Y800");
        setupScanner();

        // scan image
        int result = mScanner.scanImage(barcode);
        if (result != 0) {
            //stopCamera();
            //if(mResultHandler != null) {
            SymbolSet syms = mScanner.getResults();
            Log.d(TAG, "Number of symbols in set: " + syms.size());
            Result rawResult = new Result();
            for (Symbol sym : syms) {
                if (sym.getType() == Symbol.ISBN13) {
                    String symData = sym.getData();
                    Toast.makeText(mContext, symData, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "SYMBOL DATA: " + symData);
                    mCamera.stopPreview();
                    break;
                }
            }

            //mResultHandler.handleResult(rawResult);
            // }
        } else {
            //camera.setOneShotPreviewCallback(this);
        }
    }
    public void setupScanner() {
        mScanner = new ImageScanner();
        //mScanner.setConfig(0, Config.X_DENSITY, 3);
        //mScanner.setConfig(0, Config.Y_DENSITY, 3);
        //mScanner.setConfig(Symbol.NONE, Config.ENABLE, 0);
        for(BarcodeFormat format : getFormats()) {
            mScanner.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    public Collection<BarcodeFormat> getFormats() {
        if(mFormats == null) {
            return BarcodeFormat.ALL_FORMATS;
        }
        return mFormats;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {
            mCamera = Camera.open();
        }
        catch (RuntimeException e) {
            System.err.println(e);
            return;
        }
        // get Camera parameters, set Camera Parameters
        Camera.Parameters param = mCamera.getParameters();

        // set format to NV21.  Later will be converted for use by barcode reader
        param.setPictureFormat(ImageFormat.NV21);
        param.setPreviewFormat(ImageFormat.NV21);

        // get supported focus modes
        // put this line in manifest, suggests want autofocus available
        // <uses-feature android:name="android.hardware.camera.autofocus" required="false"/>
        List<String> focusModes = param.getSupportedFocusModes();
        for (int i=0; i<focusModes.size(); i++) {
            Log.d(TAG, "Focus Mode " + i + " " + focusModes.get(i));
        }
        // set focus mode to continuous if exists, otherwise auto focus
        if (focusModes.contains((Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))) {
            param.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        // if it's autofocus mode, need to run camera.autoFocus() and have a callback for when autofocus complete

        // param.setPreviewSize(1280, 960);

        // set the camera parameters and start preview
        mCamera.setPreviewCallback(this);
        try {
            mCamera.setDisplayOrientation(90);
            mCamera.setParameters(param);

            mCamera.setPreviewDisplay(mSurfaceHolder);
            surfaceView.setVisibility(View.VISIBLE);
            mCamera.startPreview();
        }
        catch (Exception e){
            System.err.println(e);
            return;
        }
        setReaderSize();
    }
    public void setReaderSize() {
        // for fragment instead of activity
        //WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        //Display display = wm.getDefaultDisplay();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;
        int width=10;
        int height=10;
        Log.d(TAG, "Screen size width: " + screenWidth);
        Log.d(TAG, "Screen size height: " + screenHeight);
        Camera.Parameters param = mCamera.getParameters();
        List<Camera.Size> previewSizes = param.getSupportedPreviewSizes();
        Log.d(TAG, "Number of supported sizes: " + previewSizes.size());
        for (int i=0; i<previewSizes.size(); i++) {
            //if  (previewSizes.get(i).width < screenWidth) {
                //Log.d(TAG, "Screen size width: " + previewSizes.get(i).width );
                //Log.d(TAG, "Screen size height: " + previewSizes.get(i).height);
                //width = previewSizes.get(i).width;
                //height = previewSizes.get(i).height;
                // break;
            //}
        }
        // 0 is biggest size
        width = previewSizes.get(0).width;
        height = previewSizes.get(0).height;
        Log.d(TAG, "Preview Width: " + width);
        Log.d(TAG, "Preview height: " + height);
        float aspectRatio = (float) height/width;
        Log.d(TAG, "Aspect Ratio: " + aspectRatio);
        //Log.d(TAG, "Scanner Height: " + height);
        //Log.d(TAG, "Scanner Width: " + width);
        param.setPreviewSize(width, height);
        int padding=20;
        int readerWidth = screenWidth-2*padding;
        mSurfaceHolder.setFixedSize(readerWidth, Math.round(readerWidth*aspectRatio));
        mCamera.setParameters(param);

    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshCamera();

    }
   @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // stop preview and release camera
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
    public void refreshCamera() {
        if (mSurfaceHolder.getSurface() == null) {
            return;
        }
        try {
            mCamera.stopPreview();
        }
        catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        } catch (Exception e) {

        }
    }
}
