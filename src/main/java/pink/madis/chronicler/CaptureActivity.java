package pink.madis.chronicler;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.androidhiddencamera.HiddenCameraUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class CaptureActivity extends AppCompatActivity {
    private static final String TAG = "CaptureActivity";

    @BindView(R.id.shutterClick)
    Button click;

    @BindView(R.id.signing_in_progress)
    TextView signingIn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        ButterKnife.bind(this);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            click.setVisibility(View.GONE);
            signingIn.setVisibility(View.VISIBLE);
            Log.d(TAG, "Firebase not signed in, signing in anonymously...");
            auth.signInAnonymously().addOnSuccessListener(authResult -> {
                Log.d(TAG, "Firebase signed in");
                signingIn.setVisibility(View.GONE);
                click.setVisibility(View.VISIBLE);
            }).addOnFailureListener(e -> {
                signingIn.setText(R.string.signing_in_failed);
                Log.d(TAG, "Failed to sign in anonymously", e);
            });
        } else {
            Log.d(TAG, "Firebase already signed in");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0 && permissions.length == 0) {
            // user canceled, ignore
            return;
        }

        // check if we got our permission
        if (requestCode == R.id.requestCameraPermission) {
            if (grantResults.length != 1 || permissions.length != 1) {
                throw new IllegalStateException("Unexpected number of grant results / permissions");
            }
            if (!permissions[0].equals(Manifest.permission.CAMERA)) {
                throw new IllegalStateException("We were expecting to request permission for camera");
            }
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // yaay, we got the permission, loop back to take picture...
                takePicture();
            }
        }
    }

    @OnClick(R.id.shutterClick)
    void takePicture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // we don't have the permission
            requestPermissions(new String[] { Manifest.permission.CAMERA }, R.id.requestCameraPermission);
            return;
        }

        if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
            HiddenCameraUtils.openDrawOverPermissionSetting(this);
            return;
        }

        // schedule a new picture
        CaptureService.schedulePicture(this);
    }
}
