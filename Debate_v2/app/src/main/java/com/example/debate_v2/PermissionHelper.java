package com.example.debate_v2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility class for handling runtime permissions across the app
 * Usage:
 *   PermissionHelper.checkPermissions(activity, PermissionHelper.AUDIO_PERMISSIONS, REQUEST_CODE);
 */
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    // Permission groups
    public static final String[] AUDIO_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };

    public static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    public static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public static final String[] CALL_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static final String[] ALL_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };

    /**
     * Check if all permissions are granted
     */
    public static boolean hasPermissions(Activity activity, String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true; // Permissions handled at install on older devices
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "hasPermissions: Missing - " + permission);
                return false;
            }
        }
        Log.d(TAG, "hasPermissions: All permissions granted");
        return true;
    }

    /**
     * Check if specific permission is granted
     */
    public static boolean hasPermission(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        boolean granted = ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            Log.d(TAG, "hasPermission: Missing - " + permission);
        }
        return granted;
    }

    /**
     * Request permissions from user
     */
    public static void requestPermissions(Activity activity, String[] permissions,
                                          int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "requestPermissions: Requesting " + permissions.length
                    + " permissions");
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    /**
     * Get list of denied permissions
     */
    public static String[] getDeniedPermissions(Activity activity,
                                                String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new String[0];
        }

        java.util.List<String> deniedList = new java.util.ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                deniedList.add(permission);
            }
        }
        return deniedList.toArray(new String[0]);
    }

    /**
     * Check permission request results
     */
    public static boolean areAllPermissionsGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Log permission results for debugging
     */
    public static void logPermissionResults(String[] permissions,
                                            int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            String status = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    ? "GRANTED" : "DENIED";
            Log.d(TAG, "Permission " + permissions[i] + ": " + status);
        }
    }
}
