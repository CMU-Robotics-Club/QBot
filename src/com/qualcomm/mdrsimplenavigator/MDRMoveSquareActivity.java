package com.qualcomm.mdrsimplenavigator;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.spi.Log;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

public class MDRMoveSquareActivity extends IOIOActivity{
	
	private final String TAG = "MDRMoveSquareActivity";
	private final int TOTAL_MOVE_TIME_IN_MS = 6000;
	private final int MOVE_FORWARD_IN_MS = 1000;
	private final int TURN_RIGHT_IN_MS = 500;
	
	public final int PWM_MAX_VAL_DEFAULT = 2000;
	public final int PWM_OFF_VAL = 0;
	
    public int mPwmDriveRightVal = PWM_OFF_VAL;
    public int mPwmDriveLeftVal = PWM_OFF_VAL;
	
	@SuppressWarnings("unused")
	private TextView mDisplayState;
	private Button mStopButton;
	private ProgressBar mProgressBar;
	
	private int mProgressBarState = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.movesquare);
		
		mStopButton = (Button) findViewById(R.id.btnStop);
		mDisplayState = (TextView) findViewById(R.id.movesquaredisplay);
		mProgressBar = (ProgressBar)findViewById(R.id.squareprogressbar);
		
	}
	
	class SquareLooper extends BaseIOIOLooper {
		private final int PWM_PIN_DRIVE_LEFT = 2;
		private final int PWM_PIN_DRIVE_RIGHT = 3;
		
		private final int PWM_FREQUENCY_IN_HZ = 50;
		
		private PwmOutput mPwmDriveRight;
	    private PwmOutput mPwmDriveLeft;
	   
	    @Override
	    public void setup() throws ConnectionLostException {
	    	try {
	    		mPwmDriveRight = ioio_.openPwmOutput(PWM_PIN_DRIVE_RIGHT, PWM_FREQUENCY_IN_HZ);
	    		mPwmDriveLeft = ioio_.openPwmOutput(PWM_PIN_DRIVE_LEFT, PWM_FREQUENCY_IN_HZ);
	    	} catch (final ConnectionLostException e){
	    			final String errorString =
	    				"Connection to controller NOT " + "available, configuration aborted.";
	    		    displayToast(errorString);
	    		    Log.e(TAG, errorString);
	    		    e.printStackTrace();
	    	}
	    	
	    	mProgressBar.setIndeterminate(false); 
	    	mProgressBar.setProgress(0);
			mProgressBar.setMax(100);
	    	
	    	final Timer timer = new Timer();
			timer.schedule(new ReturnTask(), TOTAL_MOVE_TIME_IN_MS);
	    	
			timer.schedule(new MoveForwardTask(), MOVE_FORWARD_IN_MS,
					MOVE_FORWARD_IN_MS + TURN_RIGHT_IN_MS);
			timer.schedule(new TurnRightTask(), TURN_RIGHT_IN_MS,
					MOVE_FORWARD_IN_MS + TURN_RIGHT_IN_MS);
			
			timer.schedule(new HandleProgress(), TOTAL_MOVE_TIME_IN_MS / 100,
					TOTAL_MOVE_TIME_IN_MS / 100);
			
			mStopButton.setOnClickListener(new OnClickListener(){
		    	@Override
		    	public void onClick(View v){
		    		timer.cancel();
		    		Intent back = new Intent(MDRMoveSquareActivity.this, MDRNavigatorActivity.class);
					startActivity(back);
		    	}
		    });
	    }
	    
	    @Override
	    public void loop() throws ConnectionLostException, InterruptedException {
	      // Pass on the latest PWM values to the IOIO hardware
	      ioio_.beginBatch();
	      try {
	        mPwmDriveRight.setPulseWidth(mPwmDriveRightVal);
	        mPwmDriveLeft.setPulseWidth(mPwmDriveLeftVal);
	      } catch (final Exception e) {
	        final String errorString = "Unable to set motor PWMs!";
	        displayToast(errorString);
	        Log.e(TAG, "Caught exception writing to PWMs");
	        e.printStackTrace();
	      } finally {
	        ioio_.endBatch();
	      }
	    }
	    	
	}
	
	protected IOIOLooper createIOIOLooper() {
	    return new SquareLooper();
	  }
	
	private void displayToast(final String toastMsg) {
	    final Context context = this;
	    runOnUiThread(new Runnable() {
	      @Override
	      public void run() {
	        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
	      }
	    });
	  }
	
	class ReturnTask extends TimerTask {
		@Override
		public void run() {
			Intent back = new Intent(MDRMoveSquareActivity.this, MDRNavigatorActivity.class);
			startActivity(back);
		}
	}
	
	class MoveForwardTask extends TimerTask {
		@Override
		public void run() {
			mPwmDriveRightVal = PWM_MAX_VAL_DEFAULT;
			mPwmDriveLeftVal = PWM_MAX_VAL_DEFAULT;
		}
	}
	
	class TurnRightTask extends TimerTask {
		@Override
		public void run() {
			mPwmDriveRightVal = PWM_OFF_VAL;
			mPwmDriveLeftVal = PWM_MAX_VAL_DEFAULT;
		}
	}
	
	class HandleProgress extends TimerTask {
		@Override
		public void run() {
			mProgressBar.setProgress(++mProgressBarState);
		}
	}
		
}
	

