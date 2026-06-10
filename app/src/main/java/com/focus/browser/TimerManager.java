package com.zen.browser;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

public class TimerManager {
    private long startTime = 0;
    private long pausedTime = 0;
    private final Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private final TextView timerView;
    private final Context context;

    public TimerManager(Context context, TextView timerView) {
        this.context = context;
        this.timerView = timerView;
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = (SystemClock.elapsedRealtime() - startTime) + pausedTime;
                long seconds = elapsed / 1000;
                long minutes = seconds / 60;
                long hours = minutes / 60;
                String timeStr = (hours > 0) ?
                        String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
                        : String.format("%d:%02d", minutes, seconds % 60);
                timerView.setText(timeStr);
                int color;
                if (minutes < 15) color = ContextCompat.getColor(context, R.color.timer_green);
                else if (minutes < 30) color = ContextCompat.getColor(context, R.color.timer_yellow);
                else color = ContextCompat.getColor(context, R.color.timer_red);
                timerView.setTextColor(color);
                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    public void start() {
        startTime = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
    }

    public void pause() {
        pausedTime += (SystemClock.elapsedRealtime() - startTime);
        startTime = 0;
        timerHandler.removeCallbacks(timerRunnable);
    }

    public void resume() {
        startTime = SystemClock.elapsedRealtime();
        timerHandler.post(timerRunnable);
    }
    
    public void destroy() {
        timerHandler.removeCallbacks(timerRunnable);
    }
}
