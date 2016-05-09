package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.util.ArrayList;
import java.util.List;

import it.jaschke.alexandria.CameraPreview.CameraPreview;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;
import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = AddBook.class.getSimpleName();

    private static final String SCAN_FORMAT = "scanFormat";
    private static final String SCAN_CONTENTS = "scanContents";

    private final int LOADER_ID = 1;
    private final String EAN_CONTENT="eanContent";
    private final String SCAN_STATE="scanState";

    private static final String scanFormat = "NV21";
    private static final String convertFormat = "Y800";
    private static final String ISBN10_PREFIX="978";


    ImageScanner mScanner;
    private List<BarcodeFormat> mFormats;

    Context mContext;
    private boolean mScanState = true;
    Cursor mData;

    private EditText mEan;
    private View rootView;
    private TextView mScanInstructionTextView;
    private TextView mManualInputInstructionTextView;
    private TextView mScanResultTextView;
    private TextView mScanISBN;
    private TextView mOrText;
    private LinearLayout mBookResultLinearLayout;
    private LinearLayout mEanContainerLinearLayout;
    private LinearLayout mBottomButtonBarLinearLayout;
    private Camera mCamera;
    private CameraPreview mCameraPreview;
    private FrameLayout mFrameLayout;
    private String mEanText;

    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mEan !=null) {
            Log.d(TAG, "mScanState: " + mScanState);
            outState.putString(EAN_CONTENT, mEan.getText().toString());
            outState.putBoolean(SCAN_STATE, mScanState);
        }
    }
    Camera.AutoFocusCallback autoFocusCb = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {

        }
    };


    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        mContext = getActivity();

        // Barcode formats read by scanner : need both EAN13 and ISBN13
        mFormats = new ArrayList<BarcodeFormat>();
        mFormats.add(BarcodeFormat.EAN13);
        mFormats.add(BarcodeFormat.ISBN13);

        // set all the views
        mEan = (EditText) rootView.findViewById(R.id.ean);
        mScanInstructionTextView = (TextView) rootView.findViewById(R.id.scanInstruction);
        mManualInputInstructionTextView = (TextView) rootView.findViewById(R.id.manualISBNInstruction);
        mScanResultTextView = (TextView) rootView.findViewById(R.id.scanResult);
        mScanISBN = (TextView) rootView.findViewById(R.id.scanISBN);
        mOrText = (TextView) rootView.findViewById(R.id.or_text);
        mBookResultLinearLayout = (LinearLayout) rootView.findViewById(R.id.book_result);
        mEanContainerLinearLayout = (LinearLayout) rootView.findViewById(R.id.eancontainer);
        mFrameLayout = (FrameLayout) rootView.findViewById(R.id.framelayout);
        mBottomButtonBarLinearLayout = (LinearLayout) rootView.findViewById(R.id.bottomButtonBar);

        mEan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mScanInstructionTextView.setVisibility(View.GONE);
                mOrText.setVisibility(View.GONE);
                stopCameraAndPreview();
            }
        });

        mEan.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {


                mEanText = s.toString();

                if (mEanText.length() < 13) {
                    return;
                }
                ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                    //Once we have an ISBN, start a book intent
                    Intent bookIntent = new Intent(getActivity(), BookService.class);
                    bookIntent.putExtra(BookService.EAN, mEanText);
                    bookIntent.setAction(BookService.FETCH_BOOK);
                    getActivity().startService(bookIntent);
                    AddBook.this.restartLoader();
                    // minimize the keyboard
                    InputMethodManager inputMethodManager = (InputMethodManager) mContext.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    View view = getActivity().getCurrentFocus();
                    if (view == null) {
                        view = new View(getActivity());
                    }
                    inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

                    mScanISBN.setText(getResources().getString(R.string.isbn_result_prefix) + mEanText);
                    toggleView();
                    removeField();
                } else {
                    Toast.makeText(mContext, getResources().getString(R.string.no_network_message), Toast.LENGTH_LONG).show();
                    mEan.setText("");
                    mEan.setHint(getResources().getString(R.string.input_hint));
                    startCameraAndPreview();
                }
            }
        });

        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEan.setText("");
                mEan.setHint(getResources().getString(R.string.input_hint));
                startCameraAndPreview();
                toggleView();
                mScanState = true;
                Toast.makeText(mContext, getResources().getString(R.string.book_added), Toast.LENGTH_LONG).show();

            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, mEanText);
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);

                mEan.setText("");
                mEan.setHint(getResources().getString(R.string.input_hint));
                startCameraAndPreview();
                toggleView();
                mScanState = true;

                Toast.makeText(mContext, getResources().getString(R.string.book_removed), Toast.LENGTH_SHORT).show();
            }
        });

        if(savedInstanceState!=null){
            mScanState = savedInstanceState.getBoolean(SCAN_STATE);
            if (savedInstanceState.getString(EAN_CONTENT).length()<13) {
                mEan.setText(savedInstanceState.getString(EAN_CONTENT));
                mEan.setHint(getResources().getString(R.string.input_hint));
            }
        }
        return rootView;
    }
    @Override
    public void onResume() {
        super.onResume();
        if (mScanState) {
            //toggleView();
            safeCameraOpen();
            startCameraAndPreview();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mCamera != null) {
            mFrameLayout.removeView(mCameraPreview);
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private void removeField() {
        mEanContainerLinearLayout.setVisibility(View.GONE);
    }

    public void setupScanner() {
        mScanner = new ImageScanner();
        for(BarcodeFormat format : mFormats) {
            mScanner.setConfig(format.getId(), Config.ENABLE, 1);
        }
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback(){
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            int width = size.width;
            int height = size.height;
            Image barcode = new Image(width, height, scanFormat);
            barcode.setData(data);
            barcode = barcode.convert(convertFormat);
            setupScanner();

            // scan image
            int result = mScanner.scanImage(barcode);
            if (result != 0) {

                SymbolSet syms = mScanner.getResults();
                Result rawResult = new Result();
                for (Symbol sym : syms) {
                    if (sym.getType() == Symbol.ISBN13) {
                        String symData = sym.getData();
                        stopCameraAndPreview();
                        mEan.setText(symData);
                        mEan.setHint(getResources().getString(R.string.input_hint));
                        mScanState = false;
                        break;
                    }
                }
            }
        }
    };

    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if(mEan.getText().length()==0){
            return null;
        }
        String eanStr= mEan.getText().toString();
        Log.d(TAG, "Loader created with " +eanStr);
        if(eanStr.length()==10 && !eanStr.startsWith(ISBN10_PREFIX)){
            eanStr=ISBN10_PREFIX+eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        showBookResult();
        mData = data;
        if (!data.moveToFirst()) {
            return;
        }
        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        Log.d(TAG, "AUTHOR: " + authors);
        if (authors == null) {
            authors = "";
        }
        String[] authorsArr = authors.split(",");
        ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
        ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",","\n"));
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if(Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
            rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }
        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

        rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }
    private boolean safeCameraOpen() {
        boolean qOpened = false;
        // check if camera exists
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            qOpened = false;
        }
        else {
            // release camera from other applications, try to open camera
            try {
                if (mCamera != null) {
                    mCamera.release();
                    mCamera = null;
                }
                mCamera = Camera.open();
                qOpened = (mCamera != null);
            } catch (Exception e) {
                Log.e(getString(R.string.app_name), "failed to open Camera");
                e.printStackTrace();
            }
        }
        return qOpened;
    }
    private void toggleView() {

        // Hide surfaceview, Hide text input, show isbn number
        // try to release the camera and remove the scanner view
        // if scan instruction is visible, toggle off scan instruction views and toggle on book result views
        if (mEanContainerLinearLayout.getVisibility() == View.VISIBLE) {
            // views for scanning action
            mScanInstructionTextView.setVisibility(View.GONE);
            mOrText.setVisibility(View.GONE);
            mManualInputInstructionTextView.setVisibility(View.GONE);
            //mEan.setVisibility(View.GONE);
            mEanContainerLinearLayout.setVisibility(View.GONE);
            // views for book result
            mScanResultTextView.setVisibility(View.VISIBLE);
            mScanISBN.setVisibility(View.VISIBLE);
            mBookResultLinearLayout.setVisibility(View.VISIBLE);
            mBottomButtonBarLinearLayout.setVisibility(View.VISIBLE);
        } else {
                mScanInstructionTextView.setVisibility(View.VISIBLE);
                mOrText.setVisibility(View.VISIBLE);
                mManualInputInstructionTextView.setVisibility(View.VISIBLE);
                //mEan.setVisibility(View.VISIBLE);
                mEanContainerLinearLayout.setVisibility(View.VISIBLE);
                // views for book result
                mScanResultTextView.setVisibility(View.GONE);
                mScanISBN.setVisibility(View.GONE);
                mBookResultLinearLayout.setVisibility(View.GONE);
                mBottomButtonBarLinearLayout.setVisibility(View.GONE);
        }
    }
    private void showBookResult() {
        mScanInstructionTextView.setVisibility(View.GONE);
        mOrText.setVisibility(View.GONE);
        mManualInputInstructionTextView.setVisibility(View.GONE);
        //mEan.setVisibility(View.GONE);
        mEanContainerLinearLayout.setVisibility(View.GONE);
        // views for book result
        mScanResultTextView.setVisibility(View.VISIBLE);
        mScanISBN.setVisibility(View.VISIBLE);
        mBookResultLinearLayout.setVisibility(View.VISIBLE);
        mBottomButtonBarLinearLayout.setVisibility(View.VISIBLE);
    }

    private void stopCameraAndPreview() {

        mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
        mCamera.release();
        mCamera = null;
        if (mCameraPreview != null) {
            mFrameLayout.removeView(mCameraPreview);
            mCameraPreview = null;
        }
    }
    private void startCameraAndPreview() {
        safeCameraOpen();
        if (mCameraPreview == null) {
            mCameraPreview = new CameraPreview(mContext, mCamera,
                    previewCallback,
                    autoFocusCb);
            mFrameLayout.addView(mCameraPreview);
        }
        mCamera.startPreview();
    }
}
