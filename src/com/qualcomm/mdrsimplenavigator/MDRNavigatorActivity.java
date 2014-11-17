/*
 * Copyright (c) 2014 Qualcomm Technologies Inc
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * (subject to the limitations in the disclaimer below) provided that the following conditions are
 * met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 * 
 * Neither the name of Qualcomm Technologies Inc nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * 
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS LICENSE. THIS
 * SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.qualcomm.mdrsimplenavigator;

import ioio.lib.api.IOIO;
import ioio.lib.api.IOIO.VersionType;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;

/**
 * MDR robot simple navigator main activity
 */
public class MDRNavigatorActivity extends IOIOActivity {
  private final String TAG = "MDRNavigatorActivity";

  private TextView mTextView = null;
  private ImageButton mBtnForkliftUp = null;
  private ImageButton mBtnForkliftDown = null;
  private ImageButton mBtnCameraPanLeft = null;
  private ImageButton mBtnCameraPanRight = null;
  private DualJoystickView mDualJoystickView = null;
  // New
  private Button mMoveSquare = null;
  private String mVersionString = "";
  private boolean mControllerConnected = false;
  
  // The IOIO board should be programmed with this application firmware
  private static final String COMPATIBLE_APP_FIRMWARE = "IOIO0500";

  /**
   * This function updates the text view with the version information and latest connection status
   */
  void updateDisplayText() {
    mTextView = (TextView) findViewById(R.id.textPlaceHolder);
    mVersionString = Version.getBuildLabel(this);

    // Display the robot version and connection state
    runOnUiThread(new Runnable() {
      String infoString = "";

      @SuppressLint("DefaultLocale")
      @Override
      public void run() {
        // Create the information string and update the display
        infoString = "Version: " + mVersionString;
        
        // Append C or X to the end indicate whether or not the controller is connected
        if (mControllerConnected == true) {
          infoString += " (C)";
        } else {
          infoString += " (X)";
        }
        
        // Update the text view
        if (mTextView != null) {
          mTextView.setText(infoString);
        } else {
          Log.e(TAG, "Text View not initialized!");
        }
      }
    });
  }

  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.navmain);

    updateDisplayText();
    
    mBtnForkliftUp = (ImageButton) findViewById(R.id.btnForkliftUp);
    mBtnForkliftDown = (ImageButton) findViewById(R.id.btnForkliftDown);

    mBtnCameraPanLeft = (ImageButton) findViewById(R.id.btnCameraPanLeft);
    mBtnCameraPanRight = (ImageButton) findViewById(R.id.btnCameraPanRight);

    mDualJoystickView = (DualJoystickView) findViewById(R.id.dualjoystickView);
    
    mMoveSquare = (Button) findViewById(R.id.btnMoveSquare);

    enableUi(false);
  }

  /**
   * This class implements all methods related to IOIO board setup and control.
   */
  class Looper extends BaseIOIOLooper {

    // PWN pin outputs to the servo motors
    private final int PWM_PIN_CAMERA_PAN = 1;
    private final int PWM_PIN_DRIVE_LEFT = 2;
    private final int PWM_PIN_DRIVE_RIGHT = 3;
    private final int PWM_PIN_FORKLIFT = 4;

    // Default PWM constants
    private final int PWM_CENTER_VAL_DEFAULT = 1500;
    private final int PWM_MIN_VAL_DEFAULT = 1000;
    private final int PWM_MAX_VAL_DEFAULT = 2000;
    private final int PWM_CHANGE_VAL = 25;
    private final int PWM_OFF_VAL = 0;

    private final int PWM_FREQUENCY_IN_HZ = 50;

    private final float JOYSTICK_MOVEMENT_RANGE = 25.0f;
    private final float JOYSTICK_MOVE_RESOLUTION = 1.0f;

    private final int SLEEP_DELAY_IN_MSEC = 10;
    
    private PwmOutput mPwmForklift;
    private PwmOutput mPwmDriveRight;
    private PwmOutput mPwmDriveLeft;
    private PwmOutput mPwmCameraPan;

    // Set all motors to stop or center positions
    private int mPwmDriveRightVal = PWM_OFF_VAL;
    private int mPwmDriveLeftVal = PWM_OFF_VAL;
    private int mPwmForkliftVal = PWM_CENTER_VAL_DEFAULT;
    private int mPwmCameraPanVal = PWM_CENTER_VAL_DEFAULT;

    @Override
    public void setup() throws ConnectionLostException {
      mControllerConnected = true;
      updateDisplayText();
      
      displayVersions(ioio_, "Versions:", false);

      try {
        mPwmForklift = ioio_.openPwmOutput(PWM_PIN_FORKLIFT, PWM_FREQUENCY_IN_HZ);
        mPwmDriveRight = ioio_.openPwmOutput(PWM_PIN_DRIVE_RIGHT, PWM_FREQUENCY_IN_HZ);
        mPwmDriveLeft = ioio_.openPwmOutput(PWM_PIN_DRIVE_LEFT, PWM_FREQUENCY_IN_HZ);
        mPwmCameraPan = ioio_.openPwmOutput(PWM_PIN_CAMERA_PAN, PWM_FREQUENCY_IN_HZ);
      } catch (final ConnectionLostException e) {
        final String errorString =
            "Connection to controller NOT " + "available, configuration aborted.";
        displayToast(errorString);
        Log.e(TAG, errorString);
        e.printStackTrace();
      }

      // MDR motor actions for different button presses.
      
      mMoveSquare.setOnClickListener(new OnClickListener(){
    	  @Override
    	  public void onClick(View v){
    		  Intent i = new Intent(MDRNavigatorActivity.this, MDRMoveSquareActivity.class);
        	  startActivity(i);
    	  }
      });
      
      mBtnForkliftUp.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          // Increment PWM value to raise the forklift by a fixed amount
          // (within allowed range)
          if (mPwmForkliftVal <= (PWM_MAX_VAL_DEFAULT - PWM_CHANGE_VAL)) {
            mPwmForkliftVal += PWM_CHANGE_VAL;
            Log.v(TAG, "New forklift pwm val: " + mPwmForkliftVal);
          }
        }
      });

      mBtnForkliftDown.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          // Decrement PWM value to lower the forklift by a fixed amount
          // (within allowed range)
          if (mPwmForkliftVal >= (PWM_MIN_VAL_DEFAULT + PWM_CHANGE_VAL)) {
            mPwmForkliftVal -= PWM_CHANGE_VAL;
            Log.v(TAG, "New forklift pwm val: " + mPwmForkliftVal);
          }
        }
      });

      mBtnCameraPanLeft.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          // Increment PWM value to rotate the mirror clockwise by a
          // fixed amount (within allowed range)
          if (mPwmCameraPanVal <= (PWM_MAX_VAL_DEFAULT - PWM_CHANGE_VAL)) {
            mPwmCameraPanVal += PWM_CHANGE_VAL;
            Log.v(TAG, "New camera pan pwm val: " + mPwmCameraPanVal);
          }
        }
      });

      mBtnCameraPanRight.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          // Decrement PWM value to rotate the mirror clockwise by a
          // fixed amount (within allowed range)
          if (mPwmCameraPanVal >= (PWM_MIN_VAL_DEFAULT + PWM_CHANGE_VAL)) {
            mPwmCameraPanVal -= PWM_CHANGE_VAL;
            Log.v(TAG, "New camera pan pwm val: " + mPwmCameraPanVal);
          }
        }
      });

      // End MDR motor actions for different button presses

      final JoystickMovedListener listenerRight = new JoystickMovedListener() {

        @Override
        public void OnMoved(int rightLeftPos, int frontBackPos) {
          final float yVal = frontBackPos / JOYSTICK_MOVEMENT_RANGE;
          final float xVal = rightLeftPos / JOYSTICK_MOVEMENT_RANGE;

          final float leftTorque = 0.5F * (xVal - yVal);
          final float rightTorque = 0.5F * (xVal + yVal);

          mPwmDriveLeftVal = getPwmFromTorque(leftTorque);
          mPwmDriveRightVal = getPwmFromTorque(rightTorque);
        }

        private int getPwmFromTorque(float torque) {
          int pwmVal = 0;
          final float deadBand = 0.05F; // 5% dead-band
          
          // Convert motor torques to motor PWM values (with )
          if (torque > deadBand) {
            pwmVal = (int) ((PWM_MAX_VAL_DEFAULT - PWM_CENTER_VAL_DEFAULT) * torque);
            pwmVal += PWM_CENTER_VAL_DEFAULT;
          } else if (torque < -deadBand) {
            pwmVal = (int) ((PWM_CENTER_VAL_DEFAULT - PWM_MIN_VAL_DEFAULT) * torque);
            pwmVal += PWM_CENTER_VAL_DEFAULT;
          }

          return pwmVal;
        }

        @Override
        public void OnReleased() {
          mPwmDriveLeftVal = PWM_OFF_VAL;
          mPwmDriveRightVal = PWM_OFF_VAL;
        }

        @Override
        public void OnReturnedToCenter() {
          mPwmDriveLeftVal = PWM_OFF_VAL;
          mPwmDriveRightVal = PWM_OFF_VAL;
        }
      };

      final JoystickMovedListener listenerLeft = new JoystickMovedListener() {
        @Override
        public void OnMoved(int cameraPan, int forklift) {
          forklift = -forklift;

          int forkliftPwm =
              (int) ((forklift / JOYSTICK_MOVEMENT_RANGE) * (PWM_MAX_VAL_DEFAULT - PWM_CENTER_VAL_DEFAULT));
          forkliftPwm += PWM_CENTER_VAL_DEFAULT;

          Log.d(TAG, "L: forklift: " + forklift + " forkliftPwm: " + forkliftPwm);

          mPwmForkliftVal = forkliftPwm;
        }

        @Override
        public void OnReleased() {
          mPwmForkliftVal = PWM_CENTER_VAL_DEFAULT;
        }

        @Override
        public void OnReturnedToCenter() {
          mPwmForkliftVal = PWM_CENTER_VAL_DEFAULT;
        }
      };

      // NOTE There is a bug in MobileAnarchy that prevents this from taking
      // effect on the right robot. So initializing it to true for now
      // (since that is the default)
      mDualJoystickView.setYAxisInverted(true, true);
      mDualJoystickView.setMovementRange(JOYSTICK_MOVEMENT_RANGE, JOYSTICK_MOVEMENT_RANGE);
      mDualJoystickView.setMoveResolution(JOYSTICK_MOVE_RESOLUTION, JOYSTICK_MOVE_RESOLUTION);
      mDualJoystickView.setOnJostickMovedListener(listenerLeft, listenerRight);

      // End MDR motor actions for different button presses

      enableUi(true);
    }

    /**
     * This method would be invoked continuously after setup. It sends the last calculated PWM
     * values to the IOIO controller.
     */
    @Override
    public void loop() throws ConnectionLostException, InterruptedException {
      // Pass on the latest PWM values to the IOIO hardware
      ioio_.beginBatch();
      try {
        mPwmDriveRight.setPulseWidth(mPwmDriveRightVal);
        mPwmDriveLeft.setPulseWidth(mPwmDriveLeftVal);
        mPwmForklift.setPulseWidth(mPwmForkliftVal);
        mPwmCameraPan.setPulseWidth(mPwmCameraPanVal);
      } catch (final Exception e) {
        final String errorString = "Unable to set motor PWMs!";
        displayToast(errorString);
        Log.e(TAG, "Caught exception writing to PWMs");
        e.printStackTrace();
      } finally {
        ioio_.endBatch();
      }

      Thread.sleep(SLEEP_DELAY_IN_MSEC);
    }

    /** Handler that is invoked upon disconnection from IOIO hardware */
    @Override
    public void disconnected() {
      final String errorString = "No connection to controller!";
      displayToast(errorString);
      
      mControllerConnected = false;
      updateDisplayText();
      
      enableUi(false);
    }

    // Handler that is invoked if the IOIO board application firmware if
    // not compatible with the IOIO software libraries in use.
    @Override
    public void incompatible() {
      displayVersions(ioio_, "Incompatible IOIO Application Firmware Version! "
          + "Please upgrade to " + COMPATIBLE_APP_FIRMWARE + ".", true);
    }

  }

  @Override
  protected IOIOLooper createIOIOLooper() {
    return new Looper();
  }

  private void enableUi(final boolean enable) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mBtnForkliftUp.setEnabled(enable);
        mBtnForkliftDown.setEnabled(enable);
        mBtnCameraPanLeft.setEnabled(enable);
        mBtnCameraPanRight.setEnabled(enable);

      }
    });
  }

  /** Display the version information */
  private void displayVersions(IOIO ioio, String title, boolean useDialog) {
    final String versionMsg =
        String.format("%s\n" + "IOIOLib: %s\n" + "Application firmware: %s\n"
            + "Bootloader firmware: %s\n" + "Hardware: %s", title,
            ioio.getImplVersion(VersionType.IOIOLIB_VER),
            ioio.getImplVersion(VersionType.APP_FIRMWARE_VER),
            ioio.getImplVersion(VersionType.BOOTLOADER_VER),
            ioio.getImplVersion(VersionType.HARDWARE_VER));

    if (useDialog == true) {
      displayDialog(versionMsg);
    } else {
      displayToast(versionMsg);
    }
  }

  /** Display a custom toast message */
  private void displayToast(final String toastMsg) {
    final Context context = this;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
      }
    });
  }

  /** Display a dialog box with custom message */
  private void displayDialog(final String dialogMsg) {
    // create the dialog fragment
    final DialogFragment newFragment = new GenericDialogFragment();

    // Supply the input message as argument.
    final Bundle args = new Bundle();
    args.putString("dialogMsg", dialogMsg);
    newFragment.setArguments(args);

    // display the dialog message
    newFragment.show(getFragmentManager(), "dialogTag");
  }

}
