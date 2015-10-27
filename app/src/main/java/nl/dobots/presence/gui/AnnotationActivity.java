package nl.dobots.presence.gui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import nl.dobots.bluenet.utils.logger.BleLogger;
import nl.dobots.presence.PresenceDetectionApp;
import nl.dobots.presence.R;

public class AnnotationActivity extends AppCompatActivity {
	public static String TAG = AnnotationActivity.class.getCanonicalName();

	int _currentFloor = -1;

	ImageView _imageView;
	Bitmap _bitmap;

	int[] _floorIds = {
//			R.drawable.basement,
			R.drawable.ground,
			R.drawable.floor_1,
			R.drawable.floor_2,
			R.drawable.floor_3
	};
	String[] _floorsText = {
//			"basement",
			"ground floor",
			"1st floor",
			"2nd floor",
			"top floor"
	};
	String[] _floorsLogText = {
//			"basement",
			"ground",
			"floor_1",
			"floor_2",
			"floor_3"
	};



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_annotation);

		_currentFloor = 0;
		loadImage(_floorIds[_currentFloor]);

		_imageView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// X and Y are in pixels, with 0,0 being the top left of the image
//				Log.d(TAG, "pos: " + event.getX() + ", " + event.getY());
//				Log.d(TAG, "Scale: " + _imageView.getScaleX() + " " + _imageView.getScaleY() + " " + _imageView.getScaleType().toString());
//				Log.d(TAG, "Size: " + _imageView.getWidth() + " " + _imageView.getHeight());
//				Drawable drawing = _imageView.getDrawable();
//				Bitmap bitmap = ((BitmapDrawable) drawing).getBitmap();
//				Log.d(TAG, "Scaled bitmap: " + bitmap.getWidth() + " " + bitmap.getHeight());


				float scaleX = (float) _imageView.getWidth() / _bitmap.getWidth();
				float scaleY = (float) _imageView.getHeight() / _bitmap.getHeight();
				// Use smallest scale
				float scale = scaleX < scaleY ? scaleX : scaleY;
//				int maxX = Math.round(bitmap.getWidth() * scale);
//				int maxY = Math.round(bitmap.getHeight() * scale);
				float xFloat = event.getX() / scale;
				float yFloat = event.getY() / scale;
				int xInt = Math.round(xFloat);
				int yInt = Math.round(yFloat);

//				Log.d(TAG, "scale=" + scale + " maxX=" + maxX + " maxY=" + maxY);

//				if (event.getX() < maxX && event.getY() <= maxY) {
				if (xInt < _bitmap.getWidth() && yInt < _bitmap.getHeight()) {
					Log.d(TAG, "at location " + xFloat + " " + yFloat);
					PresenceDetectionApp.getInstance().logLine(BleLogger.BleLogEvent.setLocation, _floorsLogText[_currentFloor] + " " + xFloat + " " + yFloat);
					Bitmap bitmapCopy = _bitmap.copy(Bitmap.Config.ARGB_8888, true);


					bitmapCopy.setPixel(xInt, yInt, Color.RED);
					int r = Math.round(3/scale);
					int xMin = (xInt-r < 0) ? 0 : xInt-r;
					int xMax = (xInt+r >= _bitmap.getWidth()) ? _bitmap.getWidth() : xInt+r;
					int yMin = (yInt-r < 0) ? 0 : yInt-r;
					int yMax = (yInt+r >= _bitmap.getHeight()) ? _bitmap.getHeight() : yInt+r;

					for (int x=xMin; x<=xMax; ++x) {
						for (int y=yMin; y<=yMax; ++y) {
							bitmapCopy.setPixel(x, y, Color.RED);
						}
					}


					_imageView.setImageBitmap(bitmapCopy);
				}


				// Don't consume the event
				return false;
			}
		});


		Spinner spinner = (Spinner) findViewById(R.id.spinner);

		ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, _floorsText);
		arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(arrayAdapter);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "Selected " + position);

				if (_currentFloor != position) {
					_currentFloor = position;
					loadImage(_floorIds[_currentFloor]);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				Log.d(TAG, "Selected nothing");
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		PresenceDetectionApp.getInstance().logLine(BleLogger.BleLogEvent.appForeGround);
	}

	@Override
	protected void onPause() {
		super.onPause();
		PresenceDetectionApp.getInstance().logLine(BleLogger.BleLogEvent.appBackGround);
	}

	private void loadImage(int floorId) {
		BitmapFactory.Options bmOptions = new BitmapFactory.Options();
		bmOptions.inScaled = false;
		_bitmap = BitmapFactory.decodeResource(getResources(), floorId, bmOptions);
		Log.d(TAG, "bitmap: " + _bitmap.getWidth() + " " + _bitmap.getHeight());

//		_imageView.getParent() // Use parent to determine what size to make the imageView

		_imageView = (ImageView) findViewById(R.id.imageView);
		_imageView.setImageBitmap(_bitmap);
//		_imageView.setBackgroundColor(Color.GREEN); // alpha, red, green, blue
		_imageView.setScaleType(ImageView.ScaleType.FIT_START); // Scales uniform, but image is larger than view
//		_imageView.setScaleType(ImageView.ScaleType.CENTER); // no scaling, but image is larger than view
//		_imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE); // Scales uniformly, but view is larger than image

//		_imageView.setAdjustViewBounds(true);
//		_imageView.setImageResource(R.drawable.almende_floor_2_png_test);

//		Log.d(TAG, "Scale: " + _imageView.getScaleX() + " " + _imageView.getScaleY() + " " + _imageView.getScaleType().toString());
		Log.d(TAG, "Size: " + _imageView.getWidth() + " " + _imageView.getHeight());
		Drawable drawing = _imageView.getDrawable();
		_bitmap = ((BitmapDrawable)drawing).getBitmap();
		Log.d(TAG, "Scaled bitmap: " + _bitmap.getWidth() + " " + _bitmap.getHeight());
	}

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.menu_annotation, menu);
//		return true;
//	}
//
//	@Override
//	public boolean onOptionsItemSelected(MenuItem item) {
//		// Handle action bar item clicks here. The action bar will
//		// automatically handle clicks on the Home/Up button, so long
//		// as you specify a parent activity in AndroidManifest.xml.
//		int id = item.getItemId();
//
//		//noinspection SimplifiableIfStatement
//		if (id == R.id.action_settings) {
//			return true;
//		}
//
//		return super.onOptionsItemSelected(item);
//	}

	public static void show(Context context) {
		context.startActivity(new Intent(context, AnnotationActivity.class));
	}
}
