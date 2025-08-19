package cc.ytdttj.speed;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.ActivityInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationHelper.OnLocationUpdateListener, LocationHelper.OnGpsStatusChangeListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private TextView speedValue, speedUnit, timeValue, distanceValue, avgSpeedValue, maxSpeedValue, currentTime, gpsSignal, batteryLevel;
    private android.widget.ImageView gpsSignalIndicator;
    private Button startStopButton, pauseButton, resetButton, rotateScreenButton;
    private ImageButton settingsButton;
    private ConstraintLayout mainLayout;
    
    private boolean isLandscape = false;

    // 位置服务助手
    private LocationHelper locationHelper;
    
    // 电池电量监控
    private Handler batteryCheckHandler = new Handler();
    private Runnable batteryCheckRunnable;

    private boolean isRecording = false;
    private boolean isPaused = false;

    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
    private long timeSwapBuff = 0L;
    private long updatedTime = 0L;

    private Handler customHandler = new Handler();

    private float totalDistance = 0.0f;
    private float maxSpeed = 0.0f;
    private Location lastLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏和沉浸式效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_main);

        initViews();
        setupLocationHelper();
        updateCurrentTime();
        updateBatteryLevel();
        setupBatteryMonitor();

        startStopButton.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        pauseButton.setOnClickListener(v -> {
            if (!isPaused) {
                pauseRecording();
            } else {
                resumeRecording();
            }
        });

        resetButton.setOnClickListener(v -> {
            resetData();
            resetButton.setVisibility(View.GONE);
        });
        
        rotateScreenButton.setOnClickListener(v -> {
            toggleScreenOrientation();
        });
        
        settingsButton.setOnClickListener(v -> {
            openSettingsActivity();
        });
    }

    private void initViews() {
        mainLayout = findViewById(R.id.main_layout);
        speedValue = findViewById(R.id.speed_value);
        speedUnit = findViewById(R.id.speed_unit);
        timeValue = findViewById(R.id.time_value);
        distanceValue = findViewById(R.id.distance_value);
        avgSpeedValue = findViewById(R.id.avg_speed_value);
        maxSpeedValue = findViewById(R.id.max_speed_value);
        currentTime = findViewById(R.id.current_time);
        gpsSignal = findViewById(R.id.gps_signal);
        gpsSignalIndicator = findViewById(R.id.gps_signal_indicator);
        batteryLevel = findViewById(R.id.battery_level);
        startStopButton = findViewById(R.id.start_stop_button);
        pauseButton = findViewById(R.id.pause_button);
        resetButton = findViewById(R.id.reset_button);
        rotateScreenButton = findViewById(R.id.rotate_screen_button);
        settingsButton = findViewById(R.id.settings_button);
    }

    /**
     * 设置位置服务助手
     */
    private void setupLocationHelper() {
        // 检查定位权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        // 初始化位置服务助手
        locationHelper = new LocationHelper(this);
        locationHelper.initialize(this, this);
    }
    
    /**
     * 设置电池监控，用于在电量低时自动切换定位模式
     */
    private void setupBatteryMonitor() {
        batteryCheckRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查电池电量
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                float batteryPct = level * 100 / (float)scale;
                
                // 如果电量低于阈值，更新定位模式
                if (batteryPct < SettingsActivity.LOW_BATTERY_THRESHOLD) {
                    // 更新定位模式
                    locationHelper.updateLocationMode();
                    
                    // 如果当前在高精度模式下，提示用户已自动切换到平衡模式
                    if (SettingsActivity.getLocationMode(MainActivity.this) == SettingsActivity.MODE_HIGH_ACCURACY) {
                        Toast.makeText(MainActivity.this, "电量低于20%，已自动切换到平衡模式以节省电量", Toast.LENGTH_LONG).show();
                    }
                }
                
                // 每分钟检查一次电量
                batteryCheckHandler.postDelayed(this, 60000);
            }
        };
        
        // 开始电池监控
        batteryCheckHandler.post(batteryCheckRunnable);
    }
    
    /**
     * 打开设置界面
     */
    private void openSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * 开始记录
     */
    private void startRecording() {
        isRecording = true;
        isPaused = false;
        startStopButton.setText("结束");
        pauseButton.setVisibility(View.VISIBLE);
        pauseButton.setText("暂停");
        mainLayout.setBackgroundColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startTime = SystemClock.uptimeMillis();
        customHandler.postDelayed(updateTimerThread, 0);
        
        // 确保位置更新已启动
        if (locationHelper != null) {
            locationHelper.startLocationUpdates();
        }

        // resetData() is called in stopRecording() now
    }

    private void stopRecording() {
        isRecording = false;
        isPaused = false;
        startStopButton.setText("开始");
        pauseButton.setVisibility(View.GONE);
        resetButton.setVisibility(View.VISIBLE);
        mainLayout.setBackgroundColor(Color.parseColor("#212121"));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        customHandler.removeCallbacks(updateTimerThread);
        // 不再调用resetData()，只在点击复位按钮时清零
        
        // 如果不再需要高精度位置更新，可以降低更新频率或暂停更新
        if (locationHelper != null) {
            // 仍然保持位置更新，但可以降低频率
            locationHelper.updateLocationMode();
        }
    }

    private void pauseRecording() {
        isPaused = true;
        pauseButton.setText("继续");
        timeSwapBuff += timeInMilliseconds;
        customHandler.removeCallbacks(updateTimerThread);
    }

    private void resumeRecording() {
        isPaused = false;
        pauseButton.setText("暂停");
        startTime = SystemClock.uptimeMillis();
        customHandler.postDelayed(updateTimerThread, 0);
    }

    private void resetData() {
        totalDistance = 0.0f;
        maxSpeed = 0.0f;
        lastLocation = null;
        timeSwapBuff = 0L;
        timeInMilliseconds = 0L;
        startTime = 0L;
        updatedTime = 0L;
        timeValue.setText("00:00:00");
        distanceValue.setText("0.00 km");
        avgSpeedValue.setText("0.0 km/h");
        maxSpeedValue.setText("0.0 km/h");
    }

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;

            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            int hours = mins / 60;
            mins = mins % 60;
            timeValue.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
            // 使用100ms更新频率，与GPS更新频率同步
            customHandler.postDelayed(this, 100);
        }
    };

    private void updateCurrentTime() {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                currentTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                handler.postDelayed(this, 1000);
            }
        });
    }
    
    /**
     * 更新电池电量显示
     */
    private void updateBatteryLevel() {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                
                float batteryPct = level * 100 / (float)scale;
                batteryLevel.setText(String.format(Locale.getDefault(), "%d%%", Math.round(batteryPct)));
                
                // 每分钟更新一次电量
                handler.postDelayed(this, 60000);
            }
        });
    }
    
    // 静止状态检测参数
    private static final float MIN_MOVEMENT_DISTANCE = 3.0f; // 最小移动距离（米）
    private static final float MIN_SPEED_THRESHOLD = 1.0f; // 最小速度阈值（km/h）
    private static final float MAX_ACCURACY_FOR_DISTANCE = 10.0f; // 用于距离计算的最大精度（米）
    
    /**
     * 位置更新回调
     */
    @Override
    public void onLocationUpdated(Location location) {
        float speed = location.getSpeed() * 3.6f; // m/s to km/h
        speedValue.setText(String.format(Locale.getDefault(), "%d", (int) speed));

        if (isRecording && !isPaused) {
            if (speed > maxSpeed) {
                maxSpeed = speed;
                maxSpeedValue.setText(String.format(Locale.getDefault(), "%.1f km/h", maxSpeed));
            }

            if (lastLocation != null) {
                float distance = lastLocation.distanceTo(location); // 距离（米）
                
                // 智能距离累积：只有在满足条件时才累积距离
                if (shouldAccumulateDistance(location, distance, speed)) {
                    totalDistance += distance / 1000; // meters to km
                    distanceValue.setText(String.format(Locale.getDefault(), "%.2f km", totalDistance));
                    lastLocation = location; // 只有累积距离时才更新lastLocation
                } else {
                    // 不累积距离，但更新位置精度较高时的参考位置
                    if (location.getAccuracy() < lastLocation.getAccuracy()) {
                        lastLocation = location;
                    }
                }
            } else {
                // 首次获取位置
                lastLocation = location;
            }

            // 计算平均速度（只有在有实际移动距离时才显示）
            long elapsedTimeInSeconds = updatedTime / 1000;
            if (elapsedTimeInSeconds > 0 && totalDistance > 0) {
                float avgSpeed = totalDistance / (elapsedTimeInSeconds / 3600.0f);
                avgSpeedValue.setText(String.format(Locale.getDefault(), "%.1f km/h", avgSpeed));
            } else {
                avgSpeedValue.setText("0.0 km/h");
            }
        }
    }
    
    /**
     * 判断是否应该累积距离
     * @param currentLocation 当前位置
     * @param distance 与上次位置的距离（米）
     * @param speed 当前速度（km/h）
     * @return 是否应该累积距离
     */
    private boolean shouldAccumulateDistance(Location currentLocation, float distance, float speed) {
        // 1. 检查位置精度：如果精度太差，不累积距离
        if (currentLocation.getAccuracy() > MAX_ACCURACY_FOR_DISTANCE) {
            return false;
        }
        
        // 2. 检查移动距离：必须超过最小移动距离阈值
        if (distance < MIN_MOVEMENT_DISTANCE) {
            return false;
        }
        
        // 3. 检查速度：如果速度太低，可能是GPS漂移
        if (speed < MIN_SPEED_THRESHOLD) {
            // 低速时需要更大的移动距离才认为是真实移动
            return distance > MIN_MOVEMENT_DISTANCE * 2;
        }
        
        // 4. 检查距离与精度的关系：移动距离应该明显大于GPS精度
        float combinedAccuracy = currentLocation.getAccuracy() + lastLocation.getAccuracy();
        if (distance < combinedAccuracy * 1.5f) {
            return false;
        }
        
        return true;
    }
    
    /**
     * GPS状态变化回调
     */
    @Override
    public void onGpsStatusChanged(int satellitesInFix, int satelliteCount, boolean isConnected) {
        gpsSignal.setText("GPS: " + satellitesInFix + "/" + satelliteCount);
        updateGpsSignalIndicator(satellitesInFix);
    }

    private void updateGpsSignalIndicator(int satellitesUsedInFix) {
        if (satellitesUsedInFix >= 12) {
            gpsSignalIndicator.setImageResource(R.drawable.ic_gps_signal_4);
        } else if (satellitesUsedInFix >= 8) {
            gpsSignalIndicator.setImageResource(R.drawable.ic_gps_signal_3);
        } else if (satellitesUsedInFix >= 4) {
            gpsSignalIndicator.setImageResource(R.drawable.ic_gps_signal_2);
        } else if (satellitesUsedInFix > 0) {
            gpsSignalIndicator.setImageResource(R.drawable.ic_gps_signal_1);
        } else {
            gpsSignalIndicator.setImageResource(R.drawable.ic_gps_signal_0);
        }
    }
    
    /**
     * 提供者状态变化回调
     */
    @Override
    public void onProviderStatusChanged(boolean isGpsEnabled, boolean isNetworkEnabled) {
        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(this, "请开启位置服务", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已获取，LocationHelper 将在 onCreate 中初始化
            } else {
                Toast.makeText(this, "权限被拒绝，无法获取速度信息", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 电池检查间隔（毫秒）
    private static final long BATTERY_CHECK_INTERVAL = 60000; // 1分钟检查一次
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isRecording && !isPaused) {
            startTime = SystemClock.uptimeMillis() - updatedTime;
            customHandler.postDelayed(updateTimerThread, 0);
        }
        // 恢复位置更新
        if (locationHelper != null) {
            locationHelper.startLocationUpdates();
        }
        // 恢复电池监控
        customHandler.postDelayed(batteryCheckRunnable, BATTERY_CHECK_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        customHandler.removeCallbacks(updateTimerThread);
        // 暂停位置更新以节省电量
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }
        // 暂停电池监控
        customHandler.removeCallbacks(batteryCheckRunnable);
    }
    
    /**
     * 保存实例状态，防止屏幕旋转时数据丢失
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        
        // 保存记录状态
        outState.putBoolean("isRecording", isRecording);
        outState.putBoolean("isPaused", isPaused);
        outState.putBoolean("isLandscape", isLandscape);
        
        // 保存计时器相关数据
        outState.putLong("startTime", startTime);
        outState.putLong("timeSwapBuff", timeSwapBuff);
        outState.putLong("timeInMilliseconds", timeInMilliseconds);
        outState.putLong("updatedTime", updatedTime);
        
        // 保存速度和距离数据
        outState.putFloat("totalDistance", totalDistance);
        outState.putFloat("maxSpeed", maxSpeed);
        
        // 保存最后位置（如果存在）
        if (lastLocation != null) {
            outState.putDouble("lastLocationLatitude", lastLocation.getLatitude());
            outState.putDouble("lastLocationLongitude", lastLocation.getLongitude());
            outState.putFloat("lastLocationAccuracy", lastLocation.getAccuracy());
            outState.putLong("lastLocationTime", lastLocation.getTime());
            outState.putFloat("lastLocationSpeed", lastLocation.getSpeed());
        }
    }
    
    /**
     * 恢复实例状态
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        
        // 恢复记录状态
        isRecording = savedInstanceState.getBoolean("isRecording", false);
        isPaused = savedInstanceState.getBoolean("isPaused", false);
        isLandscape = savedInstanceState.getBoolean("isLandscape", false);
        
        // 恢复计时器相关数据
        startTime = savedInstanceState.getLong("startTime", 0L);
        timeSwapBuff = savedInstanceState.getLong("timeSwapBuff", 0L);
        timeInMilliseconds = savedInstanceState.getLong("timeInMilliseconds", 0L);
        updatedTime = savedInstanceState.getLong("updatedTime", 0L);
        
        // 恢复速度和距离数据
        totalDistance = savedInstanceState.getFloat("totalDistance", 0.0f);
        maxSpeed = savedInstanceState.getFloat("maxSpeed", 0.0f);
        
        // 恢复最后位置
        if (savedInstanceState.containsKey("lastLocationLatitude")) {
            lastLocation = new Location("restored");
            lastLocation.setLatitude(savedInstanceState.getDouble("lastLocationLatitude"));
            lastLocation.setLongitude(savedInstanceState.getDouble("lastLocationLongitude"));
            lastLocation.setAccuracy(savedInstanceState.getFloat("lastLocationAccuracy"));
            lastLocation.setTime(savedInstanceState.getLong("lastLocationTime"));
            lastLocation.setSpeed(savedInstanceState.getFloat("lastLocationSpeed"));
        }
        
        // 恢复UI状态
        restoreUIState();
    }
    
    /**
     * 恢复UI状态
     */
    private void restoreUIState() {
        if (isRecording) {
            startStopButton.setText("结束");
            pauseButton.setVisibility(View.VISIBLE);
            resetButton.setVisibility(View.GONE);
            mainLayout.setBackgroundColor(Color.BLACK);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            
            if (isPaused) {
                pauseButton.setText("继续");
            } else {
                pauseButton.setText("暂停");
                // 重新启动计时器
                startTime = SystemClock.uptimeMillis() - updatedTime;
                customHandler.postDelayed(updateTimerThread, 0);
            }
        } else {
            startStopButton.setText("开始");
            pauseButton.setVisibility(View.GONE);
            resetButton.setVisibility(View.VISIBLE);
            mainLayout.setBackgroundColor(Color.parseColor("#212121"));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        
        // 更新显示的数据
        updateDisplayedData();
    }
    
    /**
     * 更新显示的数据
     */
    private void updateDisplayedData() {
        // 更新时间显示
        if (updatedTime > 0) {
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            int hours = mins / 60;
            mins = mins % 60;
            timeValue.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
        }
        
        // 更新距离显示
        distanceValue.setText(String.format(Locale.getDefault(), "%.2f km", totalDistance));
        
        // 更新最大速度显示
        maxSpeedValue.setText(String.format(Locale.getDefault(), "%.1f km/h", maxSpeed));
        
        // 更新平均速度显示
        long elapsedTimeInSeconds = updatedTime / 1000;
        if (elapsedTimeInSeconds > 0) {
            float avgSpeed = totalDistance / (elapsedTimeInSeconds / 3600.0f);
            avgSpeedValue.setText(String.format(Locale.getDefault(), "%.1f km/h", avgSpeed));
        }
    }
    
    /**
     * 切换屏幕方向
     */
    private void toggleScreenOrientation() {
        if (isLandscape) {
            // 切换到竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            // 切换到横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        isLandscape = !isLandscape;
    }
}