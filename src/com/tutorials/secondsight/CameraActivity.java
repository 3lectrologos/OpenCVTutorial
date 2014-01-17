package com.tutorials.secondsight;

import java.io.File;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

public class CameraActivity extends Activity implements CvCameraViewListener2 {
  private static final String STATE_CAMERA_INDEX = "cameraIndex";
  private static final String TAG = "CameraActivity";
  
  private int mCameraIndex;
  private boolean mIsCameraFrontFacing;
  private int mNumCameras;
  private CameraBridgeViewBase mCameraView;
  private boolean mIsPhotoPending;
  private Mat mBgr;
  private boolean mIsMenuLocked;
  
  private BaseLoaderCallback mLoaderCallback =
    new BaseLoaderCallback(this) {
      @Override
      public void onManagerConnected(final int status) {
        switch(status) {
          case LoaderCallbackInterface.SUCCESS:
            mCameraView.enableView();
            mBgr = new Mat();
            break;
          default:
            super.onManagerConnected(status);
            break;
        }
      }
  };
  
  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    
    if(savedInstanceState != null) {
      mCameraIndex = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
    } else {
      mCameraIndex = 0;
    }
    
    CameraInfo cameraInfo = new CameraInfo();
    Camera.getCameraInfo(mCameraIndex, cameraInfo);
    mIsCameraFrontFacing =
        (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT);
    mNumCameras = Camera.getNumberOfCameras();

    // Using Java instead of native, because the latter crashes after
    // a few seconds
    mCameraView = new JavaCameraView(this, mCameraIndex);
    mCameraView.setCvCameraViewListener(this);
    setContentView(mCameraView);
  }
  
  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putInt(STATE_CAMERA_INDEX, mCameraIndex);
  }
  
  @Override
  public void onPause() {
    if(mCameraView != null) {
      mCameraView.disableView();
    }
    super.onPause();
  }
  
  @Override
  public void onResume() {
    super.onResume();
    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_7,
                           this,
                           mLoaderCallback);
    mIsMenuLocked = false;
  }
  
  @Override
  public void onDestroy() {
    if(mCameraView != null) {
      mCameraView.disableView();
    }
    super.onDestroy();
  }
  
  @Override
  public boolean onCreateOptionsMenu(final Menu menu) {
    getMenuInflater().inflate(R.menu.activity_camera, menu);
    if(mNumCameras < 2) {
      menu.removeItem(R.id.menu_next_camera);
    }
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    if(mIsMenuLocked) {
      return true;
    }
    switch(item.getItemId()) {
      case R.id.menu_next_camera:
        mIsMenuLocked = true;
        mCameraIndex++;
        if(mCameraIndex == mNumCameras) {
          mCameraIndex = 0;
        }
        recreate();
        return true;
      case R.id.menu_take_photo:
        mIsMenuLocked = true;
        mIsPhotoPending = true;
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onCameraViewStarted(int width, int height) {    
  }

  @Override
  public void onCameraViewStopped() {
  }

  @Override
  public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
    final Mat rgba = inputFrame.rgba();
    if(mIsPhotoPending) {
      mIsPhotoPending = false;
      takePhoto(rgba);
    }
    if(mIsCameraFrontFacing) {
      Core.flip(rgba, rgba, 1);
    }
    return rgba;
  }
  
  private void takePhoto(Mat rgba) {
    final long currentTimeMillis = System.currentTimeMillis();
    final String appName = getString(R.string.app_name);
    final String galleryPath =
        Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES).toString();
    final String albumPath = galleryPath + "/" + appName;
    final String photoPath = albumPath + "/" + currentTimeMillis + ".png";
    final ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DATA, photoPath);
    values.put(Images.Media.MIME_TYPE, LabActivity.PHOTO_MIME_TYPE);
    values.put(Images.Media.TITLE, appName);
    values.put(Images.Media.DESCRIPTION, appName);
    values.put(Images.Media.DATE_TAKEN, currentTimeMillis);
    
    File album = new File(albumPath);
    if(!album.isDirectory() && !album.mkdirs()) {
      Log.e(TAG, "Failed to create album directory at " + albumPath);
      onTakePhotoFailed();
      return;
    }
    
    Imgproc.cvtColor(rgba, mBgr, Imgproc.COLOR_RGBA2BGR, 3);
    if(!Highgui.imwrite(photoPath, mBgr)) {
      Log.e(TAG, "Failed to save photo to " + photoPath);
      onTakePhotoFailed();
    }
    
    Uri uri;
    try {
      uri = getContentResolver()
          .insert(Images.Media.EXTERNAL_CONTENT_URI, values);
    } catch(Exception e) {
      File photo = new File(photoPath);
      if(!photo.delete()) {
        Log.e(TAG, "Failed to delete non-inserted photo");
      }
      onTakePhotoFailed();
      return;
    }
    
    final Intent intent = new Intent(this, LabActivity.class);
    intent.putExtra(LabActivity.EXTRA_PHOTO_URI, uri);
    intent.putExtra(LabActivity.EXTRA_PHOTO_DATA_PATH, photoPath);
    startActivity(intent);
  }
  
  private void onTakePhotoFailed() {
    mIsMenuLocked = false;
    final String errorMessage = getString(R.string.photo_error_message);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CameraActivity.this, errorMessage, Toast.LENGTH_SHORT)
             .show();
      }
    });
  }
}
