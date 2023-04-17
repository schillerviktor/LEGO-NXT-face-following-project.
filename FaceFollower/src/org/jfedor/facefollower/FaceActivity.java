package org.jfedor.facefollower;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

public class FaceActivity extends Activity {
    
    OutputStream out = null;
    BluetoothSocket socket = null;
    
    SurfaceView mSurfaceView;
    FaceOverlay mFaceOverlay;
    Camera mCamera;
    int mRotation;
    boolean previewOn = false;
    boolean faceDetectionStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_face);

        final View contentView = findViewById(R.id.fullscreen_content);
        
        // Hide the notificaton bar.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Hide the on-screen navigation buttons on the devices that have them.
        // Any interaction brings them back, but we don't care as this application is non-interactive,
        // so we don't set up any mechanism to hide them again.
        contentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        
        mSurfaceView = (SurfaceView) contentView;
        mFaceOverlay = (FaceOverlay) findViewById(R.id.overlay);
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            if (socket != null) {
                if (out != null) {
                    motors((byte) 0, (byte) 0, false, false);
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        if (mCamera != null) {
            if (faceDetectionStarted) {
                mCamera.stopFaceDetection();
                faceDetectionStarted = false;
            }
            if (previewOn) {
                mCamera.stopPreview();
                previewOn = false;
            }
            mCamera.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = null;
        
        // Go through paired devices to find the NXT brick using the device class.
        Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
        for (BluetoothDevice d : pairedDevices) {
            if ((d.getBluetoothClass() != null) && (d.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.TOY_ROBOT)) {
                device = d;
                break;
            }
        }
        // Alternatively, set the device address manually:
        //device = adapter.getRemoteDevice("xx:xx:xx:xx:xx:xx");

        try {
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            socket.connect();
            out = socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        mCamera = Camera.open(1);
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);
        mRotation = getWindowManager().getDefaultDisplay().getRotation();
        int cameraRotation = 0;
        if (mRotation == Surface.ROTATION_90) {
            cameraRotation = 90;
        } else if (mRotation == Surface.ROTATION_180) {
            cameraRotation = 180;
        } else if (mRotation == Surface.ROTATION_270) {
            cameraRotation = 270;
        }
        int orientation = (info.orientation-cameraRotation+360)%360;
        mCamera.setDisplayOrientation(orientation);
        mFaceOverlay.orientation = orientation;
        
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(1280, 720); // XXX
        mCamera.setParameters(parameters);
        SurfaceHolder sh1 = mSurfaceView.getHolder();
        sh1.removeCallback(surfaceCallback);
        sh1.addCallback(surfaceCallback);
        
        Log.i("Face", "max detected faces: "+parameters.getMaxNumDetectedFaces());
        
        mCamera.setFaceDetectionListener(faceDetectionListener);
    }
    
    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            mCamera.startPreview();
            // When I immediately call startFaceDetection() here, I get a crash (why?),
            // so I call it when the first frame arrives.
            mCamera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (!faceDetectionStarted) {
                        mCamera.startFaceDetection();
                        faceDetectionStarted = true;
                    }
                }
            });
            previewOn = true;
        }

        public void surfaceCreated(SurfaceHolder sh) {
            try {
                mCamera.setPreviewDisplay(sh);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void surfaceDestroyed(SurfaceHolder arg0) {
        }

    };
    
    Camera.FaceDetectionListener faceDetectionListener = new Camera.FaceDetectionListener() {

        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            if (faces.length > 0) {
                // We could see if there's more than one face and do something in that case. What though?
                Rect rect = faces[0].rect;
                mFaceOverlay.setRect(rect);
                float x = (rect.left + rect.right)*0.5f;
                // If the face is on the left, turn left, if it's on the right, turn right.
                // Speed is proportional to how far to the side of the image the face is.
                // The coordinates we get from face detection are from -1000 to 1000.
                // NXT motor speed is from -100 to 100.
                byte power = (byte) Math.min(100, (x/10f));
                motors(power, (byte) -power, false, false);
            } else {
                mFaceOverlay.setRect(null);
                motors((byte) 0, (byte) 0, false, false);
            }
            mFaceOverlay.invalidate();
        }
    };

    public void motors(byte l, byte r, boolean speedReg, boolean motorSync) {
        // See Lego website for the protocol.
        // http://mindstorms.lego.com/en-us/support/files/default.aspx
        byte[] data = { 0x0c, 0x00, (byte) 0x80, 0x04, 0x02, 0x32, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00,
                        0x0c, 0x00, (byte) 0x80, 0x04, 0x01, 0x32, 0x07, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00 };
        
        data[5] = l;
        data[19] = r;
        if (speedReg) {
            data[7] |= 0x01;
            data[21] |= 0x01;
        }
        if (motorSync) {
            data[7] |= 0x02;
            data[21] |= 0x02;
        }
        try {
            out.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
