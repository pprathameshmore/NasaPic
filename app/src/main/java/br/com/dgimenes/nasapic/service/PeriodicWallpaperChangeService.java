package br.com.dgimenes.nasapic.service;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Calendar;

import br.com.dgimenes.nasapic.R;
import br.com.dgimenes.nasapic.service.interactor.ApodInteractor;
import br.com.dgimenes.nasapic.service.interactor.OnFinishListener;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PeriodicWallpaperChangeService extends JobService {
    public static final int JOB_ID = 666;
    private static final String LAST_APOD_CHECK_DAY = "LAST_APOD_CHECK_DAY";
    private static final String LOG_TAG = PeriodicWallpaperChangeService.class.getSimpleName();
    private static final int PERIOD_IN_HOURS = 6;
    public static final String UNDO_OPERATION_EXTRA = "UNDO_OPERATION_EXTRA";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean undoOperation = intent.getExtras().getBoolean(UNDO_OPERATION_EXTRA, false);
        if (undoOperation) {
            undoLastWallpaperChange();
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(WallpaperChangeNotification.NOTIFICATION_ID);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void undoLastWallpaperChange() {
        try {
            new ApodInteractor(this).undoLastWallpaperChangeSync();
        } catch (IOException e) {
            e.printStackTrace();
            String undoErrorMessage = getResources().getString(R.string.undo_error_message);
            Log.e(LOG_TAG, undoErrorMessage);
            Toast.makeText(this, undoErrorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    public static void updatePeriodicWallpaperChangeSetup(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Resources res = context.getResources();
            boolean periodicChangeActivated = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(res.getString(R.string.periodic_change_preference),
                            res.getBoolean(R.bool.periodic_change_preference_default_value));
            if (periodicChangeActivated) {
                setupIfNeededPeriodicWallpaperChange(context);
            } else {
                unschedulePeriodicWallpaperChange(context);
            }
        }
    }

    public static void setupIfNeededPeriodicWallpaperChange(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Resources res = context.getResources();
            JobScheduler scheduler = (JobScheduler) context
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (scheduler.getAllPendingJobs().size() == 0) {
                ComponentName serviceEndpoint = new ComponentName(context,
                        PeriodicWallpaperChangeService.class);
                JobInfo wallpaperChangeJob = new JobInfo.Builder(
                        PeriodicWallpaperChangeService.JOB_ID, serviceEndpoint)
                        .setRequiresCharging(false)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(PERIOD_IN_HOURS * 60 * 60 * 1000)
                        .build();

                scheduler.schedule(wallpaperChangeJob);
                String scheduledMessage = res.getString(R.string.periodic_change_scheduled);
                Toast.makeText(context, scheduledMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void unschedulePeriodicWallpaperChange(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler scheduler = (JobScheduler) context
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (scheduler.getAllPendingJobs().size() > 0) {
                scheduler.cancelAll();
                String unscheduledMessage = context.getResources()
                        .getString(R.string.periodic_change_unscheduled);
                Toast.makeText(context, unscheduledMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (haventTriedChangedToday()) {
            ApodInteractor apodInteractor = new ApodInteractor(this);
            apodInteractor.setTodaysApodAsWallpaper(new OnFinishListener<Void>() {
                @Override
                public void onSuccess(Void result) {
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(PeriodicWallpaperChangeService.this)
                            .edit();
                    editor.putInt(LAST_APOD_CHECK_DAY,
                            Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
                    editor.apply();
                }

                @Override
                public void onError(Throwable throwable) {
                    String errorMessage = PeriodicWallpaperChangeService.this.getResources()
                            .getString(R.string.periodic_change_error);
                    WallpaperChangeNotification.createNotification(
                            PeriodicWallpaperChangeService.this, errorMessage);
                }
            });
        } else {
            Log.d(LOG_TAG, "Skipping periodic wallpaper change because already did it today.");
        }
        return false; // means everything is done
    }

    private boolean haventTriedChangedToday() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int dayOfMonth = preferences.getInt(LAST_APOD_CHECK_DAY, -1);
        int currentDayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        return dayOfMonth != currentDayOfMonth;
    }



    @Override
    public boolean onStopJob(JobParameters params) {
        WallpaperChangeNotification.createNotification(this,
                "NasaPic PeriodicWallpaperChangeService stopping...");
        return false; // means everything is done
    }
}
