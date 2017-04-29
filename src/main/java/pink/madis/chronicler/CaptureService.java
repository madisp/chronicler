package pink.madis.chronicler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

public class CaptureService extends HiddenCameraService {
    private static final String TAG = "CaptureService";

    // almost done! now I just need to make sure that this gets kicked off without the activity at boot.
    private static final long CYCLE = 10 * 60 * 1000L; // 10 minutes

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private StorageReference firebase;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // nicked shamelessly from the fb docs
        firebase = FirebaseStorage.getInstance().getReference();

        // oh thats a weird builder pattern...
        CameraConfig.Builder builder = new CameraConfig().getBuilder(this);
        builder.setCameraFacing(CameraFacing.REAR_FACING_CAMERA);
        builder.setCameraResolution(CameraResolution.HIGH_RESOLUTION);
        builder.setImageFormat(CameraImageFormat.FORMAT_JPEG);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.wtf(TAG,"Should not happen...");
            return;
        }

        startCamera(builder.build());
    }

    @Override
    public void onDestroy() {
        stopCamera();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        // ugly hacks yaaay
        // looks like the setup of the camera takes _some_ time, I don't think the API has any
        // callbacks either. So I'm just going to wing it and take a picture 1sec later while hoping that
        // the camera _was_ set up...

        // in my case I want to create something like a 1 picture per hour timelapse camera anyway
        // on a specific device (Nexus 5) so I don't really care about the portability of the code here.
        // for production you'd probably need to do something prettier.
        uiHandler.postDelayed(() -> {
            // haha, now I get both. Doesn't matter as long as it is taking pictures.
            takePicture();
        }, 1000L /* one second */);
        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(@NonNull final File file) {
        Log.d(TAG, "Picture taken: " + file);
        // no need for a thread apparently
        StorageReference remote = firebase.child("upload/" + file.getName());
        remote.putFile(Uri.fromFile(file))
                .addOnSuccessListener(taskSnapshot -> {
                    // no need for the local file anymore
                    file.delete();
                    Log.d(TAG, "Uploaded " + file + " to firebase");
                    schedulePicture(CaptureService.this);
                    stopSelf();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed uploading file", e);
                    // keep the file and stop, so that I can upload them manually later..
                    schedulePicture(CaptureService.this);
                    stopSelf();
                });
    }

    @Override
    public void onCameraError(int i) {
        // crash here
        throw new RuntimeException("Camera error: " + i);
    }

    /**
     * Schedules a picture with something like X amount of time from now
     * @param context
     */
    public static void schedulePicture(Context context) {
        Log.d(TAG, "Sending a new picture in " + CYCLE + "ms");

        long next = SystemClock.elapsedRealtime() + CYCLE;

        Intent intent = new Intent(context, CaptureService.class);
        PendingIntent pi = PendingIntent.getService(context, R.id.takePicture, intent, PendingIntent.FLAG_ONE_SHOT);

        // maybe I should be using JobScheduler here?
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
    }
}
