package ca.uwaterloo.Lab2_201_03;

import android.app.Activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.os.Build;
import ca.uwaterloo.Lab2_201.R;
import ca.uwaterloo.sensortoy.*;

public class MainActivity extends Activity {
	
	static LineGraphView graph;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		

		setContentView(R.layout.activity_main);
		
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction().add(R.id.container, new PlaceholderFragment()).commit();
		}



		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public enum stepState{
		atRest, startStep, stepPeak, stepDescent, stepRebound
	}
	
	/**
	 * A placeholder fragment containing a simple view.
	 */
	public class PlaceholderFragment extends Fragment {
		

		private FileOutputStream outputStream;
		public PlaceholderFragment() {
			try{
				outputStream = openFileOutput("test.txt", Context.MODE_PRIVATE); 
			}
			catch (Exception e) {
			}
		}

		
		class someSensorEventListener implements SensorEventListener {
			
			TextView output;
			TextView stepView;
			TextView stateView;
			
			private stepState currentState;
			private int stepCount;
			private long timeElapsed;

			private int sensorType;
			private float[] recordVals = new float[3];
			
			private float[] lowPassOut;
			
			private String sensorString;
			private String sensorValString;
			private String sensorRecordValString = "x: 0 y: 0 z: 0";
			
			public someSensorEventListener(TextView outputView, TextView _stepView, TextView _stateView, int wantedSensorType)
			{
				output = outputView;
				stepView = _stepView;
				stateView = _stateView;
				sensorType = wantedSensorType;
				currentState = stepState.atRest;
				
				// Change initial label to correspond to Sensor being recorded.
				switch (wantedSensorType)
				{
				case Sensor.TYPE_LINEAR_ACCELERATION:
					sensorString = "\nAcclerometer Reading:";
					break;
				case Sensor.TYPE_LIGHT:
					sensorString = "\nLight Reading:";
					sensorRecordValString = "";
					break;
				case Sensor.TYPE_MAGNETIC_FIELD:
					sensorString = "\nMagnetic Field Reading:";
					break;
				case Sensor.TYPE_ROTATION_VECTOR:
					sensorString = "\nRotation Vector Reading:";
					break;
				default:
					Log.d("Error","Invalid SensorType given! App will not work.");
				}
			}
			
			// Resets all record values to 0;
			public void clearRecords()
			{
				for (int i = 0; i < recordVals.length; i++)
				{
					recordVals[i] = 0;
				}
			}

			public void onAccuracyChanged(Sensor s, int i) {}
			
			public float[] lowPassFilter(float[] in, float[] out)
			{
				float a = 0.25f;
				if (out == null ) return in;
				for ( int i = 0; i <in.length; i++ ) {
					out[i] = out[i] + a *(in[i] - out[i]);
					//out[i] += (in[i] - out[i]) / a;
				}
				return out;
			}
			
			// for all sensors beside Light sensor, displays the x: y: z: values associated with it
			// and the absolute value records of each 
			public void onSensorChanged(SensorEvent se) {
				
				if (se.sensor.getType() == sensorType){

					if (sensorType != Sensor.TYPE_LIGHT){


						if(se.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
							lowPassOut = lowPassFilter(se.values.clone(), lowPassOut);
							se.values[0] = lowPassOut[0];
							se.values[1] = lowPassOut[1];
							se.values[2] = lowPassOut[2];


							// raw data graph
							//graph.addPoint(se.values);
							
							// low pass filter graph
							graph.addPoint(lowPassOut);

							/*
							try {
								outputStream.write(String.format("%.2f \n" ,lowPassOut[2]).getBytes());
							} catch (IOException e) {
								e.printStackTrace();
							}
							*/

							switch (currentState)
							{
								case atRest: 
									if ( (lowPassOut[2] > 0.15) && ( abs(lowPassOut[1]) > 1.0f)&& (lowPassOut[0] < 5.15f)) {
										currentState = stepState.startStep;
										timeElapsed = System.nanoTime();
									}
									stateView.setText("At rest");
									break;
								case startStep:
									if ( (lowPassOut[2] > 1.51f) && (abs(lowPassOut[1]) > 0.5f)) {
										currentState = stepState.stepPeak;
									}
									stateView.setText("Startstep");
									break;
								case stepPeak:
									if ( lowPassOut[2] > 6.8){
										currentState = stepState.atRest;
									}
									else if ( lowPassOut[2] < 3.5) {
										currentState = stepState.stepDescent;
									}
									stateView.setText("stepPeak");
									break;
								case stepDescent:
									if ( lowPassOut[2] < -0.15) {
										currentState = stepState.stepRebound;
									}
									stateView.setText("step descent");
									break;
								case stepRebound:
									if ( lowPassOut[2] > -0.15) {
										
										// how much time has passed since startStep was initiated
										timeElapsed = System.nanoTime() - timeElapsed;
										Log.d("time", String.valueOf(timeElapsed));
										if ( timeElapsed > 250000000){
											// time for step was less than 250ms
											// unreasonable for human being, reset state without updating counter
											stepCount++;
										} 
										
										currentState = stepState.atRest;
										timeElapsed = 0;

									}
									stateView.setText("stepRebound");
									break;
							}
							stepView.setText(String.valueOf(stepCount));


						}
						sensorValString = String.format("\n x: %.2f y: %.2f z: %.2f", se.values[0], se.values[1], se.values[2]);
						
						
						for (int i = 0; i < 3; i ++){
							if (abs(se.values[i]) > abs(recordVals[i])) {
								recordVals[i] = se.values[i];
							}
						}
						sensorRecordValString = String.format("\nRecord: x: %.2f y: %.2f z: %.2f", recordVals[0],  recordVals[1], recordVals[2]);
					} else {
						sensorValString = String.format(" %.2f", se.values[0]);
					}
					output.setText(String.valueOf(sensorString+sensorValString + sensorRecordValString)); 
				}
				
			}
			
			public void resetCounter()
			{
				stepCount = 0;
			}
	}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container, false);
			LinearLayout layout = (LinearLayout) rootView.findViewById(R.id.layout1);
			layout.setOrientation(LinearLayout.VERTICAL);
			
			try {

				graph = new LineGraphView(getActivity(), 100, Arrays.asList("x", "y", "z"));
				layout.addView(graph);
				graph.setVisibility(View.VISIBLE);
				
			} catch ( NullPointerException e){
				Log.d("exception", "null pointer!");
			}
			
			
			// using label id to add view
			//TextView lightValuesOut = (TextView) rootView.findViewById(R.id.label1);
			
			// programmatically adding labels
			TextView lightValuesOut = addNewLabel("", rootView, layout);
			TextView accelValuesOut = addNewLabel("", rootView, layout);
			TextView magFieldValuesOut = addNewLabel("", rootView, layout);
			TextView rotVecValuesOut = addNewLabel("", rootView, layout);
			TextView stepCount = addNewLabel("", rootView, layout);
			TextView currentState = addNewLabel("", rootView, layout);

			
	

			
			// Create sensor manager and Sensor references for each applicable sensor
			final SensorManager sensorManager = (SensorManager) rootView.getContext().getSystemService(SENSOR_SERVICE);
			Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
			Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
			Sensor magFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			Sensor rotVecSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
			
			// Create references to all needed eventListeners.
			SensorEventListener lightListener = new someSensorEventListener(lightValuesOut, stepCount, currentState, Sensor.TYPE_LIGHT);
			sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
			
			final SensorEventListener accelListener = new someSensorEventListener(accelValuesOut,stepCount, currentState, Sensor.TYPE_LINEAR_ACCELERATION);
			sensorManager.registerListener(accelListener, accelSensor, SensorManager.SENSOR_DELAY_FASTEST);
			
			final SensorEventListener magFieldListener = new someSensorEventListener(magFieldValuesOut,stepCount, currentState, Sensor.TYPE_MAGNETIC_FIELD);
			sensorManager.registerListener(magFieldListener, magFieldSensor, SensorManager.SENSOR_DELAY_NORMAL);
			
			final SensorEventListener rotVecListener = new someSensorEventListener(rotVecValuesOut,stepCount, currentState, Sensor.TYPE_ROTATION_VECTOR);
			sensorManager.registerListener(rotVecListener, rotVecSensor, SensorManager.SENSOR_DELAY_NORMAL);
			
			// Step count and state label
			
		
			// add clear button for class
			final Button clearButton = new Button(rootView.getContext());
			clearButton.setText("Clear");
			layout.addView(clearButton);
			clearButton.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					((someSensorEventListener) accelListener).clearRecords();
					((someSensorEventListener) magFieldListener).clearRecords();
					((someSensorEventListener) rotVecListener).clearRecords();
					((someSensorEventListener) accelListener).resetCounter();

					
				}
			});
			// add clear button for class
			final Button pauseButton = new Button(rootView.getContext());
			pauseButton.setText("Pause Graph");
			layout.addView(pauseButton);
			pauseButton.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					try {
						sensorManager.unregisterListener(accelListener);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			});	
			return rootView;
		
		}
		
		public TextView addNewLabel(String label,View rootView, LinearLayout layout)
		{
			TextView l = new TextView(rootView.getContext());
			l.setText(label);
			l.setTextSize((float) 15.0);
			layout.addView(l);
			
			return l;
		}
		
	}
	private float abs(float f) {
		// returns absolute value of a float
		if (f < 0)
			return (float) (-1.0*f);
		else
			return f;
	}

}
